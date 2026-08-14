#!/usr/bin/env python3
"""
Klopft eine Liste moeglicher Portale ab und sagt, welche sich lohnen.

Hintergrund: Die beiden Portale aus der urspruenglichen Aufgabenstellung
antworten nicht mehr (siehe DECISIONS.md). Bevor irgendwelche Selektoren
geschrieben werden, muss also erst feststehen, welche Seiten es ueberhaupt
noch gibt und ob sie ihre Aktionen im HTML ausliefern oder erst per JavaScript
nachladen — im zweiten Fall bringt Scrapen nichts.

Ausgegeben wird je Adresse:
  * Erreichbarkeit, Status, Groesse, Content-Type
  * ob robots.txt den Abruf erlaubt
  * bei Feeds: Anzahl der Eintraege und die ersten Titel
  * bei HTML: wiederkehrende Container, ein Beispieleintrag, Klassennamen
  * eine grobe Einschaetzung, wie viel nach Geld-zurueck-Aktion aussieht

Aufruf:
    python scraper/probe_kandidaten.py                  # ganze Liste
    python scraper/probe_kandidaten.py --nur geldzurueck  # nur passende Adressen
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

from bs4 import BeautifulSoup

sys.path.insert(0, str(Path(__file__).resolve().parent))

from gzg_scraper.fetch import Fetcher  # noqa: E402
from inspect_source import zeige_kandidaten  # noqa: E402

LISTE = Path(__file__).resolve().parent / "kandidaten.txt"

# Woran man eine Geld-zurueck-Aktion im Text erkennt. Bewusst grob: Es geht nur
# darum, eine Themenseite von einer Fehlerseite zu unterscheiden.
SIGNALE = (
    "geld zurück",
    "geld-zurück",
    "gratis testen",
    "cashback",
    "kaufpreis erstattet",
    "kaufbeleg",
)

BETRAG = re.compile(r"\d+[.,]?\d*\s*(?:€|EUR)")


def lade_adressen(pfad: Path) -> list[str]:
    zeilen = pfad.read_text(encoding="utf-8").splitlines()
    return [z.strip() for z in zeilen if z.strip() and not z.lstrip().startswith("#")]


def ist_feed(content_type: str, text: str) -> bool:
    if "xml" in content_type:
        return True
    return text.lstrip()[:200].startswith("<?xml") or "<rss" in text[:400].lower()


def zeige_feed(text: str) -> None:
    suppe = BeautifulSoup(text, "xml")
    eintraege = suppe.find_all(["item", "entry"])
    print(f"  Feed mit {len(eintraege)} Einträgen")
    for eintrag in eintraege[:8]:
        titel = eintrag.find("title")
        datum = eintrag.find(["pubDate", "updated", "published"])
        print(
            f"    - {titel.get_text(strip=True)[:80]!r}"
            + (f"  ({datum.get_text(strip=True)})" if datum else "")
        )


def zeige_einschaetzung(text: str) -> None:
    klein = text.lower()
    getroffen = [wort for wort in SIGNALE if wort in klein]
    betraege = BETRAG.findall(text)
    print(f"  Signalwörter: {', '.join(getroffen) if getroffen else 'keine'}")
    print(f"  Geldbeträge im Text: {len(betraege)}" + (f" (z. B. {betraege[:5]})" if betraege else ""))


def main(argv: list[str] | None = None) -> int:
    zerleger = argparse.ArgumentParser(description=__doc__)
    zerleger.add_argument("--liste", type=Path, default=LISTE)
    zerleger.add_argument("--nur", help="nur Adressen, die diesen Text enthalten")
    zerleger.add_argument("--kandidaten", type=int, default=10, help="wie viele Container zeigen")
    argumente = zerleger.parse_args(argv)

    adressen = lade_adressen(argumente.liste)
    if argumente.nur:
        adressen = [a for a in adressen if argumente.nur in a]

    if not adressen:
        print("Keine Adressen zu prüfen.")
        return 1

    # Feeds und HTML kommen mit unterschiedlichem Accept besser durch.
    fetcher = Fetcher(delay=2.0, timeout=25.0)
    fetcher.session.headers["Accept"] = (
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    )

    erreichbar: list[str] = []

    for adresse in adressen:
        print(f"\n{'=' * 78}\n=== {adresse}\n{'=' * 78}")
        try:
            erlaubt = fetcher.darf(adresse)
        except Exception as fehler:  # noqa: BLE001
            print(f"  robots.txt nicht auswertbar: {fehler}")
            erlaubt = True
        print(f"  robots.txt erlaubt: {erlaubt}")

        try:
            fetcher._warte(adresse)  # noqa: SLF001 — bewusst, um die Pause einzuhalten
            antwort = fetcher.session.get(adresse, timeout=fetcher.timeout)
        except Exception as fehler:  # noqa: BLE001
            print(f"  NICHT ERREICHBAR: {type(fehler).__name__}: {str(fehler)[:200]}")
            continue

        content_type = antwort.headers.get("content-type", "")
        print(
            f"  HTTP {antwort.status_code}, {len(antwort.content)} Bytes, "
            f"Content-Type {content_type}, Endadresse {antwort.url}"
        )
        if antwort.status_code != 200:
            continue

        if antwort.encoding is None or antwort.encoding.lower() == "iso-8859-1":
            antwort.encoding = antwort.apparent_encoding
        text = antwort.text
        erreichbar.append(adresse)

        if ist_feed(content_type, text):
            zeige_feed(text)
            continue

        suppe = BeautifulSoup(text, "lxml")
        titel = suppe.find("title")
        print(f"  <title>: {titel.get_text(strip=True)[:100]!r}" if titel else "  kein <title>")
        zeige_einschaetzung(text)
        zeige_kandidaten(suppe, anzahl=argumente.kandidaten)

    print(f"\n\n### Erreichbar ({len(erreichbar)} von {len(adressen)}):")
    for adresse in erreichbar:
        print(f"  {adresse}")
    return 0 if erreichbar else 1


if __name__ == "__main__":
    raise SystemExit(main())
