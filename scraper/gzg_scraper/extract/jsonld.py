"""
Strukturierte Daten aus der Seite lesen, bevor irgendjemand raet.

Sehr viele Kampagnenseiten betten ihre Eckdaten als ``schema.org``-JSON-LD ein,
damit Suchmaschinen sie verstehen. Das ist die beste Quelle, die es auf so einer
Seite gibt: vom Anbieter selbst gepflegt, ausdruecklich fuer Maschinen
veroeffentlicht, exakt statt geraten — und kostenlos. Deshalb laeuft dieser
Schritt vor dem Modell, nicht danach.

Was hier bewusst **nicht** uebernommen wird: der Preis. In einem
``Product``-Knoten steht unter ``offers.price`` der *Verkaufspreis des
Produkts*, nicht die Hoehe der Erstattung. Bei "gratis testen" ist beides oft
dasselbe, aber eben nur oft — sobald die Aktion bei "bis zu 4,99 €" deckelt,
waeren wir mit dem Ladenpreis daneben, und zwar nach oben. Der Betrag kommt
deshalb aus dem Flaeschentext ueber ``parsing.betrag_in_cent`` und muss
anschliessend die Belegpruefung in ``pruefung`` bestehen.
"""

from __future__ import annotations

import json
import logging

from bs4 import BeautifulSoup

from ..models import Action
from ..parsing import datum_iso, saeubere

log = logging.getLogger(__name__)

# Knotenarten, die eine Aktion beschreiben koennen. Andere (Organization,
# BreadcrumbList, WebSite) tragen nichts bei und werden uebergangen.
_INTERESSANT = {
    "product",
    "offer",
    "aggregateoffer",
    "saleevent",
    "event",
    "specialannouncement",
}


def _knoten(daten) -> list[dict]:
    """
    Faltet die ueblichen JSON-LD-Verschachtelungen zu einer flachen Liste.

    Anbieter liefern mal ein einzelnes Objekt, mal eine Liste, mal ein
    ``@graph`` — und verschachteln ``offers`` noch einmal darin.
    """
    gesammelt: list[dict] = []

    def gehe(wert) -> None:
        if isinstance(wert, list):
            for eintrag in wert:
                gehe(eintrag)
        elif isinstance(wert, dict):
            gesammelt.append(wert)
            for schluessel in ("@graph", "offers", "mainEntity", "itemOffered"):
                if schluessel in wert:
                    gehe(wert[schluessel])

    gehe(daten)
    return gesammelt


def _typen(knoten: dict) -> set[str]:
    art = knoten.get("@type") or knoten.get("type") or []
    if isinstance(art, str):
        art = [art]
    return {str(a).casefold() for a in art if isinstance(a, str)}


def _text(wert) -> str | None:
    """Holt einen Textwert, auch wenn er als Objekt oder Liste ankommt."""
    if isinstance(wert, str):
        return saeubere(wert)
    if isinstance(wert, dict):
        return _text(wert.get("name") or wert.get("@value") or wert.get("url"))
    if isinstance(wert, list):
        for eintrag in wert:
            gefunden = _text(eintrag)
            if gefunden:
                return gefunden
    return None


def lies(html: str | None, quellenname: str, url: str | None = None) -> Action | None:
    """
    Baut eine Aktion aus den JSON-LD-Angaben einer Seite.

    Gibt ``None`` zurueck, wenn die Seite keine brauchbaren strukturierten Daten
    hat — dann ist das Modell dran. Ein Titel allein reicht als Ergebnis nicht:
    Ohne mindestens eine zweite Angabe (Marke, Frist oder Bild) waere der
    Eintrag so duenn, dass der teurere Weg ohnehin besser ist.
    """
    if not html:
        return None

    suppe = BeautifulSoup(html, "lxml")
    knoten: list[dict] = []

    for block in suppe.find_all("script", attrs={"type": "application/ld+json"}):
        roh = block.string or block.get_text() or ""
        if not roh.strip():
            continue
        try:
            knoten.extend(_knoten(json.loads(roh)))
        except (json.JSONDecodeError, TypeError, ValueError) as fehler:
            # Kaputtes JSON-LD ist haeufig und kein Grund, die Seite aufzugeben.
            log.debug("JSON-LD auf %s nicht lesbar: %s", url or "?", fehler)

    passend = [k for k in knoten if _typen(k) & _INTERESSANT]
    if not passend:
        return None

    titel = brand = bild = gueltig_von = gueltig_bis = None

    for eintrag in passend:
        titel = titel or _text(eintrag.get("name")) or _text(eintrag.get("headline"))
        brand = brand or _text(eintrag.get("brand")) or _text(eintrag.get("manufacturer"))
        bild = bild or _text(eintrag.get("image")) or _text(eintrag.get("logo"))
        gueltig_von = gueltig_von or datum_iso(_text(eintrag.get("validFrom")) or _text(eintrag.get("startDate")))
        gueltig_bis = gueltig_bis or datum_iso(
            _text(eintrag.get("validThrough"))
            or _text(eintrag.get("priceValidUntil"))
            or _text(eintrag.get("endDate"))
        )

    if not titel:
        return None
    if not any((brand, gueltig_bis, bild)):
        log.debug("JSON-LD auf %s hat nur einen Titel — zu dünn", url or "?")
        return None

    return Action(
        title=titel,
        source=quellenname,
        brand=brand,
        url=url,
        submit_url=url,
        valid_from=gueltig_von,
        valid_to=gueltig_bis,
        submission_deadline=gueltig_bis,
        image_url=bild,
    )
