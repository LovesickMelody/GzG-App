"""
Quellenart „Erstanbieter": Kampagnen bei ihrem Urheber lesen statt bei einem Portal.

Das ist die Pipeline aus Entdeckung, Extraktion und Pruefung::

    ct_logs + sitemap  →  Kandidaten-Adressen   (kein Portal beteiligt)
            ↓
    fetch + tdm-Prüfung
            ↓
    extract (JSON-LD, sonst Modell)             (kein Selektor beteiligt)
            ↓
    pruefung                                    (was nicht belegt ist, fliegt raus)

Warum das eine eigene Quellenart ist und kein weiterer Parser: Ein Parser
bekommt fertiges HTML und gibt viele Aktionen zurueck. Hier ist es umgekehrt —
wir muessen erst herausfinden, *welche* Seiten es gibt, und bekommen dann je
Seite hoechstens *eine* Aktion. Das in die ``parse(html, quelle)``-Form zu
pressen haette nur verschleiert, dass es etwas anderes ist.

Der Preis: ein Abruf je Kandidat. Deshalb ``max_kandidaten`` — und deshalb
sortiert die Entdeckung die neuesten nach vorn.
"""

from __future__ import annotations

import hashlib
import logging
from datetime import date

from .extract import Modellextraktor, extrahiere, sichtbarer_text
from .discovery import Kandidat, ct_logs, sitemap
from .fetch import Fetcher
from .models import Action, adressenschluessel
from .pruefung import Kontext, pruefe_liste
from .tdm import Vorbehaltspruefer

log = logging.getLogger(__name__)

# Wieviele Kandidatenseiten je Quelle und Lauf hoechstens abgerufen werden.
# Bei drei Sekunden Pause je Host sind 40 Abrufe rund zwei Minuten — vertretbar
# fuer einen Lauf am Tag. Was darueber liegt, kommt beim naechsten Mal dran.
STANDARD_MAX_KANDIDATEN = 40

# Nach wievielen Tagen eine bereits bekannte Kampagne erneut gelesen wird.
#
# Ohne diese Wiederverwendung kostet jeder Lauf das Volle: Eine Kampagne, die
# gestern gefunden wurde, bekaeme heute wieder einen Abruf und einen
# Modellaufruf, obwohl sie unveraendert in ``actions.json`` steht. Das ist der
# eigentliche Kostentreiber — nicht die Zahl der Nutzer, denn die App laedt nur
# die fertige Datei.
#
# Sieben Tage heisst: Jede bekannte Kampagne wird einmal die Woche
# gegengelesen, aber nicht alle am selben Tag. Welcher Tag es ist, entscheidet
# die Adresse selbst (siehe ``_auffrischen_faellig``) — so verteilt sich die
# Last gleichmaessig, statt einmal pro Woche in einer Spitze anzufallen.
STANDARD_AUFFRISCHEN_TAGE = 7


def bekannte_adressen(bestand: dict, quellenname: str) -> dict[str, dict]:
    """
    Baut das Verzeichnis "Adresse → bereits bekannte Aktion" fuer eine Quelle.

    Eingetragen werden ``url`` **und** ``submit_url``: Welche der beiden mit der
    Kandidatenadresse uebereinstimmt, haengt davon ab, ob der Abruf beim letzten
    Mal auf eine andere Adresse weitergeleitet wurde.
    """
    verzeichnis: dict[str, dict] = {}
    for eintrag in bestand.get("actions", []):
        if eintrag.get("source") != quellenname:
            continue
        for feld in ("url", "submit_url"):
            schluessel = adressenschluessel(eintrag.get(feld))
            if schluessel:
                verzeichnis.setdefault(schluessel, eintrag)
    return verzeichnis


def _noch_gueltig(eintrag: dict, heute: date) -> bool:
    """
    Laeuft die bekannte Aktion noch?

    Ohne Frist gilt sie als **nicht** wiederverwendbar. Das ist die einzige
    Stelle, an der "keine Frist bekannt" streng ausgelegt wird — und zwar
    bewusst: Ohne Frist laesst sich nicht sagen, ob die Kampagne noch laeuft,
    und eine abgelaufene weiterzuschleppen waere schlimmer als ein Abruf zu viel.
    """
    frist = eintrag.get("submission_deadline") or eintrag.get("valid_to")
    if not frist:
        return False
    try:
        return date.fromisoformat(frist) >= heute
    except (ValueError, TypeError):
        return False


def _auffrischen_faellig(url: str, heute: date, tage: int) -> bool:
    """
    Ist diese Kampagne heute mit Gegenlesen dran?

    Die Adresse bestimmt ihren eigenen Wochentag. Das ist stabil (dieselbe
    Kampagne trifft immer denselben Tag), gleichverteilt (die Last verteilt sich
    ueber die Woche) und braucht keinen Zeitstempel je Aktion — der muesste
    sonst in ``actions.json``, und dieses Format liest die App.
    """
    if tage <= 1:
        return True
    schlitz = int(hashlib.sha1(url.encode("utf-8")).hexdigest(), 16) % tage
    return heute.toordinal() % tage == schlitz


def entdecke(quelle: dict, fetcher: Fetcher) -> list[Kandidat]:
    """Fuehrt alle konfigurierten Entdecker aus und fasst sie zusammen."""
    gefunden: list[Kandidat] = []

    anbieter = quelle.get("ct_anbieter") or ct_logs.STANDARD_ANBIETER
    for basis in quelle.get("ct_logs") or []:
        gefunden.extend(ct_logs.finde(basis, fetcher, anbieter))

    for eintrag in quelle.get("sitemaps") or []:
        if isinstance(eintrag, str):
            eintrag = {"url": eintrag}
        adresse = eintrag.get("url")
        if not adresse:
            continue
        gefunden.extend(
            sitemap.finde(
                adresse,
                fetcher,
                muster=eintrag.get("muster"),
                quellenname=f"sitemap:{quelle['name']}",
            )
        )

    # Dieselbe Adresse kann aus zwei Entdeckern kommen. Der erste Fund gewinnt,
    # damit die Herkunft im Log stabil bleibt.
    gesehen: set[str] = set()
    eindeutig: list[Kandidat] = []
    for kandidat in gefunden:
        if kandidat.url in gesehen:
            continue
        gesehen.add(kandidat.url)
        eindeutig.append(kandidat)

    # Neueste zuerst: Wenn die Obergrenze greift, sollen die frischen Kampagnen
    # drin sein und nicht die von vorletztem Jahr. Ohne Datum ans Ende.
    eindeutig.sort(key=lambda k: k.zuerst_gesehen or "", reverse=True)
    return eindeutig


def sammle(
    quelle: dict,
    fetcher: Fetcher,
    extraktor: Modellextraktor | None = None,
    pruefer: Vorbehaltspruefer | None = None,
    bekannt: dict[str, dict] | None = None,
    heute: date | None = None,
) -> list[Action] | None:
    """
    Holt alle laufenden Aktionen einer Erstanbieter-Quelle.

    Gibt ``None`` zurueck, wenn die Entdeckung selbst ausfiel — dann bleibt der
    bisherige Stand dieser Quelle stehen, genau wie bei einem Portalausfall.
    Eine leere Liste heisst dagegen: erfolgreich gesucht, gerade laeuft nichts.
    """
    name = quelle["name"]
    pruefer = pruefer or Vorbehaltspruefer()

    kandidaten = entdecke(quelle, fetcher)
    if not kandidaten:
        log.error("Quelle %s: keine Kandidaten gefunden — alter Stand bleibt", name)
        return None

    grenze = int(quelle.get("max_kandidaten", STANDARD_MAX_KANDIDATEN))
    if len(kandidaten) > grenze:
        log.warning(
            "Quelle %s: %s Kandidaten gefunden, nur die %s neuesten werden geprüft",
            name,
            len(kandidaten),
            grenze,
        )
        kandidaten = kandidaten[:grenze]

    gesammelt: list[Action] = []
    bekannt = bekannt or {}
    stichtag = heute or date.today()
    auffrischen = int(quelle.get("auffrischen_tage", STANDARD_AUFFRISCHEN_TAGE))
    wiederverwendet = 0

    for kandidat in kandidaten:
        # Bereits bekannt, laeuft noch und heute nicht mit Gegenlesen dran?
        # Dann weder abrufen noch auswerten — das spart den Abruf *und* den
        # Modellaufruf, und der Eintrag bleibt exakt derselbe.
        vorhanden = bekannt.get(adressenschluessel(kandidat.url) or "")
        if (
            vorhanden
            and _noch_gueltig(vorhanden, stichtag)
            and not _auffrischen_faellig(kandidat.url, stichtag, auffrischen)
        ):
            gesammelt.append(Action.from_json(vorhanden))
            wiederverwendet += 1
            continue

        seite = fetcher.hole_seite(kandidat.url)
        if seite is None:
            continue
        html, gelandet = seite

        vorbehalt = pruefer.pruefe(gelandet, html, fetcher)
        if vorbehalt:
            log.info("%s: Nutzungsvorbehalt erkannt — %s", gelandet, vorbehalt)
            continue

        aktion = extrahiere(html, gelandet, name, extraktor)
        if aktion is None:
            continue

        befunde = pruefe_liste(
            [aktion],
            # nur_gestartete: Hier — und nur hier — kennen wir Kampagnen aus
            #   Zertifikaten, die noch niemand angekuendigt hat.
            # eigene_herkunft: Der Einreichungslink kommt aus dem Seitentext,
            #   und den schreibt der Betreiber der Seite, nicht wir.
            Kontext(
                seitentext=sichtbarer_text(html),
                nur_gestartete=True,
                eigene_herkunft=True,
            ),
            quellenname=name,
        )
        gesammelt.extend(befunde)

    log.info(
        "Quelle %s: %s Aktion(en) aus %s Kandidaten "
        "(%s wiederverwendet, %s frisch gelesen)",
        name,
        len(gesammelt),
        len(kandidaten),
        wiederverwendet,
        len(kandidaten) - wiederverwendet,
    )
    return gesammelt
