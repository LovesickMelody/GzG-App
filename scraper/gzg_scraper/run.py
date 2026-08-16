"""
Hauptlauf des Scrapers.

Ablauf: Quellen aus ``sources.yaml`` lesen, je Quelle abrufen und zerlegen,
Ergebnis mit dem bisherigen Stand zusammenfuehren und ``data/actions.json``
schreiben — aber nur, wenn sich inhaltlich etwas geaendert hat.

Der wichtigste Teil ist die Fehlerbehandlung: **Eine kaputte Quelle darf die
anderen nicht mitreissen.** Faellt ein Portal aus, behaelt die Datei dessen
bisherige Aktionen und der Job endet trotzdem erfolgreich.
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
from datetime import date, datetime, timezone
from pathlib import Path

import yaml

from . import erstanbieter
from .detail import reichere_an
from .extract import Modellextraktor
from .extract.modell import STANDARD_EFFORT as EFFORT_VORGABE
from .extract.modell import STANDARD_MODELL as MODELL_VORGABE
from .fetch import Fetcher
from .models import Action, adressenschluessel
from .pruefung import Kontext, pruefe_liste
from .registry import hole as hole_parser
from .tdm import Vorbehaltspruefer

log = logging.getLogger("gzg_scraper")

WURZEL = Path(__file__).resolve().parents[2]
STANDARD_QUELLEN = WURZEL / "scraper" / "sources.yaml"
STANDARD_AUSGABE = WURZEL / "data" / "actions.json"


def lade_quellen(pfad: Path, nur: str | None = None) -> list[dict]:
    """
    Liest die aktiven Quellen aus ``sources.yaml``.

    ``nur`` uebergeht dabei ``enabled``. Wer eine Quelle ausdruecklich benennt,
    meint sie auch — sonst liesse sich eine neue Quelle nie probelaufen lassen,
    bevor sie scharf geschaltet ist, und genau das ist der Weg, den die README
    fuer jede neue Quelle vorschreibt.
    """
    with pfad.open(encoding="utf-8") as datei:
        inhalt = yaml.safe_load(datei) or {}
    quellen = inhalt.get("sources", [])

    if nur:
        return [q for q in quellen if q.get("name") == nur]

    return [q for q in quellen if q.get("enabled", True)]


def eingeschaltete_namen(pfad: Path) -> set[str]:
    """
    Namen aller eingeschalteten Quellen — unabhaengig von ``--only``.

    Wird gebraucht, um zu erkennen, welche Quellen ein Lauf uebersprungen hat.
    Bewusst ohne die abgeschalteten: Wer eine Quelle auf ``enabled: false``
    setzt, will ihre Aktionen aus dem Feed haben, und beim naechsten Lauf
    verschwinden sie auch.
    """
    return {q["name"] for q in lade_quellen(pfad) if q.get("name")}


def lade_bestand(pfad: Path) -> dict:
    if not pfad.exists():
        return {"generated_at": None, "actions": []}
    try:
        with pfad.open(encoding="utf-8") as datei:
            return json.load(datei)
    except (OSError, json.JSONDecodeError) as fehler:
        log.warning("Bisherige %s nicht lesbar (%s) — starte leer", pfad.name, fehler)
        return {"generated_at": None, "actions": []}


def sammle_quelle(quelle: dict, fetcher: Fetcher) -> list[Action] | None:
    """
    Holt und zerlegt eine Quelle.

    Gibt ``None`` zurueck, wenn die Quelle als fehlgeschlagen gilt — dann bleibt
    der alte Stand stehen. Eine leere Liste dagegen heisst: erfolgreich geholt,
    es gibt gerade keine Aktionen.
    """
    parser = hole_parser(quelle.get("parser", "css_listing"))
    if parser is None:
        log.error("Quelle %s: Parser %r unbekannt", quelle["name"], quelle.get("parser"))
        return None

    seiten = quelle.get("listing_urls") or []
    if not seiten:
        log.error("Quelle %s: keine listing_urls angegeben", quelle["name"])
        return None

    gesammelt: list[Action] = []
    erfolgreiche_seiten = 0

    for adresse in seiten:
        html = fetcher.hole(adresse)
        if html is None:
            continue
        try:
            gefunden = parser(html, quelle)
        except Exception as fehler:  # noqa: BLE001 — eine Quelle darf alles werfen
            log.exception("Quelle %s: Parser abgestürzt auf %s: %s", quelle["name"], adresse, fehler)
            continue

        erfolgreiche_seiten += 1
        gesammelt.extend(gefunden)
        log.info("Quelle %s: %s Aktionen auf %s", quelle["name"], len(gefunden), adresse)

    if erfolgreiche_seiten == 0:
        log.error("Quelle %s: keine Seite erreichbar — alter Stand bleibt", quelle["name"])
        return None

    if not gesammelt:
        # Alle Seiten geladen, aber nichts gefunden: fast immer ein geaenderter
        # Selektor, nicht ein leergefegtes Portal. Alten Stand behalten.
        log.error(
            "Quelle %s: Seiten geladen, aber keine Aktion erkannt — "
            "Selektoren prüfen. Alter Stand bleibt.",
            quelle["name"],
        )
        return None

    # Erst filtern, dann Detailseiten holen: Was ohnehin rausfliegt, muss auch
    # nicht abgerufen werden. Spart je Lauf ein gutes Dutzend Abrufe.
    gesammelt = filtere_arten(gesammelt, quelle)
    gesammelt = filtere_abgelaufene(gesammelt, quelle["name"])
    if not gesammelt:
        log.warning("Quelle %s: alle Aktionen aussortiert", quelle["name"])
        return []

    reichere_an(gesammelt, quelle, fetcher)

    # Zum Schluss die Eingangspruefung. Ohne Seitentext greifen hier nur die
    # billigen Regeln (Pflichtfelder, Vorab-Start, absurde Frist) — die
    # Betragspruefung braucht eine Seite und laeuft deshalb nur bei den
    # Erstanbieter-Quellen, wo es eine gibt.
    return pruefe_liste(gesammelt, quellenname=quelle["name"])


def filtere_abgelaufene(
    aktionen: list[Action], quellenname: str, heute: date | None = None
) -> list[Action]:
    """
    Wirft Aktionen weg, deren Einsendeschluss vorbei ist.

    Portale lassen abgelaufene Eintraege gern stehen — mydealz zeigt Aktionen
    aus dem Juli noch im August. In der App waere das schlimmer als eine
    fehlende Aktion: Man kauft das Produkt und erfaehrt erst beim Einreichen,
    dass nichts mehr geht.

    Ohne Frist bleibt eine Aktion stehen. "Keine Frist bekannt" heisst nicht
    "abgelaufen", und die Frist fehlt bei einer der Quellen grundsaetzlich.
    """
    stichtag = heute or date.today()
    behalten: list[Action] = []
    verworfen = 0

    for aktion in aktionen:
        frist = aktion.submission_deadline or aktion.valid_to
        if frist:
            try:
                if date.fromisoformat(frist) < stichtag:
                    verworfen += 1
                    continue
            except ValueError:
                pass  # Unlesbares Datum: lieber behalten als grundlos wegwerfen.
        behalten.append(aktion)

    if verworfen:
        log.info("Quelle %s: %s abgelaufene Aktion(en) aussortiert", quellenname, verworfen)
    return behalten


def filtere_arten(aktionen: list[Action], quelle: dict) -> list[Action]:
    """
    Behaelt nur die gewuenschten Aktionsarten.

    Standard sind ausschliesslich volle Erstattungen (``gratis_testen``): Genau
    dafuer ist die App da. Wer auch Teilbetraege sehen will, setzt in
    ``sources.yaml`` etwa ``nur_arten: [gratis_testen, cashback_teilbetrag]``
    oder ``nur_arten: []`` fuer "alles".
    """
    erlaubt = quelle.get("nur_arten", ["gratis_testen"])
    if not erlaubt:
        return aktionen

    behalten = [a for a in aktionen if a.type in erlaubt]
    verworfen = len(aktionen) - len(behalten)
    if verworfen:
        log.info(
            "Quelle %s: %s Aktion(en) aussortiert, weil nicht %s",
            quelle["name"],
            verworfen,
            "/".join(erlaubt),
        )
    return behalten


def _vollstaendigkeit(eintrag: dict) -> int:
    """Wie viele Felder gefuellt sind — je mehr, desto besser als Grundlage."""
    return sum(1 for wert in eintrag.values() if wert not in (None, "", []))


def fasse_dubletten_zusammen(aktionen: list[dict]) -> list[dict]:
    """
    Fasst dieselbe Aktion aus mehreren Portalen zu einem Eintrag zusammen.

    Zusammengefasst wird **nur** bei identischer Einreichungsadresse. Das ist
    keine Aehnlichkeitsschaetzung, sondern dieselbe Identitaet: Wer auf
    demselben Formular einreicht, macht bei derselben Aktion mit. Titel zu
    vergleichen waere verlockend ("Bonduelle Frische Salate" gegen "Bonduelle
    Salat Gratis Testen via scondoo"), wuerde aber mal richtig und mal falsch
    zusammenwerfen — und eine faelschlich verschluckte Aktion ist schlimmer als
    eine doppelt angezeigte.

    Grundlage ist der vollstaendigste Eintrag; fehlende Felder werden aus den
    anderen ergaenzt. So bekommt die Aktion die Frist der einen Quelle und die
    Bedingungen der anderen.
    """
    nach_adresse: dict[str, list[dict]] = {}
    ohne_adresse: list[dict] = []

    for eintrag in aktionen:
        schluessel = adressenschluessel(eintrag.get("submit_url"))
        if schluessel is None:
            ohne_adresse.append(eintrag)
        else:
            nach_adresse.setdefault(schluessel, []).append(eintrag)

    ergebnis = list(ohne_adresse)

    for gruppe in nach_adresse.values():
        if len(gruppe) == 1:
            ergebnis.append(gruppe[0])
            continue

        # Stabile Reihenfolge: erst Vollstaendigkeit, dann Quelle und Id. Ohne
        # den zweiten Teil koennte der Gewinner zwischen zwei Laeufen wechseln
        # und die App die Aktion als neu fuehren.
        sortiert = sorted(
            gruppe, key=lambda e: (-_vollstaendigkeit(e), e["source"], e["id"])
        )
        zusammen = dict(sortiert[0])

        for anderer in sortiert[1:]:
            for feld, wert in anderer.items():
                # Die Quelle bleibt die des Grundeintrags. Die App raeumt je
                # Quelle auf; ein zusammengesetzter Wert wie "a+b" wuerde dabei
                # nie wieder getroffen und der Eintrag bliebe ewig stehen.
                if feld == "source":
                    continue
                if wert in (None, "", []):
                    continue
                if zusammen.get(feld) in (None, "", []):
                    zusammen[feld] = wert
                elif feld in ("retailers", "eans"):
                    zusammen[feld] = sorted(set(zusammen[feld]) | set(wert))

        ergebnis.append(zusammen)

    log.info(
        "%s Aktionen nach dem Zusammenfassen (vorher %s)", len(ergebnis), len(aktionen)
    )
    return ergebnis


def fuehre_zusammen(
    bestand: dict,
    neu_je_quelle: dict[str, list[Action]],
    ausgefallen: set[str],
    uebersprungen: set[str] | frozenset[str] = frozenset(),
) -> list[dict]:
    """
    Baut die neue Aktionsliste.

    Fuer ausgefallene Quellen werden die bisherigen Eintraege uebernommen, fuer
    alle anderen die frisch geholten. Aktionen aus Quellen, die es in
    ``sources.yaml`` nicht mehr gibt, fallen weg.

    ``uebersprungen`` sind eingeschaltete Quellen, die dieser Lauf gar nicht
    angefasst hat — bei ``--only`` also alle anderen. Ihre Eintraege bleiben
    ebenfalls stehen. Ohne das loeschte ein Probelauf mit ``--only`` den halben
    Feed: Die uebrigen Quellen stehen weder in ``neu_je_quelle`` noch in
    ``ausgefallen``, ihre Aktionen fielen also stillschweigend hinten runter —
    und auf ``main`` haette der naechste Commit das festgeschrieben.

    Der Unterschied zu einer Quelle, die sauber gelaufen ist und nichts
    gefunden hat, bleibt dabei erhalten: Die steht mit leerer Liste in
    ``neu_je_quelle``, und ihre alten Eintraege verschwinden zu Recht.
    """
    ergebnis: dict[str, dict] = {}

    for eintrag in bestand.get("actions", []):
        if eintrag.get("source") in ausgefallen or eintrag.get("source") in uebersprungen:
            ergebnis[eintrag["id"]] = eintrag

    for aktionen in neu_je_quelle.values():
        for aktion in aktionen:
            als_json = aktion.to_json()
            # Bei doppelten Ids gewinnt der erste Treffer; sortiert wird
            # anschliessend ohnehin deterministisch.
            ergebnis.setdefault(als_json["id"], als_json)

    zusammengefasst = fasse_dubletten_zusammen(list(ergebnis.values()))
    return sorted(zusammengefasst, key=lambda a: (a["source"], a["title"], a["id"]))


def schreibe_wenn_geaendert(pfad: Path, aktionen: list[dict]) -> bool:
    """
    Schreibt die Datei nur bei inhaltlicher Aenderung.

    ``generated_at`` wird dabei absichtlich nicht mitverglichen und auch nur bei
    einer echten Aenderung neu gesetzt — sonst gaebe es jeden Tag einen Commit,
    der nichts als den Zeitstempel dreht.
    """
    bestand = lade_bestand(pfad)
    if bestand.get("actions") == aktionen:
        log.info("Keine Änderungen — %s bleibt wie sie ist", pfad.name)
        return False

    pfad.parent.mkdir(parents=True, exist_ok=True)
    inhalt = {
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "actions": aktionen,
    }
    with pfad.open("w", encoding="utf-8") as datei:
        json.dump(inhalt, datei, ensure_ascii=False, indent=2)
        datei.write("\n")

    log.info("%s aktualisiert: %s Aktionen", pfad.name, len(aktionen))
    return True


def main(argv: list[str] | None = None) -> int:
    zerleger = argparse.ArgumentParser(description="Sammelt GZG-Aktionen als actions.json")
    zerleger.add_argument("--sources", type=Path, default=STANDARD_QUELLEN)
    zerleger.add_argument("--output", type=Path, default=STANDARD_AUSGABE)
    zerleger.add_argument("--delay", type=float, default=2.0, help="Sekunden zwischen Abrufen")
    zerleger.add_argument("--only", help="nur diese Quelle laufen lassen")
    zerleger.add_argument(
        "--ignore-robots",
        action="store_true",
        help="robots.txt übergehen (nur für eigene Seiten sinnvoll)",
    )
    # Bewusst `or` statt eines Vorgabewerts in .get(): Eine nicht gesetzte
    # GitHub-Variable landet als *leerer* String in der Umgebung, nicht als
    # fehlender Schluessel. Mit .get(name, vorgabe) liefe der Job dann gegen
    # ein Modell namens "".
    zerleger.add_argument(
        "--modell",
        default=os.environ.get("GZG_MODELL") or MODELL_VORGABE,
        help="Modell für die Extraktion (Vorgabe: %(default)s)",
    )
    zerleger.add_argument(
        "--effort",
        default=os.environ.get("GZG_EFFORT") or EFFORT_VORGABE,
        choices=["low", "medium", "high", "xhigh", "max"],
        help="Denktiefe des Modells (Vorgabe: %(default)s)",
    )
    zerleger.add_argument(
        "--ohne-modell",
        action="store_true",
        help="nur JSON-LD auswerten, kein Modell aufrufen",
    )
    argumente = zerleger.parse_args(argv)

    logging.basicConfig(
        level=logging.INFO,
        format="%(levelname)s %(name)s: %(message)s",
        stream=sys.stdout,
    )

    quellen = lade_quellen(argumente.sources, argumente.only)

    if not quellen:
        if argumente.only:
            log.error("Quelle %r steht nicht in %s", argumente.only, argumente.sources)
        else:
            log.error("Keine aktive Quelle in %s", argumente.sources)
        return 1

    for quelle in quellen:
        if not quelle.get("enabled", True):
            log.info(
                "Quelle %s ist abgeschaltet und läuft nur, weil sie mit --only "
                "ausdrücklich genannt wurde",
                quelle["name"],
            )

    fetcher = Fetcher(delay=argumente.delay, respect_robots=not argumente.ignore_robots)

    # Einmal je Lauf, nicht je Quelle: Der Vorbehaltspruefer merkt sich seine
    # Ergebnisse je Host, und der Extraktor haelt eine Verbindung offen.
    extraktor = (
        None
        if argumente.ohne_modell
        else Modellextraktor(modell=argumente.modell, effort=argumente.effort)
    )
    pruefer = Vorbehaltspruefer()

    # Vor der Schleife, nicht danach: Die Erstanbieter-Quellen brauchen den
    # bisherigen Stand, um bekannte Kampagnen nicht erneut abzurufen und
    # auszuwerten.
    bestand = lade_bestand(argumente.output)

    neu_je_quelle: dict[str, list[Action]] = {}
    ausgefallen: set[str] = set()

    for quelle in quellen:
        name = quelle["name"]
        log.info("--- Quelle %s ---", name)
        if quelle.get("parser") == "erstanbieter":
            ergebnis = erstanbieter.sammle(
                quelle,
                fetcher,
                extraktor,
                pruefer,
                bekannt=erstanbieter.bekannte_adressen(bestand, name),
            )
        else:
            ergebnis = sammle_quelle(quelle, fetcher)
        if ergebnis is None:
            ausgefallen.add(name)
        else:
            neu_je_quelle[name] = ergebnis

    # Quellen, die dieser Lauf nicht angefasst hat (bei --only alle anderen).
    # Ihre Eintraege muessen stehenbleiben, sonst raeumt ein Probelauf den Feed
    # leer.
    uebersprungen = eingeschaltete_namen(argumente.sources) - {
        q["name"] for q in quellen
    }
    if uebersprungen:
        log.info(
            "Nicht gelaufen, bisheriger Stand bleibt: %s",
            ", ".join(sorted(uebersprungen)),
        )

    aktionen = fuehre_zusammen(bestand, neu_je_quelle, ausgefallen, uebersprungen)

    schreibe_wenn_geaendert(argumente.output, aktionen)

    log.info(
        "Fertig: %s Aktionen, %s Quellen erfolgreich, %s ausgefallen%s",
        len(aktionen),
        len(neu_je_quelle),
        len(ausgefallen),
        f" ({', '.join(sorted(ausgefallen))})" if ausgefallen else "",
    )

    # Ausgefallene Quellen sind kein Grund, den Job rot zu faerben — sonst
    # rauscht jede Portalwartung als Fehlalarm durch. Erst wenn *keine* Quelle
    # mehr geht, stimmt etwas Grundsaetzliches nicht.
    if not neu_je_quelle:
        log.error("Keine einzige Quelle lieferte Daten")
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
