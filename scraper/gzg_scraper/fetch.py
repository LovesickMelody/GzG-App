"""HTTP-Zugriff: hoeflich, langsam und mit Blick auf robots.txt."""

from __future__ import annotations

import logging
import time
import urllib.robotparser
from dataclasses import dataclass, field
from urllib.parse import urljoin, urlparse

import requests

log = logging.getLogger(__name__)

# Ehrlicher User-Agent mit Kontaktmoeglichkeit. Ein getarnter Browser-UA waere
# gegenueber den Betreibern unfair und macht Probleme schwerer nachvollziehbar.
USER_AGENT = (
    "GZG-Tracker-Scraper/1.0 (privates Projekt; "
    "+https://github.com/LovesickMelody/GzG-App)"
)


@dataclass
class Fetcher:
    """
    Holt Seiten und haelt sich dabei zurueck.

    - fragt einmal je Host die robots.txt und respektiert sie
    - wartet [delay] Sekunden zwischen zwei Abrufen desselben Hosts
    - bricht nach [timeout] Sekunden ab, statt den Job haengen zu lassen
    """

    delay: float = 2.0
    timeout: float = 20.0
    respect_robots: bool = True
    session: requests.Session = field(default_factory=requests.Session)

    _robots: dict[str, urllib.robotparser.RobotFileParser | None] = field(
        default_factory=dict, repr=False
    )
    _letzter_abruf: dict[str, float] = field(default_factory=dict, repr=False)

    def __post_init__(self) -> None:
        self.session.headers.update(
            {
                "User-Agent": USER_AGENT,
                "Accept": "text/html,application/xhtml+xml",
                "Accept-Language": "de-DE,de;q=0.9",
            }
        )

    def darf(self, url: str) -> bool:
        if not self.respect_robots:
            return True
        regeln = self._robots_fuer(url)
        if regeln is None:
            # Keine robots.txt erreichbar: als erlaubt behandeln, so sieht es
            # der Standard vor.
            return True
        return regeln.can_fetch(USER_AGENT, url)

    def hole(self, url: str, still: bool = False) -> str | None:
        """Gibt den HTML-Text zurueck oder ``None``, wenn es nicht geklappt hat."""
        seite = self.hole_seite(url, still=still)
        return seite[0] if seite else None

    def hole_seite(self, url: str, still: bool = False) -> tuple[str, str] | None:
        """
        Wie [hole], gibt aber zusaetzlich die Adresse zurueck, bei der man
        wirklich gelandet ist.

        Der Unterschied zaehlt bei Weiterleitungen: mydealz verlinkt ueber eine
        eigene Zwischenseite, und in der App sah man beim Einreichen erst das
        mydealz-Logo, statt gleich beim Hersteller zu sein. Wer die
        Zieladdresse kennt, kann sie speichern und die Zwischenseite ueberspringen.

        [still] dreht das Melden eines Fehlschlags auf ``debug``. Gedacht fuer
        Adressen, deren Fehlen der Normalfall ist — ``/.well-known/tdmrep.json``
        gibt es auf den wenigsten Seiten, und eine Warnung je Host und Lauf
        wuerde das Log zumuellen, in dem man die echten Probleme sucht.
        """
        melde = log.debug if still else log.warning

        if not self.darf(url):
            melde("robots.txt verbietet %s — übersprungen", url)
            return None

        self._warte(url)

        try:
            antwort = self.session.get(url, timeout=self.timeout)
        except requests.RequestException as fehler:
            melde("Abruf von %s fehlgeschlagen: %s", url, fehler)
            return None

        if antwort.status_code != 200:
            melde("%s antwortet mit %s", url, antwort.status_code)
            return None

        # Ohne diese Zeile raet requests bei fehlendem Charset auf ISO-8859-1
        # und aus "für" wird "fÃ¼r".
        if antwort.encoding is None or antwort.encoding.lower() == "iso-8859-1":
            antwort.encoding = antwort.apparent_encoding

        return antwort.text, str(antwort.url)

    def _warte(self, url: str) -> None:
        host = urlparse(url).netloc
        vorher = self._letzter_abruf.get(host)
        if vorher is not None:
            rest = self.delay - (time.monotonic() - vorher)
            if rest > 0:
                time.sleep(rest)
        self._letzter_abruf[host] = time.monotonic()

    def _robots_fuer(self, url: str):
        teile = urlparse(url)
        host = f"{teile.scheme}://{teile.netloc}"
        if host in self._robots:
            return self._robots[host]

        parser = urllib.robotparser.RobotFileParser()
        robots_url = urljoin(host, "/robots.txt")
        try:
            antwort = self.session.get(robots_url, timeout=self.timeout)
            if antwort.status_code == 200:
                parser.parse(antwort.text.splitlines())
            else:
                parser = None  # type: ignore[assignment]
        except requests.RequestException as fehler:
            log.info("robots.txt von %s nicht lesbar: %s", host, fehler)
            parser = None  # type: ignore[assignment]

        self._robots[host] = parser
        return parser
