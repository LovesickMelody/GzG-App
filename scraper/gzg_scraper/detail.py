"""
Holt je Aktion die Detailseite nach — fuer Einreichungslink und Bedingungen.

Warum ueberhaupt: Auf der Uebersichtsseite steht der Titel, aber nicht, **wo**
man einreicht. Der Link zum Formular des Herstellers taucht erst auf der
Detailseite des Portals auf, und dort stehen auch die Teilnahmebedingungen, aus
denen sich die Checkliste "Was brauche ich?" ableiten laesst.

Was das kostet: einen zusaetzlichen Abruf je Aktion. Bei einem Dutzend Aktionen
und drei Sekunden Pause ist das unter einer Minute — vertretbar fuer einen Lauf
pro Tag. Quellen ohne ``detail``-Block ruehrt dieser Schritt nicht an.

Scheitert ein einzelner Abruf, behaelt die Aktion einfach ihre bisherigen
Angaben. Eine Aktion ohne Einreichungslink ist immer noch besser als keine.
"""

from __future__ import annotations

import logging
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from .fetch import Fetcher
from .models import Action
from .parsing import anforderungen_aus, saeubere

log = logging.getLogger(__name__)


def _finde_link(suppe: BeautifulSoup, selektor: str, beschriftung: str | None) -> str | None:
    """
    Sucht den Link zur Einreichungsseite.

    Portale benutzen dieselbe Knopf-Klasse gern mehrfach — fuer "Zur Aktion",
    aber auch fuer "Teilnahmebedingungen" oder den eigenen Newsletter. Ist eine
    [beschriftung] angegeben, zaehlt nur ein Treffer, dessen Text sie enthaelt.
    """
    selektor, _, attribut = selektor.partition("@")
    attribut = attribut.strip() or "href"

    try:
        treffer = suppe.select(selektor.strip())
    except Exception as fehler:  # noqa: BLE001
        log.warning("Ungültiger Detail-Selektor %r: %s", selektor, fehler)
        return None

    for knoten in treffer:
        ziel = knoten.get(attribut)
        if not ziel:
            continue
        if beschriftung:
            text = knoten.get_text(" ", strip=True).casefold()
            if beschriftung.casefold() not in text:
                continue
        return saeubere(ziel)

    return None


def reichere_an(aktionen: list[Action], quelle: dict, fetcher: Fetcher) -> None:
    """
    Ergaenzt ``submit_url`` und ``requirements`` aus den Detailseiten.

    Arbeitet auf der Liste selbst, weil die Aktionen danach unveraendert
    weiterverarbeitet werden — nur eben vollstaendiger.
    """
    einstellungen = quelle.get("detail") or {}
    if not einstellungen.get("enabled"):
        return

    link_selektor = einstellungen.get("submit_link")
    link_text = einstellungen.get("submit_link_text")
    text_selektor = einstellungen.get("text")
    basis = quelle.get("base_url", "")

    ergaenzt = 0
    for aktion in aktionen:
        if not aktion.url:
            continue

        html = fetcher.hole(aktion.url)
        if html is None:
            continue

        suppe = BeautifulSoup(html, "lxml")

        if link_selektor and not aktion.submit_url:
            ziel = _finde_link(suppe, link_selektor, link_text)
            if ziel:
                aktion.submit_url = urljoin(basis, ziel)

        if not aktion.requirements:
            bereich = suppe.select_one(text_selektor) if text_selektor else suppe
            if bereich is not None:
                aktion.requirements = anforderungen_aus(bereich.get_text(" ", strip=True))

        if aktion.submit_url or aktion.requirements:
            ergaenzt += 1

    log.info(
        "Quelle %s: %s von %s Aktionen um Detailangaben ergänzt",
        quelle["name"],
        ergaenzt,
        len(aktionen),
    )
