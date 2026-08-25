"""
Kampagnen über Certificate-Transparency-Logs finden.

Jedes ausgestellte TLS-Zertifikat wird nach RFC 6962 in ein oeffentliches,
manipulationssicheres Protokoll geschrieben. Legt eine Aktionsplattform je
Kampagne eine eigene Subdomain an, steht die neue Kampagne dort binnen Minuten —
lange bevor ein Portal davon erfaehrt. Belegt fuer JustSnap: die Air-Wick-Aktion
laeuft unter ``airwick.justsnap.eu``.

Eine Abfrage am Tag je Plattform genuegt. Das ist **rein passiv**: oeffentliche
Register lesen, keine Anfrage an die Zielsysteme, kein Scannen, kein Erraten von
Namen. Und es findet nur, was ohnehin veroeffentlicht wurde.

Zwei Zugaenge zu denselben Protokollen, weil der naheliegende ausfaellt:

``certspotter``  Die dokumentierte API von SSLMate. Der Standardweg.
``crt.sh``       Die bekanntere Adresse — aber ihre ``robots.txt`` verbietet
                 den Abruf, und wir halten uns daran (siehe DECISIONS.md).
                 Bleibt drin fuer den Fall, dass jemand mit ausdruecklicher
                 Erlaubnis laeuft; ohne ``--ignore-robots`` passiert nichts.

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

# Die Ausgabe ist auf 100 Eintraege je Abruf gedeckelt; weiter geht es ueber
# ``after=<id>``. ``expand`` sorgt dafuer, dass Namen und Ausstellungszeitpunkt
# gleich mitkommen, statt sie je Zertifikat einzeln nachzuladen.
CERTSPOTTER = (
    "https://api.certspotter.com/v1/issuances"
    "?domain={}&include_subdomains=true&expand=dns_names&expand=not_before"
)

# Wieviele Seiten der Certspotter-Ausgabe hoechstens geholt werden.
#
# Ohne Deckel laeuft eine grosse Plattform den Lauf leer; mit einem stillen
# Deckel faellt das Fehlende niemandem auf. Deshalb beides: begrenzen **und**
# im Log sagen, dass begrenzt wurde.
MAX_SEITEN = 5

STANDARD_ANBIETER = "certspotter"

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


def _lade_liste(inhalt: str | None, basis: str, anbieter: str) -> list[dict]:
    """Macht aus der Antwort eine Liste von Objekten — oder eine leere Liste."""
    if not inhalt:
        return []
    try:
        eintraege = json.loads(inhalt)
    except json.JSONDecodeError as fehler:
        log.warning("Antwort von %s für %s nicht lesbar: %s", anbieter, basis, fehler)
        return []
    return eintraege if isinstance(eintraege, list) else []


def _zu_kandidaten(paare: list[tuple[str, str | None]], basis: str) -> list[Kandidat]:
    """
    Fasst (Name, Ausstellungszeitpunkt) zu Kandidaten zusammen.

    Ein Name kann in vielen Zertifikaten stehen (Erneuerung alle 90 Tage). Es
    zaehlt der **frueheste** Zeitpunkt: Wann die Kampagne entstand, nicht wann
    ihr Zertifikat zuletzt erneuert wurde.
    """
    frueheste: dict[str, str | None] = {}

    for name, seit in paare:
        name = name.strip().casefold().rstrip(".")
        if not _brauchbar(name, basis):
            continue
        vorher = frueheste.get(name)
        if name not in frueheste or (seit and vorher and seit < vorher):
            frueheste[name] = seit

    return [
        Kandidat(
            url=f"https://{name}/",
            entdeckt_ueber=f"ct:{basis}",
            zuerst_gesehen=(seit or None),
        )
        for name, seit in sorted(frueheste.items())
    ]


def lies_antwort(inhalt: str | None, basis: str) -> list[Kandidat]:
    """
    Wertet die JSON-Antwort von crt.sh aus.

    Als eigene Funktion, damit die Tests sie gegen eine gespeicherte Antwort
    pruefen koennen — ohne Netz, wie alle anderen Parser-Tests auch.
    """
    eintraege = _lade_liste(inhalt, basis, "crt.sh")

    paare: list[tuple[str, str | None]] = []
    for eintrag in eintraege:
        if not isinstance(eintrag, dict):
            continue
        seit = eintrag.get("not_before")
        # Ein Zertifikat deckt oft mehrere Namen ab; sie stehen zeilenweise.
        for name in str(eintrag.get("name_value") or "").splitlines():
            paare.append((name, seit))

    kandidaten = _zu_kandidaten(paare, basis)
    log.info(
        "crt.sh %s: %s Einträge → %s mögliche Kampagnen",
        basis,
        len(eintraege),
        len(kandidaten),
    )
    return kandidaten


def lies_certspotter(inhalt: str | None, basis: str) -> list[Kandidat]:
    """
    Wertet die JSON-Antwort der Certspotter-API aus.

    Unterschied zu crt.sh: Die Namen stehen als Liste in ``dns_names`` statt
    zeilenweise in einem Textfeld. Der Rest ist dieselbe Frage — welcher Name,
    seit wann.
    """
    eintraege = _lade_liste(inhalt, basis, "certspotter")

    paare: list[tuple[str, str | None]] = []
    for eintrag in eintraege:
        if not isinstance(eintrag, dict):
            continue
        seit = eintrag.get("not_before")
        namen = eintrag.get("dns_names")
        if not isinstance(namen, list):
            continue
        for name in namen:
            paare.append((str(name), seit))

    kandidaten = _zu_kandidaten(paare, basis)
    log.info(
        "certspotter %s: %s Einträge → %s mögliche Kampagnen",
        basis,
        len(eintraege),
        len(kandidaten),
    )
    return kandidaten


def _letzte_id(inhalt: str | None) -> str | None:
    """Die Id des letzten Eintrags — der Anker fuer die naechste Seite."""
    eintraege = _lade_liste(inhalt, "", "certspotter")
    if not eintraege or not isinstance(eintraege[-1], dict):
        return None
    kennung = eintraege[-1].get("id")
    return str(kennung) if kennung is not None else None


def _finde_certspotter(basis: str, fetcher) -> list[Kandidat]:
    """Holt die Ausgabe seitenweise, bis nichts mehr kommt."""
    gefunden: list[Kandidat] = []
    gesehen: set[str] = set()
    adresse = CERTSPOTTER.format(quote(basis))

    for seite in range(MAX_SEITEN):
        inhalt = fetcher.hole(adresse)
        if inhalt is None:
            if seite == 0:
                log.warning("certspotter für %s nicht erreichbar — übersprungen", basis)
            return gefunden

        for kandidat in lies_certspotter(inhalt, basis):
            if kandidat.url not in gesehen:
                gesehen.add(kandidat.url)
                gefunden.append(kandidat)

        weiter = _letzte_id(inhalt)
        if not weiter:
            return gefunden
        adresse = f"{CERTSPOTTER.format(quote(basis))}&after={quote(weiter)}"

    # Nicht still abschneiden: Sonst liest sich ein halbes Ergebnis wie ein
    # vollstaendiges, und niemand kommt auf die Idee, MAX_SEITEN anzuheben.
    log.warning(
        "certspotter %s: nach %s Seiten abgebrochen — es kann mehr geben",
        basis,
        MAX_SEITEN,
    )
    return gefunden


def finde(basis: str, fetcher, anbieter: str = STANDARD_ANBIETER) -> list[Kandidat]:
    """
    Holt alle je protokollierten Subdomains einer Plattform.

    ``basis`` ist die nackte Domain, etwa ``justsnap.eu``. Keiner der beiden
    Anbieter braucht einen Schluessel.
    """
    if anbieter == "crt.sh":
        adresse = CRT_SH.format(quote(f"%.{basis}"))
        inhalt = fetcher.hole(adresse)
        if inhalt is None:
            log.warning("crt.sh für %s nicht erreichbar — übersprungen", basis)
            return []
        return lies_antwort(inhalt, basis)

    if anbieter != STANDARD_ANBIETER:
        log.warning("Unbekannter CT-Anbieter %r — %s wird benutzt", anbieter, STANDARD_ANBIETER)

    return _finde_certspotter(basis, fetcher)
