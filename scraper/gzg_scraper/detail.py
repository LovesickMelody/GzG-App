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
from urllib.parse import urljoin, urlparse

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
    von_aktionsseite = bool(quelle.get("bedingungen_von_aktionsseite"))
    if not einstellungen.get("enabled") and not von_aktionsseite:
        return

    link_selektor = einstellungen.get("submit_link")
    link_text = einstellungen.get("submit_link_text")
    text_selektor = einstellungen.get("text")
    basis = quelle.get("base_url", "")

    ergaenzt = 0
    for aktion in aktionen:
        if einstellungen.get("enabled") and aktion.url:
            html = fetcher.hole(aktion.url)
            if html is not None:
                suppe = BeautifulSoup(html, "lxml")

                if link_selektor and not aktion.submit_url:
                    ziel = _finde_link(suppe, link_selektor, link_text)
                    if ziel:
                        aktion.submit_url = urljoin(basis, ziel)

                if not aktion.requirements:
                    bereich = suppe.select_one(text_selektor) if text_selektor else suppe
                    if bereich is not None:
                        aktion.requirements = anforderungen_aus(
                            bereich.get_text(" ", strip=True)
                        )

        if von_aktionsseite:
            _lies_bedingungen_von_aktionsseite(aktion, fetcher)

        if aktion.submit_url or aktion.requirements:
            ergaenzt += 1

    log.info(
        "Quelle %s: %s von %s Aktionen um Detailangaben ergänzt",
        quelle["name"],
        ergaenzt,
        len(aktionen),
    )


def _uebernimm_weiterleitung(aktion: Action, gelandet: str) -> None:
    """
    Speichert die Adresse, bei der die Weiterleitung wirklich endet.

    mydealz verlinkt ueber ``/visit/threadmain/<id>``. In der App sah man beim
    Einreichen deshalb erst eine Zwischenseite mit fremdem Logo — und wenn die
    haengen blieb, gar nichts. Gespeichert wird ab jetzt das Ziel.

    Bleibt die Weiterleitung auf demselben Host (etwa nur ``/de/`` angehaengt),
    aendert sich nichts Wesentliches, und die urspruengliche Adresse bleibt
    stehen — sie ist die stabilere von beiden.
    """
    if not gelandet or gelandet == aktion.submit_url:
        return

    vorher = urlparse(aktion.submit_url or "")
    nachher = urlparse(gelandet)
    if nachher.scheme not in ("http", "https"):
        return
    if nachher.netloc == vorher.netloc:
        return

    log.info(
        "Aktion %r: Weiterleitung aufgelöst, %s → %s",
        aktion.title[:40],
        aktion.submit_url,
        gelandet,
    )
    aktion.submit_url = gelandet


def _verwirf_unaufloesbare_zwischenseite(aktion: Action) -> None:
    """
    Nimmt einen Einreichungslink zurueck, der nur auf das Portal selbst zeigt.

    mydealz beantwortet einzelne ``/visit/``-Adressen mit 403. Bleibt so eine
    Adresse als ``submit_url`` stehen, tippt man in der App auf "Einreichen" und
    sieht eine leere Seite — genau das ist bei Borotalco passiert. Ohne
    ``submit_url`` faellt die App auf die Deal-Seite zurueck: Die laedt, und der
    Weg zum Hersteller steht dort drin.

    Zeigt der Link dagegen bereits auf einen fremden Host, ist er der richtige
    und bleibt stehen, auch wenn der Anbieter uns gerade nicht antwortet.
    """
    if not aktion.url or not aktion.submit_url:
        return
    if urlparse(aktion.submit_url).netloc != urlparse(aktion.url).netloc:
        return

    log.info(
        "Aktion %r: Zwischenseite %s nicht auflösbar — App nimmt die Portalseite",
        aktion.title[:40],
        aktion.submit_url,
    )
    aktion.submit_url = None


def _lies_bedingungen_von_aktionsseite(aktion: Action, fetcher: Fetcher) -> None:
    """
    Holt die Teilnahmebedingungen dort, wo sie wirklich stehen.

    Die Portalbeschreibung sagt meist nur, *dass* es Geld zurueck gibt. Ob man
    das Produkt, den Bon oder beides zusammen fotografieren muss und ob vorher
    eine Handynummer bestaetigt wird, steht auf der Seite des Anbieters — also
    genau dort, wohin ``submit_url`` fuehrt.

    Genau daran krankte die Checkliste vorher: Sie sah bei jeder Aktion gleich
    aus, weil sie aus immer gleichem Portaltext kam.

    Was hier gefunden wird, ersetzt die schwaechere Angabe aus dem Portal.
    Findet sich nichts, bleibt die alte stehen — schlechter wird es nie.
    """
    if not aktion.submit_url:
        return

    seite = fetcher.hole_seite(aktion.submit_url)
    if seite is None:
        log.info("Aktion %r: Aktionsseite %s nicht erreichbar", aktion.title[:40], aktion.submit_url)
        _verwirf_unaufloesbare_zwischenseite(aktion)
        return
    html, gelandet = seite

    _uebernimm_weiterleitung(aktion, gelandet)

    suppe = BeautifulSoup(html, "lxml")
    # Skripte und Stilbloecke raus: Ihr Inhalt ist kein Text fuer Menschen und
    # bringt die Worterkennung durcheinander.
    for stoerer in suppe(["script", "style", "noscript"]):
        stoerer.decompose()

    gefunden = anforderungen_aus(suppe.get_text(" ", strip=True))
    if gefunden:
        log.info(
            "Aktion %r: Bedingungen von der Aktionsseite — %s",
            aktion.title[:40],
            ", ".join(gefunden),
        )
        aktion.requirements = gefunden
