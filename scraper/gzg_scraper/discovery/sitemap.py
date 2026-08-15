"""
Kampagnen über die ``sitemap.xml`` der Plattform finden.

Eine Sitemap ist — wie ein RSS-Feed — eine ausdrueckliche Einladung, maschinell
gelesen zu werden. Sie faengt genau die Anbieter ab, die ``ct_logs`` nicht
sieht: solche, die ihre Kampagnen ueber Pfade fuehren statt ueber Subdomains
(``…/template/airwick-gratis-testen/``).

Ein Sitemap-Index verweist auf weitere Sitemaps. Verfolgt wird genau **eine**
Ebene tief. Tiefer zu gehen liefe bei einer grossen Seite auf Dutzende Abrufe
hinaus, ohne dass bei den geprueften Plattformen noch etwas dazukaeme.
"""

from __future__ import annotations

import logging
import re

from bs4 import BeautifulSoup

from . import Kandidat

log = logging.getLogger(__name__)

MAX_INDEX_ABRUFE = 10


def lies_adressen(xml: str | None) -> tuple[list[str], list[str]]:
    """
    Zerlegt eine Sitemap in (Seitenadressen, verwiesene Sitemaps).

    Ein ``<sitemapindex>`` liefert nur den zweiten Wert, eine ``<urlset>`` nur
    den ersten. Der Aufrufer entscheidet, ob er den Verweisen folgt.
    """
    if not xml:
        return [], []

    suppe = BeautifulSoup(xml, "xml")

    if suppe.find("sitemapindex"):
        verweise = [
            ort.get_text(strip=True)
            for eintrag in suppe.find_all("sitemap")
            if (ort := eintrag.find("loc")) is not None
        ]
        return [], [v for v in verweise if v]

    adressen = [
        ort.get_text(strip=True)
        for eintrag in suppe.find_all("url")
        if (ort := eintrag.find("loc")) is not None
    ]
    return [a for a in adressen if a], []


def finde(
    sitemap_url: str,
    fetcher,
    muster: str | None = None,
    quellenname: str | None = None,
) -> list[Kandidat]:
    """
    Sammelt Kandidaten aus einer Sitemap.

    ``muster`` ist ein regulaerer Ausdruck; ohne ihn kaeme bei einer grossen
    Plattform die halbe Seite zurueck, Impressum und Blog eingeschlossen. Mit
    ``muster: "/aktion/"`` bleiben die Kampagnenpfade uebrig.
    """
    herkunft = quellenname or f"sitemap:{sitemap_url}"

    xml = fetcher.hole(sitemap_url)
    if xml is None:
        log.warning("Sitemap %s nicht erreichbar — übersprungen", sitemap_url)
        return []

    adressen, verweise = lies_adressen(xml)

    for verweis in verweise[:MAX_INDEX_ABRUFE]:
        weiteres = fetcher.hole(verweis)
        if weiteres is None:
            continue
        weitere_adressen, _ = lies_adressen(weiteres)
        adressen.extend(weitere_adressen)

    if len(verweise) > MAX_INDEX_ABRUFE:
        # Stilles Abschneiden wäre schlimmer als die Lücke selbst: Man sucht
        # sonst vergeblich nach der Kampagne, die es angeblich nicht gibt.
        log.warning(
            "Sitemap-Index %s hat %s Verweise — nur die ersten %s ausgewertet",
            sitemap_url,
            len(verweise),
            MAX_INDEX_ABRUFE,
        )

    if muster:
        try:
            passend = re.compile(muster)
        except re.error as fehler:
            log.error("Ungültiges Sitemap-Muster %r: %s", muster, fehler)
            return []
        adressen = [a for a in adressen if passend.search(a)]

    gesehen: set[str] = set()
    kandidaten: list[Kandidat] = []
    for adresse in adressen:
        if adresse in gesehen:
            continue
        gesehen.add(adresse)
        kandidaten.append(Kandidat(url=adresse, entdeckt_ueber=herkunft))

    log.info("Sitemap %s: %s Kandidaten", sitemap_url, len(kandidaten))
    return kandidaten
