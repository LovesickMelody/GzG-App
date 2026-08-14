"""
Parser fuer RSS- und Atom-Feeds.

Warum das der bevorzugte Weg ist: Ein Feed ist eine Zusage des Betreibers,
maschinenlesbar zu bleiben. Sein Aufbau aendert sich praktisch nie, waehrend das
HTML derselben Seite jede Umgestaltung mitmacht. Wo ein Portal einen Feed
anbietet, kostet er ausserdem weniger Last als eine vollstaendige Uebersichts-
seite — genau das, was die Betreiber von einem Scraper erwarten duerfen.

Zusaetzliche Felder in ``sources.yaml``:
    keywords      Ein Eintrag zaehlt nur, wenn eines dieser Woerter in Titel
                  oder Beschreibung steht. Noetig bei allgemeinen Deal-Feeds,
                  in denen Geld-zurueck-Aktionen zwischen anderen Angeboten
                  stehen. Fehlt das Feld, zaehlt jeder Eintrag.
    ausschluss    Gegenstueck dazu: Eintraege mit einem dieser Woerter fliegen
                  raus (etwa Gewinnspiele).
    brand_trenner Zeichen, an dem der Markenname vom Rest des Titels getrennt
                  wird, z. B. ":" bei "Valess: 100 % Geld zurueck".
"""

from __future__ import annotations

import logging
import re

from bs4 import BeautifulSoup

from ..models import Action
from ..parsing import (
    anforderungen_aus,
    art_aus_text,
    betrag_in_cent,
    datum_bereich,
    eans_aus,
    haendler_aus,
    kuerze_titel,
    saeubere,
)

log = logging.getLogger(__name__)

# Dieselbe Liste wie im CSS-Parser; bewusst dupliziert statt importiert, damit
# ein Umbau am HTML-Parser den Feed-Parser nicht mitverdreht.
BEKANNTE_HAENDLER = [
    "dm", "Rossmann", "Müller", "Edeka", "Rewe", "Kaufland", "Lidl", "Aldi",
    "Netto", "Penny", "Norma", "Real", "Globus", "Budni", "tegut",
]


def _erster_text(eintrag, *namen: str) -> str | None:
    for name in namen:
        knoten = eintrag.find(name)
        if knoten is not None:
            text = knoten.get_text(" ", strip=True)
            if text:
                return saeubere(text)
    return None


def _link(eintrag) -> str | None:
    """RSS legt den Link in den Elementtext, Atom in das Attribut ``href``."""
    knoten = eintrag.find("link")
    if knoten is None:
        return None
    text = knoten.get_text(strip=True)
    return saeubere(text) or saeubere(knoten.get("href"))


def _bild(eintrag) -> str | None:
    for name, attribut in (("content", "url"), ("thumbnail", "url"), ("enclosure", "url")):
        knoten = eintrag.find(name)
        if knoten is not None and knoten.get(attribut):
            return saeubere(knoten.get(attribut))
    return None


def _anbieter_aus_element(eintrag) -> str | None:
    """
    Liest den Anbieter aus einem eigenen Feed-Element.

    mydealz (Pepper) haengt an jeden Eintrag ``<pepper:merchant name="…"/>``.
    Was da drinsteht, ist mal die Marke ("Milka", "JACOBS Kaffee") und mal der
    Haendler oder die Cashback-Plattform ("dm", "Rossmann", "marktguru") — der
    Einsteller entscheidet das. Deshalb wird der Wert oben einsortiert statt
    blind ins Markenfeld geschrieben.
    """
    for name in ("merchant", "brand", "vendor"):
        knoten = eintrag.find(name)
        if knoten is None:
            continue
        wert = knoten.get("name") or knoten.get_text(" ", strip=True)
        if wert:
            return saeubere(wert)
    return None


# Plattformen, ueber die eingereicht wird. Stehen sie im Anbieterfeld, ist das
# keine Marke — sonst hiesse die Haelfte aller Aktionen "scondoo".
PLATTFORMEN = ("scondoo", "marktguru", "payback", "coupies", "smhaggle")


def _sortiere_anbieter(
    anbieter: str | None, bekannte_haendler: list[str]
) -> tuple[str | None, list[str]]:
    """Entscheidet, ob der Anbieter eine Marke oder ein Haendler/eine Plattform ist."""
    if not anbieter:
        return None, []

    klein = anbieter.casefold()
    if any(plattform in klein for plattform in PLATTFORMEN):
        return None, []

    for haendler in bekannte_haendler:
        if haendler.casefold() == klein:
            # Kanonische Schreibweise nehmen: "ROSSMANN" und "Rossmann" sollen
            # denselben Filtereintrag ergeben.
            return None, [haendler]

    return anbieter, []


def _einreichungslink(link: str | None, regel: dict | None) -> str | None:
    """
    Baut die Einreichungsadresse aus der Adresse des Eintrags.

    Manche Portale halten keinen Link zum Anbieter im Feed, haben dafuer aber
    eine eigene Weiterleitung: mydealz fuehrt ueber ``/visit/threadmain/<id>``
    direkt auf die Aktionsseite. Die Kennnummer steht hinten in der Deal-Adresse.

    Bewusst die Weiterleitung selbst statt ihres Ziels: Sie einmal aufzuloesen
    hiesse ein zusaetzlicher Abruf je Aktion, und die Weiterleitung ist der Weg,
    ueber den das Portal seine Vermittlung zaehlt. Wer die Aktion ueber mydealz
    findet, soll mydealz auch nicht die Anrechnung wegnehmen. Zu sehen bekommt
    man davon nichts — der Browser springt durch.
    """
    if not link or not regel:
        return None
    muster = regel.get("muster")
    vorlage = regel.get("vorlage")
    if not muster or not vorlage:
        return None
    treffer = re.search(muster, link)
    if not treffer:
        return None
    return vorlage.format(*treffer.groups())


def _marke(titel: str, trenner: str | None) -> str | None:
    """
    Schneidet den Markennamen vom Titel ab.

    Nur wenn der Trenner wirklich vorkommt und der linke Teil kurz genug ist,
    um ein Markenname zu sein — sonst landet der halbe Titel im Markenfeld und
    der Filter in der App wird unbrauchbar.
    """
    if not trenner or trenner not in titel:
        return None
    links = titel.split(trenner, 1)[0].strip()
    return links if 0 < len(links) <= 40 else None


def parse(xml: str, quelle: dict) -> list[Action]:
    suppe = BeautifulSoup(xml, "xml")
    eintraege = suppe.find_all(["item", "entry"])
    name: str = quelle["name"]

    if not eintraege:
        log.warning("Quelle %s: Feed enthält keine Einträge", name)
        return []

    schluesselwoerter = [w.casefold() for w in quelle.get("keywords", [])]
    ausschluss = [w.casefold() for w in quelle.get("ausschluss", [])]
    trenner = quelle.get("brand_trenner")

    aktionen: list[Action] = []
    aussortiert = 0

    for eintrag in eintraege:
        titel = _erster_text(eintrag, "title")
        if not titel:
            continue

        beschreibung = _erster_text(eintrag, "description", "summary", "content") or ""
        # Beschreibungen kommen oft als eingepacktes HTML — erst auspacken,
        # sonst sucht die Betragserkennung in Markup statt in Text.
        beschreibung = saeubere(BeautifulSoup(beschreibung, "lxml").get_text(" ")) or ""
        volltext = f"{titel} {beschreibung}"
        pruefbar = volltext.casefold()

        if schluesselwoerter and not any(w in pruefbar for w in schluesselwoerter):
            aussortiert += 1
            continue
        if any(w in pruefbar for w in ausschluss):
            aussortiert += 1
            continue

        # Der Zeitraum steht meist als "17.08.2026-30.09.2026" in einer Zeile.
        # Der Einsendeschluss ist das spaetere der beiden Daten.
        von, bis = datum_bereich(beschreibung)
        if bis is None:
            von, bis = datum_bereich(titel)

        adresse = _link(eintrag)
        bekannte = quelle.get("retailers", BEKANNTE_HAENDLER)
        marke, aus_anbieter = _sortiere_anbieter(_anbieter_aus_element(eintrag), bekannte)

        aktionen.append(
            Action(
                title=kuerze_titel(titel, quelle.get("titel_entfernen")),
                source=name,
                brand=marke or _marke(titel, trenner),
                type=art_aus_text(volltext),
                max_refund_cents=betrag_in_cent(volltext),
                valid_from=von,
                valid_to=bis,
                submission_deadline=bis,
                url=adresse,
                submit_url=_einreichungslink(adresse, quelle.get("submit_url_aus_link")),
                requirements=anforderungen_aus(volltext),
                retailers=haendler_aus(volltext, bekannte) + aus_anbieter,
                eans=eans_aus(volltext),
                image_url=_bild(eintrag),
            )
        )

    log.info(
        "Quelle %s: %s Einträge im Feed, %s passend, %s aussortiert",
        name,
        len(eintraege),
        len(aktionen),
        aussortiert,
    )
    return aktionen
