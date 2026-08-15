"""
Generische Extraktion: erst die strukturierten Daten, dann das Modell.

Die Reihenfolge ist der ganze Punkt. ``jsonld`` kostet nichts und liefert
exakte Angaben, wo eine Seite sie fuer Suchmaschinen ohnehin veroeffentlicht;
das Modell kommt nur dran, wo das nichts hergibt. Bei einer Plattform mit
sauberem Markup laeuft ein ganzer Lauf damit ohne einen einzigen Modellaufruf.

Der Betrag kommt in **beiden** Wegen aus ``parsing.betrag_in_cent`` und damit
aus dem sichtbaren Seitentext — nie aus einem Feld, das jemand anders berechnet
hat. Warum das so ist, steht in ``jsonld`` (Ladenpreis ist nicht Erstattung)
und in ``modell`` (ein Modell rechnet nicht, es zitiert).
"""

from __future__ import annotations

import logging

from bs4 import BeautifulSoup

from ..models import Action
from ..parsing import anforderungen_aus, art_aus_text, betrag_in_cent
from . import jsonld
from .modell import Modellextraktor

log = logging.getLogger(__name__)

__all__ = ["Modellextraktor", "extrahiere", "sichtbarer_text"]


def sichtbarer_text(html: str | None) -> str:
    """Text ohne Skripte und Stilbloecke — Grundlage jeder Betragspruefung."""
    if not html:
        return ""
    suppe = BeautifulSoup(html, "lxml")
    for stoerer in suppe(["script", "style", "noscript"]):
        stoerer.decompose()
    return suppe.get_text(" ", strip=True)


def extrahiere(
    html: str | None,
    url: str,
    quellenname: str,
    extraktor: Modellextraktor | None = None,
) -> Action | None:
    """
    Liest eine Aktion aus einer beliebigen Seite.

    Gibt ``None`` zurueck, wenn die Seite keine Aktion beschreibt oder zu wenig
    hergibt. Der Aufrufer wirft den Kandidaten dann weg — bei einer Entdeckung
    ueber Zertifikatsprotokolle sind die meisten Adressen ohnehin Infrastruktur
    und keine Kampagne.
    """
    if not html:
        return None

    text = sichtbarer_text(html)

    aktion = jsonld.lies(html, quellenname, url)
    herkunft = "JSON-LD"

    if aktion is None and extraktor is not None:
        aktion = extraktor.lies(text, url, quellenname)
        herkunft = "Modell"

    if aktion is None:
        return None

    # Was JSON-LD grundsaetzlich nicht liefert, kommt aus dem Text — mit
    # denselben Parsern, die seit jeher die Portale auswerten.
    if aktion.max_refund_cents is None:
        aktion.max_refund_cents = betrag_in_cent(text)
    if not aktion.requirements:
        aktion.requirements = anforderungen_aus(text)

    aktion.type = art_aus_text(f"{aktion.title} {text[:2000]}", aktion.max_refund_cents)

    log.info(
        "%s: %r über %s (Betrag=%s, Frist=%s)",
        quellenname,
        aktion.title[:50],
        herkunft,
        aktion.max_refund_cents,
        aktion.submission_deadline or "—",
    )
    return aktion
