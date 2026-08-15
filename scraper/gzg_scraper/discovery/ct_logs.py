"""
Kampagnen über Certificate-Transparency-Logs finden.

Jedes ausgestellte TLS-Zertifikat wird nach RFC 6962 in ein oeffentliches,
manipulationssicheres Protokoll geschrieben. Legt eine Aktionsplattform je
Kampagne eine eigene Subdomain an, steht die neue Kampagne dort binnen Minuten —
lange bevor ein Portal davon erfaehrt. Belegt fuer JustSnap: die Air-Wick-Aktion
laeuft unter ``airwick.justsnap.eu``.

Eine Abfrage am Tag je Plattform genuegt::

    https://crt.sh/?q=%25.justsnap.eu&output=json

Das ist **rein passiv**: oeffentliche Register lesen, keine Anfrage an die
Zielsysteme, kein Scannen, kein Erraten von Namen. Und es findet nur, was
ohnehin veroeffentlicht wurde.

Was es *nicht* leistet: Plattformen, die ihre Kampagnen ueber Pfade statt
Subdomains fuehren, tauchen hier nicht auf — dafuer gibt es ``sitemap``.
"""

from __future__ import annotations

import json
import logging
from urllib.parse import quote

from . import Kandidat

log = logging.getLogger(__name__)

CRT_SH = "https://crt.sh/?q={}&output=json"

# Namen, hinter denen nie eine Kampagne steckt. Ohne diese Liste besteht das
# Ergebnis zu zwei Dritteln aus Infrastruktur, und jeder Eintrag kostet einen
# Seitenabruf.
INFRASTRUKTUR = {
    "www", "mail", "smtp", "imap", "pop", "mx", "ns", "ns1", "ns2",
    "autodiscover", "autoconfig", "cpanel", "webmail", "webdisk",
    "api", "cdn", "static", "assets", "img", "images", "media",
    "admin", "portal", "dashboard", "login", "auth", "sso", "id",
    "test", "testing", "dev", "development", "stage", "staging", "demo",
    "preview", "beta", "sandbox", "local", "localhost",
    "git", "ci", "build", "jenkins", "grafana", "kibana", "status",
    "vpn", "proxy", "gateway", "internal", "intern",
}


def _brauchbar(name: str, basis: str) -> bool:
    """
    Filtert Platzhalter, Infrastruktur und mehrstufige Namen heraus.

    Mehrstufig (``a.b.plattform.de``) faellt bewusst weg: Kampagnen sitzen bei
    den geprueften Plattformen direkt unter der Basis, tiefere Ebenen sind
    Technik. Lieber eine Kampagne verpassen als hundert Abrufe ins Leere.
    """
    name = name.strip().casefold().rstrip(".")

    if not name or name.startswith("*") or not name.endswith("." + basis):
        return False

    davor = name[: -(len(basis) + 1)]
    if not davor or "." in davor:
        return False

    return davor not in INFRASTRUKTUR


def lies_antwort(inhalt: str | None, basis: str) -> list[Kandidat]:
    """
    Wertet die JSON-Antwort von crt.sh aus.

    Als eigene Funktion, damit die Tests sie gegen eine gespeicherte Antwort
    pruefen koennen — ohne Netz, wie alle anderen Parser-Tests auch.

    Ein Name kann in vielen Zertifikaten stehen (Erneuerung alle 90 Tage). Es
    zaehlt der **frueheste** Zeitpunkt: Wann die Kampagne entstand, nicht wann
    ihr Zertifikat zuletzt erneuert wurde.
    """
    if not inhalt:
        return []

    try:
        eintraege = json.loads(inhalt)
    except json.JSONDecodeError as fehler:
        log.warning("Antwort von crt.sh für %s nicht lesbar: %s", basis, fehler)
        return []

    if not isinstance(eintraege, list):
        return []

    frueheste: dict[str, str | None] = {}

    for eintrag in eintraege:
        if not isinstance(eintrag, dict):
            continue
        seit = eintrag.get("not_before")
        # Ein Zertifikat deckt oft mehrere Namen ab; sie stehen zeilenweise.
        for name in str(eintrag.get("name_value") or "").splitlines():
            name = name.strip().casefold().rstrip(".")
            if not _brauchbar(name, basis):
                continue
            vorher = frueheste.get(name)
            if name not in frueheste or (seit and vorher and seit < vorher):
                frueheste[name] = seit

    kandidaten = [
        Kandidat(
            url=f"https://{name}/",
            entdeckt_ueber=f"ct:{basis}",
            zuerst_gesehen=(seit or None),
        )
        for name, seit in sorted(frueheste.items())
    ]

    log.info(
        "crt.sh %s: %s Einträge → %s mögliche Kampagnen",
        basis,
        len(eintraege),
        len(kandidaten),
    )
    return kandidaten


def finde(basis: str, fetcher) -> list[Kandidat]:
    """
    Holt alle je protokollierten Subdomains einer Plattform.

    ``basis`` ist die nackte Domain, etwa ``justsnap.eu``. Ein Abruf je
    Plattform und Lauf — crt.sh braucht keinen Schluessel.
    """
    adresse = CRT_SH.format(quote(f"%.{basis}"))
    inhalt = fetcher.hole(adresse)
    if inhalt is None:
        log.warning("crt.sh für %s nicht erreichbar — übersprungen", basis)
        return []
    return lies_antwort(inhalt, basis)
