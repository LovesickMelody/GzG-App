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
# Ohne Waehrungszeichen ist die Erkennung heikel. Drei Faelle aus den echten
# Portalseiten, die alle wie ein Betrag aussehen und keiner sind:
#   "30.08.2026"          -> waere 30,08 €   (Datum)
#   "1.450 Einlösungen"   -> waere 1,45 €    (laengere Zahl)
#   "medium+ lemon 0,75l" -> waere 0,75 €    (Fuellmenge)
# Deshalb: links und rechts keine weitere Ziffer, und rechts keine Einheit.
_EINHEITEN = r"l|ml|cl|dl|g|mg|kg|m|mm|cm|km|St|Stk|Stück|kWh|x|%"
_BETRAG_OHNE_WAEHRUNG = re.compile(
    r"(?<![\d.,])(\d{1,3}(?:[.\s]\d{3})*|\d+)[,.](\d{2})"
    rf"(?![\d.,])(?!\s*(?:{_EINHEITEN})\b)",
    re.I,
)
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


def datum_bereich(text: str | None) -> tuple[str | None, str | None]:
    """
    Liest einen Aktionszeitraum wie "17.08.2026-30.09.2026" als (von, bis).

    Portale schreiben den Zeitraum meist in einer Zeile statt in zwei Feldern.
    Steht nur ein Datum da, ist es das Ende — bei einer Aktion interessiert der
    Einsendeschluss, nicht der Beginn.
    """
    if not text:
        return None, None

    gefunden: list[str] = []
    for muster in (_DATUM_ISO, _DATUM_PUNKT, _DATUM_WORT):
        for treffer in muster.finditer(text):
            iso = datum_iso(treffer.group(0))
            if iso and iso not in gefunden:
                gefunden.append(iso)

    if not gefunden:
        return None, None
    if len(gefunden) == 1:
        return None, gefunden[0]

    gefunden.sort()
    return gefunden[0], gefunden[-1]


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


# Formulierungen, die "du bekommst den vollen Kaufpreis zurueck" bedeuten.
# "100 % Cashback" gehoert ausdruecklich dazu: Das Wort Cashback allein sagt
# noch nicht, ob voll oder anteilig erstattet wird.
_VOLLE_ERSTATTUNG = (
    "gratis", "kostenlos", "umsonst",
    "100 %", "100%", "100 prozent",
    "voller kaufpreis", "vollen kaufpreis", "kompletten kaufpreis",
    "kaufpreis erstattet", "kaufpreis zurück", "kaufpreis zurueck",
    "geld komplett zurück", "komplett erstattet",
)

_TEILERSTATTUNG = (
    "teilbetrag", "teil-cashback", "anteilig", "rabatt von",
    "50 %", "50%", "25 %", "25%",
)


def art_aus_text(text: str | None, max_refund_cents: int | None = None) -> str:
    """
    Rät die Art der Aktion aus der Beschreibung.

    "Gratis testen" heisst voller Kaufpreis zurueck, ein genannter Teilbetrag
    heisst nur anteilig. Entscheidend ist die Reihenfolge: Eine Formulierung
    fuer volle Erstattung schlaegt jeden Cashback-Hinweis, denn "100 % Cashback"
    ist gratis testen — das Wort Cashback allein sagt nichts ueber die Hoehe.

    Im Zweifel gilt gratis_testen, weil das bei diesen Portalen der Regelfall
    ist. Wer nur volle Erstattungen sehen will, filtert ueber ``nur_arten`` in
    ``sources.yaml``; ein zu Unrecht aussortierter Volltreffer waere aergerlicher
    als ein durchgerutschter Teilbetrag.
    """
    inhalt = (text or "").casefold()

    if any(wort in inhalt for wort in _VOLLE_ERSTATTUNG):
        return "gratis_testen"

    if any(wort in inhalt for wort in _TEILERSTATTUNG):
        return "cashback_teilbetrag"

    if "cashback" in inhalt:
        return "cashback_teilbetrag"

    return "gratis_testen"


# Was man braucht, um bei einer Aktion mitzumachen. Reihenfolge = Reihenfolge
# der Checkliste in der App, deshalb bewusst als Liste und nicht als Menge.
#
# Je Eintrag: (Schluessel, Woerter, die ihn ausloesen). Erkannt wird grob und
# konservativ — ein fehlender Haken ist aergerlich, ein erfundener schickt
# jemanden mit dem falschen Foto los.
_ANFORDERUNGEN: list[tuple[str, tuple[str, ...]]] = [
    (
        "produktfoto",
        (
            "produkt fotografieren", "produkte fotografieren", "produktfoto",
            "foto des produkts", "foto vom produkt", "bild des produkts",
            "artikel fotografieren", "produkt abfotografieren",
            "verpackung fotografieren", "produktverpackung",
        ),
    ),
    (
        "bonfoto",
        (
            "kassenbon", "kaufbeleg", "kassenzettel", "kassenbeleg",
            "beleg hochladen", "bon hochladen", "bon fotografieren",
            "beleg fotografieren", "originalbon", "original-kassenbon",
            "foto des belegs", "foto vom beleg",
        ),
    ),
    (
        "zusammen_fotografieren",
        (
            "zusammen mit dem kassenbon", "zusammen fotografieren",
            "alles zusammen", "gemeinsam fotografieren", "zusammen mit dem beleg",
            "produkt und kassenbon", "produkt mit kassenbon",
            "produkt und bon", "auf einem foto", "auf einem bild",
            "gemeinsam auf einem",
        ),
    ),
    (
        "strichcode",
        (
            "strichcode", "barcode", "ean ausschneiden", "ean-code ausschneiden",
            "ean-code", "strichcode ausschneiden",
        ),
    ),
    (
        "verpackung_aufbewahren",
        ("verpackung aufbewahren", "verpackung aufheben", "verpackung einsenden"),
    ),
    (
        "handy_verifizierung",
        (
            # Viele Aktionen schicken einen Code aufs Handy, bevor ueberhaupt
            # etwas eingereicht werden kann. Wer das nicht weiss, steht mit dem
            # Bon da und kommt nicht weiter.
            "handynummer", "mobilfunknummer", "mobilnummer",
            "per sms", "sms-code", "sms zugesendet", "sms mit",
            "verifizierungscode", "bestätigungscode", "bestaetigungscode",
            "telefonnummer bestätigen", "nummer verifizieren",
        ),
    ),
    (
        "app",
        ("in der app", "über die app", "app hochladen", "scondoo", "marktguru"),
    ),
    (
        "registrierung",
        ("registrieren", "registrierung", "konto anlegen", "benutzerkonto"),
    ),
    (
        "iban",
        ("iban", "kontodaten", "bankverbindung", "bankdaten", "kontoinhaber"),
    ),
]


def anforderungen_aus(text: str | None) -> list[str]:
    """
    Liest aus der Beschreibung, was man zum Mitmachen braucht.

    Ergebnis sind Schluessel wie ``produktfoto`` oder ``bonfoto``, aus denen die
    App die Checkliste "Was brauche ich?" baut. Die Reihenfolge ist fest, damit
    die Liste in der App nicht bei jedem Lauf springt.

    Bewusst ohne Raten: Steht nichts Erkennbares im Text, kommt eine leere Liste
    zurueck und die App sagt ehrlich, dass die Bedingungen auf der Aktionsseite
    stehen. Ein erfundener Haken waere schlimmer als kein Haken — danach steht
    man mit dem falschen Foto da und die Erstattung faellt aus.
    """
    if not text:
        return []

    inhalt = text.casefold()
    gefunden = [
        schluessel
        for schluessel, woerter in _ANFORDERUNGEN
        if any(wort in inhalt for wort in woerter)
    ]

    # "Zusammen fotografieren" heisst zwangslaeufig: beides wird gebraucht.
    # Portale schreiben das oft nur einmal hin, statt beide Fotos aufzuzaehlen.
    if "zusammen_fotografieren" in gefunden:
        for noetig in ("produktfoto", "bonfoto"):
            if noetig not in gefunden:
                gefunden.append(noetig)
        gefunden.sort(key=lambda s: [k for k, _ in _ANFORDERUNGEN].index(s))

    return gefunden


def kuerze_titel(titel: str, muster: str | None) -> str:
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


# --- Kontingent ------------------------------------------------------------
# Viele Aktionen sind gedeckelt: "1.000 Teilnahmen pro Woche", und montags um
# 9 Uhr faengt es von vorn an. Wer das nicht weiss, kauft das Produkt und stellt
# beim Einreichen fest, dass er zu spaet dran war.
#
# Gelesen wird vorsichtig. Eine falsche Zahl waere schlimmer als gar keine: Sie
# klaenge nach einer verlaesslichen Auskunft. Der erste Anlauf war zu grosszuegig
# und machte aus dem Teilnehmerzaehler einer Seite ("schon 30.652 Teilnahmen!")
# eine Obergrenze.

_ZAHL = r"(\d{1,3}(?:[. ]\d{3})+|\d+)"

_EINHEITEN = (
    "teilnahmen", "teilnehmer", "einlösungen", "einloesungen", "einreichungen",
    "erstattungen", "cashbacks", "cashback", "codes", "gutscheine", "pakete",
)

_LIMIT = re.compile(
    rf"{_ZAHL}\s*(?:x\s*)?(?:{'|'.join(_EINHEITEN)})",
    re.IGNORECASE,
)

# Ohne eines dieser Woerter in der Naehe ist eine Zahl keine Obergrenze, sondern
# irgendeine Zahl auf einer Werbeseite.
_LIMITWOERTER = (
    "begrenzt", "beschränkt", "beschraenkt", "limitiert", "maximal", "maximale",
    "kontingent", "insgesamt", "zur verfügung", "zur verfuegung", "stehen bereit",
    "vorrat", "erste", "ersten", "je woche", "pro woche", "je tag", "pro tag",
    "pro monat", "je monat", "täglich", "taeglich", "wöchentlich", "woechentlich",
)

_ZEITRAEUME: list[tuple[str, tuple[str, ...]]] = [
    ("tag", ("pro tag", "je tag", "täglich", "taeglich", "am tag", "pro kalendertag")),
    ("woche", ("pro woche", "je woche", "wöchentlich", "woechentlich", "pro kalenderwoche",
               "je kalenderwoche", "in der woche")),
    ("monat", ("pro monat", "je monat", "monatlich", "im monat")),
]

_WOCHENTAGE = (
    "montag", "dienstag", "mittwoch", "donnerstag", "freitag", "samstag", "sonntag",
)

_RESETWOERTER = (
    "zurückgesetzt", "zurueckgesetzt", "zurückgestellt", "neues kontingent",
    "neu freigeschaltet", "wieder verfügbar", "wieder verfuegbar", "neue teilnahmen",
    "startet neu", "beginnt neu", "erneut teilnehmen", "aufgefüllt", "aufgefuellt",
)

# Nur klare Aussagen ueber den *jetzigen* Zustand. Das blosse Wort "erschoepft"
# reicht nicht — es steht auch in "sobald das Kontingent erschoepft ist", und das
# bedeutet das Gegenteil.
_ERSCHOEPFT = re.compile(
    r"(?:(?:ist|sind|wurde|wurden)\s+(?:\w+\s+){0,3}"
    r"(?:erschöpft|erschoepft|ausgeschöpft|ausgeschoepft|vergriffen)"
    r"|leider\s+vergriffen|bereits\s+vergriffen"
    r"|teilnahmelimit\s+erreicht|maximale\s+teilnehmerzahl\s+erreicht"
    r"|alle\s+codes\s+vergeben)",
    re.IGNORECASE,
)

# Woerter, die aus einer Aussage eine Bedingung machen.
_BEDINGT = (
    "sobald", "wenn", "falls", "sollte", "solange", "bis das", "kann es",
    "sofern", "im falle", "andernfalls",
)

_ZEITANGABE = re.compile(r"(?:um|ab)\s*(\d{1,2})(?::(\d{2}))?\s*uhr", re.IGNORECASE)


def kontingent_aus(text: str | None) -> dict:
    """
    Liest aus den Teilnahmebedingungen, wie stark eine Aktion gedeckelt ist.

    Zurueck kommt immer ein Dictionary; fehlt eine Angabe, steht dort ``None``.

    - ``anzahl``: die genannte Obergrenze, etwa 1000
    - ``zeitraum``: "tag", "woche", "monat" oder ``None`` fuer "insgesamt"
    - ``zuruecksetzung``: wann es von vorn losgeht, als lesbarer Text
    - ``erschoepft``: True, wenn die Seite sagt, dass gerade nichts mehr geht
    """
    leer = {"anzahl": None, "zeitraum": None, "zuruecksetzung": None, "erschoepft": False}
    if not text:
        return leer

    inhalt = re.sub(r"\s+", " ", text)
    klein = inhalt.casefold()

    ergebnis = dict(leer)
    ergebnis["erschoepft"] = _erschoepft_aus(inhalt)
    ergebnis["zuruecksetzung"] = _zuruecksetzung_aus(inhalt)

    for treffer in _LIMIT.finditer(inhalt):
        anzahl = int(re.sub(r"[. ]", "", treffer.group(1)))
        # Unter zehn ist keine Kontingentangabe, sondern meist "2 Teilnahmen je
        # Haushalt" — eine andere Aussage, die hier nur verwirren wuerde.
        if not 10 <= anzahl <= 10_000_000:
            continue

        # Das Wort, das die Zahl zur Obergrenze macht, steht im selben Satzteil.
        fenster = klein[max(0, treffer.start() - 90):treffer.end() + 60]
        if not any(wort in fenster for wort in _LIMITWOERTER):
            continue

        ergebnis["anzahl"] = anzahl
        for schluessel, woerter in _ZEITRAEUME:
            if any(wort in fenster for wort in woerter):
                ergebnis["zeitraum"] = schluessel
                break
        break

    return ergebnis


def _erschoepft_aus(inhalt: str) -> bool:
    """
    True, wenn die Seite sagt, dass gerade nichts mehr geht.

    Geprueft wird nur der unmittelbare Zusammenhang. Ueber einen ganzen
    Seitentext zu suchen ginge schief: Der hat keine verlaesslichen Satzgrenzen,
    und irgendwo steht immer ein "sobald".
    """
    for treffer in _ERSCHOEPFT.finditer(inhalt):
        davor = inhalt[max(0, treffer.start() - 70):treffer.start()].casefold()
        if any(wort in davor for wort in _BEDINGT):
            continue
        return True
    return False


def _zuruecksetzung_aus(inhalt: str) -> str | None:
    """
    Sucht den Satz, der sagt, wann das Kontingent von vorn beginnt.

    Nur Saetze, in denen auch wirklich vom Zuruecksetzen die Rede ist: "Montag
    9 Uhr" allein waere sonst genauso gut eine Oeffnungszeit.
    """
    for satz in re.split(r"(?<=[.!?])\s+", inhalt):
        klein = satz.casefold()
        if not any(wort in klein for wort in _RESETWOERTER):
            continue

        zeit = _ZEITANGABE.search(satz)
        uhrzeit = None
        if zeit:
            stunde = int(zeit.group(1))
            minute = int(zeit.group(2) or 0)
            if 0 <= stunde <= 23 and 0 <= minute <= 59:
                uhrzeit = f"{stunde:02d}:{minute:02d} Uhr"

        tag = next((t for t in _WOCHENTAGE if t in klein), None)
        if tag:
            benennung = tag.capitalize() + "s"
            return f"{benennung} um {uhrzeit}" if uhrzeit else benennung
        if any(wort in klein for wort in ("täglich", "taeglich", "jeden tag")):
            return f"Täglich um {uhrzeit}" if uhrzeit else "Täglich"
        if any(wort in klein for wort in ("monatlich", "jeden monat", "zum monatsanfang")):
            return "Monatlich"

    return None
