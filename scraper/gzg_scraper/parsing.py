"""Textbausteine, die jeder Portal-Parser braucht: Betraege, Daten, EANs."""

from __future__ import annotations

import re
from datetime import date, datetime

MONATE = {
    "januar": 1, "jan": 1,
    "februar": 2, "feb": 2,
    "maerz": 3, "märz": 3, "mrz": 3, "mar": 3,
    "april": 4, "apr": 4,
    "mai": 5,
    "juni": 6, "jun": 6,
    "juli": 7, "jul": 7,
    "august": 8, "aug": 8,
    "september": 9, "sep": 9, "sept": 9,
    "oktober": 10, "okt": 10,
    "november": 11, "nov": 11,
    "dezember": 12, "dez": 12,
}

_BETRAG = re.compile(r"(\d{1,3}(?:[.\s]\d{3})*|\d+)(?:[,.](\d{1,2}))?\s*(?:€|EUR|Euro)", re.I)
_BETRAG_OHNE_WAEHRUNG = re.compile(r"(\d{1,3}(?:[.\s]\d{3})*|\d+)[,.](\d{2})\b")
_EAN = re.compile(r"\b(\d{13}|\d{8})\b")

_DATUM_PUNKT = re.compile(r"\b(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{2,4})\b")
_DATUM_ISO = re.compile(r"\b(\d{4})-(\d{2})-(\d{2})\b")
_DATUM_WORT = re.compile(r"\b(\d{1,2})\.?\s+([A-Za-zÄÖÜäöü]+)\s+(\d{4})\b")


def betrag_in_cent(text: str | None) -> int | None:
    """
    Liest den ersten Geldbetrag aus einem Text als Cent.

    Erkennt "3,99 €", "bis zu 4,99 EUR", "10 Euro" und "1.234,50 €".
    Gibt ``None`` zurueck, wenn kein Betrag drinsteht — der Aufrufer entscheidet,
    ob das ein Fehler ist.
    """
    if not text:
        return None

    treffer = _BETRAG.search(text) or _BETRAG_OHNE_WAEHRUNG.search(text)
    if not treffer:
        return None

    ganz = re.sub(r"[.\s]", "", treffer.group(1))
    nachkomma = treffer.group(2) or "0"
    if len(nachkomma) == 1:
        nachkomma += "0"

    try:
        return int(ganz) * 100 + int(nachkomma)
    except ValueError:
        return None


def datum_iso(text: str | None, heute: date | None = None) -> str | None:
    """
    Liest ein Datum als ISO-String (YYYY-MM-DD).

    Erkennt "31.12.2026", "31.12.26", "2026-12-31" und "31. Dezember 2026".
    Zweistellige Jahre werden ins aktuelle Jahrhundert gelegt.
    """
    if not text:
        return None

    treffer = _DATUM_ISO.search(text)
    if treffer:
        return _bauen(int(treffer.group(1)), int(treffer.group(2)), int(treffer.group(3)))

    treffer = _DATUM_PUNKT.search(text)
    if treffer:
        jahr = int(treffer.group(3))
        if jahr < 100:
            jahr += 2000
        return _bauen(jahr, int(treffer.group(2)), int(treffer.group(1)))

    treffer = _DATUM_WORT.search(text)
    if treffer:
        monat = MONATE.get(treffer.group(2).casefold())
        if monat:
            return _bauen(int(treffer.group(3)), monat, int(treffer.group(1)))

    return None


def _bauen(jahr: int, monat: int, tag: int) -> str | None:
    try:
        return datetime(jahr, monat, tag).date().isoformat()
    except ValueError:
        # Etwa der 31. Februar — lieber kein Datum als ein falsches.
        return None


def eans_aus(text: str | None) -> list[str]:
    """Sammelt alle EAN-13 und EAN-8 aus einem Text, ohne Dubletten."""
    if not text:
        return []
    gefunden: list[str] = []
    for code in _EAN.findall(text):
        if code not in gefunden and pruefziffer_stimmt(code):
            gefunden.append(code)
    return gefunden


def pruefziffer_stimmt(code: str) -> bool:
    """
    Prueft die EAN-Pruefziffer.

    Ohne diese Pruefung landen Artikelnummern, Telefonnummern und
    Postleitzahl-Kombinationen aus dem Fliesstext als EAN in der App — und der
    Barcode-Scan trifft dann die falsche Aktion.
    """
    if len(code) not in (8, 13) or not code.isdigit():
        return False

    ziffern = [int(z) for z in code]
    pruefziffer = ziffern.pop()

    # Von rechts gelesen wechseln sich die Gewichte 3 und 1 ab.
    summe = 0
    for index, ziffer in enumerate(reversed(ziffern)):
        summe += ziffer * (3 if index % 2 == 0 else 1)

    return (10 - summe % 10) % 10 == pruefziffer


def art_aus_text(text: str | None, max_refund_cents: int | None = None) -> str:
    """
    Rät die Art der Aktion aus der Beschreibung.

    "Gratis testen" heisst voller Kaufpreis zurueck, "Cashback" oder ein
    genannter Teilbetrag heisst nur anteilig. Im Zweifel gilt gratis_testen,
    weil das bei diesen Portalen der Regelfall ist.
    """
    inhalt = (text or "").casefold()

    if any(
        wort in inhalt
        for wort in ("teilbetrag", "teil-cashback", "anteilig", "rabatt von")
    ):
        return "cashback_teilbetrag"

    if "cashback" in inhalt and "gratis" not in inhalt:
        return "cashback_teilbetrag"

    return "gratis_testen"


def saeubere(text: str | None) -> str | None:
    """Schrumpft Leerraum und macht aus einem leeren Rest ``None``."""
    if text is None:
        return None
    gesaeubert = re.sub(r"\s+", " ", text).strip()
    return gesaeubert or None


def haendler_aus(text: str | None, bekannte: list[str]) -> list[str]:
    """
    Findet bekannte Haendlernamen im Text.

    Bewusst gegen eine feste Liste statt frei geraten: Ein aus dem Fliesstext
    geratener "Markt" oder "Filiale" waere als Filter in der App wertlos.
    """
    if not text:
        return []
    inhalt = text.casefold()
    return [name for name in bekannte if name.casefold() in inhalt]
