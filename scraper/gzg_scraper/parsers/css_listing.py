"""
Ein selektorgesteuerter Parser fuer Uebersichtsseiten.

Warum konfigurierbar statt je Portal handgeschrieben: Diese Seiten aendern ihr
Markup oefter als ihre Struktur. Steht die Zuordnung in ``sources.yaml``, ist
eine kaputte Quelle mit einer geaenderten Zeile YAML repariert — ohne
Python-Kenntnisse und ohne neuen Test. Braucht ein Portal doch einmal echte
Logik, kommt daneben ein eigener Parser ins Register.
"""

from __future__ import annotations

import logging
import re
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from ..models import Action
from ..parsing import (
    art_aus_text,
    betrag_in_cent,
    datum_iso,
    eans_aus,
    haendler_aus,
    saeubere,
)

log = logging.getLogger(__name__)

# Nur Namen, die als Filter in der App etwas taugen.
BEKANNTE_HAENDLER = [
    "dm", "Rossmann", "Müller", "Edeka", "Rewe", "Kaufland", "Lidl", "Aldi",
    "Netto", "Penny", "Norma", "Real", "Globus", "Budni", "tegut",
]


def _text(knoten, selektor: str | None) -> str | None:
    """
    Liest Text oder Attribut zu einem Selektor.

    ``"a@href"`` liest das Attribut ``href`` des ersten Treffers, ``"h2"`` den Text.
    """
    if not selektor:
        return None

    selektor, _, attribut = selektor.partition("@")
    treffer = knoten.select_one(selektor.strip()) if selektor.strip() else knoten
    if treffer is None:
        return None

    if attribut:
        wert = treffer.get(attribut.strip())
        if isinstance(wert, list):
            wert = " ".join(wert)
        return saeubere(wert)

    return saeubere(treffer.get_text(" ", strip=True))


def _kuerze_titel(titel: str, muster: str | None) -> str:
    """
    Entfernt einen wiederkehrenden Zusatz aus dem Titel.

    Manche Portale haengen an jeden Titel dieselbe Kennzeichnung — etwa
    "[gratis testen, Geld zurueck!]". In der App steht das dann neunzehnmal
    untereinander und verdeckt den Produktnamen. Bleibt nach dem Kuerzen nichts
    uebrig, gilt der urspruengliche Titel: lieber laut als leer.
    """
    if not muster:
        return titel
    gekuerzt = saeubere(re.sub(muster, " ", titel))
    return gekuerzt or titel


def parse(html: str, quelle: dict) -> list[Action]:
    """Zerlegt eine Uebersichtsseite anhand der Selektoren aus ``sources.yaml``."""
    suppe = BeautifulSoup(html, "lxml")
    selektoren: dict = quelle.get("selectors", {})
    basis: str = quelle.get("base_url", "")
    name: str = quelle["name"]

    eintraege = suppe.select(selektoren.get("item", ""))
    if not eintraege:
        log.warning(
            "Quelle %s: Selektor %r trifft nichts — Markup vermutlich geändert",
            name,
            selektoren.get("item"),
        )
        return []

    aktionen: list[Action] = []
    for eintrag in eintraege:
        titel = _text(eintrag, selektoren.get("title"))
        if not titel:
            continue
        titel = _kuerze_titel(titel, quelle.get("titel_entfernen"))

        volltext = eintrag.get_text(" ", strip=True)

        link = _text(eintrag, selektoren.get("link"))
        bild = _text(eintrag, selektoren.get("image"))

        betrag_text = _text(eintrag, selektoren.get("max_refund")) or volltext
        frist_text = _text(eintrag, selektoren.get("deadline"))
        gueltig_text = _text(eintrag, selektoren.get("valid_to"))

        aktion = Action(
            title=titel,
            source=name,
            brand=_text(eintrag, selektoren.get("brand")),
            type=art_aus_text(volltext),
            max_refund_cents=betrag_in_cent(betrag_text),
            valid_from=datum_iso(_text(eintrag, selektoren.get("valid_from"))),
            valid_to=datum_iso(gueltig_text),
            # Fehlt eine ausgewiesene Einsendefrist, dient das Aktionsende als
            # Ersatz — lieber eine Frist zu frueh anzeigen als gar keine.
            submission_deadline=datum_iso(frist_text) or datum_iso(gueltig_text),
            url=urljoin(basis, link) if link else None,
            retailers=haendler_aus(volltext, quelle.get("retailers", BEKANNTE_HAENDLER)),
            eans=eans_aus(volltext),
            image_url=urljoin(basis, bild) if bild else None,
        )
        aktionen.append(aktion)

    return aktionen
