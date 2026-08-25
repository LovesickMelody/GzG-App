"""
Kampagnen bei Abwicklern nachsehen, die wir schon kennen.

Der Gedanke: Jede Aktion, die über ein Portal hereinkommt, nennt bereits die
Adresse beim Abwickler — ``airwick.justsnap.eu``, ``www.erlebe-haleon.de``,
``milka-oreo-icecream-cashback.de``. Diese Adressen sind kein Zufallsfund,
sondern ein Hinweis, den das Portal selbst gegeben hat. Wer sie mitschreibt,
baut sich über die Zeit ein Verzeichnis der Abwickler auf, **ohne dafür
irgendetwas zusätzlich zu durchsuchen**.

Warum das nötig wurde: Der geplante Weg über Zertifikatsprotokolle ist zu.
crt.sh und certspotter verbieten den Abruf beide per robots.txt, und eine
Kampagnenübersicht veröffentlicht ein Abwickler nicht — das wäre seine
Kundenkartei. Damit blieb keine Entdeckung übrig, die neue Kampagnen von sich
aus findet.

Was dieser Weg leistet und was nicht — ehrlich getrennt:

* **Ausfallsicherung, ja.** Fällt ein Portal aus, bleiben die gelernten
  Adressen. Die Aktionen dahinter lassen sich weiter lesen, prüfen und
  anzeigen, ohne dass ein Portal beteiligt ist.
* **Aktualität, ja.** Eine Aktion, deren Frist der Abwickler inzwischen
  geändert hat, wird an der Quelle gelesen statt beim Portal abgeschrieben.
* **Entdeckung wirklich neuer Kampagnen, nur begrenzt.** Wir sehen einen
  Abwickler erst, wenn ein Portal ihn einmal genannt hat. Betreibt er später
  unter derselben Adresse eine neue Kampagne, finden wir sie; eröffnet er eine
  neue Subdomain, nicht. Das ist der Preis dafür, dass hier nichts gesucht,
  sondern nur nachgesehen wird.

Rechtlich ist das der unbedenklichste der drei Wege: Wir folgen Links, die uns
ohnehin vorliegen, und landen beim Urheber der Aktion — bei dem, der ein
Interesse daran hat, gefunden zu werden. Die Prüfung auf Nutzungsvorbehalt und
robots.txt läuft trotzdem, wie bei jedem anderen Abruf auch.
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse

from . import Kandidat

log = logging.getLogger(__name__)

# Hosts, die zwar als submit_url auftauchen, aber keine Kampagnenseite sind:
# Cashback-Plattformen mit eigenem Konto, Portale, Verkuerzer. Sie hier
# nachzuschlagen brächte nichts — dort steht keine einzelne Aktion, sondern
# eine App-Anmeldung.
NICHT_LERNEN = frozenset(
    {
        "www.mydealz.de",
        "mydealz.de",
        "rabattigel.de",
        "geldzurueck.deals",
        "www.marktguru.de",
        "marktguru.de",
        "scondoo.de",
        "www.scondoo.de",
        "cashu-club.de",
        "www.cashu-club.de",
        "bit.ly",
        "t.co",
    }
)


# Pfadteile, die eine Anmeldeseite verraten. Dahinter steht keine Aktion,
# sondern ein Formular — und die Adresse traegt oft einen Sitzungsschluessel,
# der in einem oeffentlichen Repo nichts zu suchen hat. Aufgefallen an
# `konto.for-me-online.de/u/login?state=hKFo2SBPdl9…`, das beim ersten Lauf
# gegen die echten Daten prompt im Verzeichnis landete.
ANMELDEWOERTER = frozenset(
    {
        "login",
        "signin",
        "sign-in",
        "signup",
        "anmelden",
        "anmeldung",
        "registrieren",
        "register",
        "auth",
        "account",
        "konto",
        "mein-konto",
        "my-account",
    }
)


def _ist_anmeldung(teile) -> bool:
    """
    Erkennt Anmeldeseiten am Pfad.

    Verglichen werden **Pfadsegmente**, nicht Teilzeichenketten: Sonst faengt
    "konto" nicht `/mein-konto`, und umgekehrt schluege "auth" bei einem
    `/authentische-aktion` faelschlich an.
    """
    segmente = {teil for teil in teile.path.lower().split("/") if teil}
    return bool(segmente & ANMELDEWOERTER)


@dataclass(frozen=True)
class Abwickler:
    """Ein Host, der schon einmal eine Aktionsseite getragen hat."""

    host: str
    #: Die zuletzt gesehene vollständige Adresse — die konkrete Kampagnenseite.
    adresse: str
    zuerst_gesehen: str
    zuletzt_gesehen: str

    def to_json(self) -> dict:
        return {
            "host": self.host,
            "adresse": self.adresse,
            "zuerst_gesehen": self.zuerst_gesehen,
            "zuletzt_gesehen": self.zuletzt_gesehen,
        }


def lies_verzeichnis(pfad: Path) -> dict[str, Abwickler]:
    """Liest das Verzeichnis. Fehlt oder bricht es, fangen wir leer an."""
    if not pfad.exists():
        return {}
    try:
        roh = json.loads(pfad.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as fehler:
        log.warning("Abwicklerverzeichnis %s nicht lesbar (%s) — starte leer", pfad.name, fehler)
        return {}

    verzeichnis: dict[str, Abwickler] = {}
    for eintrag in roh.get("abwickler", []):
        host = eintrag.get("host")
        adresse = eintrag.get("adresse")
        if not host or not adresse:
            continue
        verzeichnis[host] = Abwickler(
            host=host,
            adresse=adresse,
            zuerst_gesehen=eintrag.get("zuerst_gesehen") or "",
            zuletzt_gesehen=eintrag.get("zuletzt_gesehen") or "",
        )
    return verzeichnis


def lerne(
    verzeichnis: dict[str, Abwickler],
    aktionen: list,
    heute: str,
) -> dict[str, Abwickler]:
    """
    Schreibt die Abwickler der übergebenen Aktionen fort.

    Gibt ein **neues** Verzeichnis zurück statt das alte zu verändern: Der
    Aufrufer schreibt es nur weg, wenn der Lauf auch sonst geklappt hat.

    Ein bekannter Host behält sein ``zuerst_gesehen`` und bekommt die neueste
    Adresse — läuft dort inzwischen eine andere Kampagne, ist die neue die
    interessantere.
    """
    neu = dict(verzeichnis)

    for aktion in aktionen:
        adresse = getattr(aktion, "submit_url", None) or (
            aktion.get("submit_url") if isinstance(aktion, dict) else None
        )
        if not adresse:
            continue

        teile = urlparse(adresse)
        if teile.scheme not in ("http", "https") or not teile.netloc:
            continue
        host = teile.netloc.lower()
        if host in NICHT_LERNEN:
            continue
        if _ist_anmeldung(teile):
            log.debug("Anmeldeseite nicht gelernt: %s", host)
            continue

        vorher = neu.get(host)
        neu[host] = Abwickler(
            host=host,
            adresse=adresse,
            zuerst_gesehen=vorher.zuerst_gesehen if vorher else heute,
            zuletzt_gesehen=heute,
        )

    dazu = len(neu) - len(verzeichnis)
    if dazu:
        log.info("Abwicklerverzeichnis: %s neue(r) Host(s), jetzt %s", dazu, len(neu))
    return neu


def schreibe_verzeichnis(pfad: Path, verzeichnis: dict[str, Abwickler]) -> bool:
    """
    Schreibt das Verzeichnis, wenn sich etwas geändert hat.

    Wie bei ``actions.json``: Ein Commit, der nur einen Zeitstempel dreht,
    verrauscht die Historie. Deshalb wird ``zuletzt_gesehen`` zwar gepflegt,
    aber ein reiner Zeitstempelwechsel zählt nicht als Änderung.
    """
    eintraege = [verzeichnis[host].to_json() for host in sorted(verzeichnis)]
    inhalt = json.dumps({"abwickler": eintraege}, ensure_ascii=False, indent=2) + "\n"

    if pfad.exists():
        alt = pfad.read_text(encoding="utf-8")
        if _ohne_zeitstempel(alt) == _ohne_zeitstempel(inhalt):
            return False

    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_text(inhalt, encoding="utf-8")
    return True


def _ohne_zeitstempel(text: str) -> str:
    """Vergleichsform: alles außer ``zuletzt_gesehen``."""
    try:
        daten = json.loads(text)
    except json.JSONDecodeError:
        return text
    for eintrag in daten.get("abwickler", []):
        eintrag.pop("zuletzt_gesehen", None)
    return json.dumps(daten, ensure_ascii=False, sort_keys=True)


def finde(pfad: Path, quellenname: str | None = None) -> list[Kandidat]:
    """
    Gibt die gelernten Adressen als Kandidaten zurück.

    Kein Netzzugriff — die Adressen stehen in der Datei. Ob dahinter noch eine
    laufende Aktion steht, entscheidet wie immer erst ``extract`` und danach
    ``pruefung``.
    """
    verzeichnis = lies_verzeichnis(pfad)
    herkunft = quellenname or "gelernt"

    kandidaten = [
        Kandidat(
            url=eintrag.adresse,
            entdeckt_ueber=f"{herkunft}:{eintrag.host}",
            zuerst_gesehen=eintrag.zuerst_gesehen or None,
        )
        for eintrag in verzeichnis.values()
    ]
    log.info("Gelernte Abwickler: %s Adresse(n) aus %s", len(kandidaten), pfad.name)
    return kandidaten
