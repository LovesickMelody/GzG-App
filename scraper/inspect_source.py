#!/usr/bin/env python3
"""
Zeigt, wie eine Portalseite aufgebaut ist — die Vorstufe zum Selektor-Schreiben.

Gedacht fuer den Fall "Quelle liefert nichts mehr": Statt im Browser zu suchen,
laesst man dieses Skript laufen (lokal oder ueber den Workflow `scrape.yml` mit
dem Eingabefeld `inspect`) und liest die Kandidaten direkt aus der Ausgabe ab.

Ausgegeben werden:
  1. die wiederkehrenden Container (gleiche Klassenkombination, mehrfach da) —
     daraus wird `item`
  2. je Kandidat ein Beispiel mit Ueberschriften, Links, Bildern und Klassen —
     daraus werden `title`, `link`, `image`
  3. was die aktuell konfigurierten Selektoren tatsaechlich treffen

Beispiel:
    python scraper/inspect_source.py --source gratis-testen
    python scraper/inspect_source.py --url https://example.org/ --datei seite.html
"""

from __future__ import annotations

import argparse
import sys
from collections import Counter
from pathlib import Path

from bs4 import BeautifulSoup

sys.path.insert(0, str(Path(__file__).resolve().parent))

from gzg_scraper.fetch import Fetcher  # noqa: E402
from gzg_scraper.registry import hole as hole_parser  # noqa: E402
from gzg_scraper.run import lade_quellen  # noqa: E402

QUELLEN = Path(__file__).resolve().parent / "sources.yaml"


def signatur(knoten) -> str:
    klassen = knoten.get("class") or []
    return f"{knoten.name}.{'.'.join(sorted(klassen))}" if klassen else knoten.name


def zeige_kandidaten(suppe: BeautifulSoup, anzahl: int = 12) -> None:
    """Sucht Container, die mehrfach vorkommen und Text enthalten — typische Listeneintraege."""
    zaehler: Counter[str] = Counter()
    beispiele: dict[str, object] = {}

    for knoten in suppe.find_all(["article", "div", "li", "section"]):
        text = knoten.get_text(" ", strip=True)
        if not (40 <= len(text) <= 600):
            continue
        # Nur Container, die selbst wie ein Eintrag aussehen: eine Ueberschrift
        # oder ein Link mit Text drin.
        if not (knoten.find(["h1", "h2", "h3", "h4"]) or knoten.find("a")):
            continue
        schluessel = signatur(knoten)
        zaehler[schluessel] += 1
        beispiele.setdefault(schluessel, knoten)

    mehrfach = [(s, n) for s, n in zaehler.most_common(60) if n >= 2]
    if not mehrfach:
        print("  (keine wiederkehrenden Container gefunden — Seite evtl. per JavaScript aufgebaut)")
        return

    print(f"\n  Wiederkehrende Container (Kandidaten für `item`), Top {anzahl}:")
    for schluessel, n in mehrfach[:anzahl]:
        print(f"    {n:>3}×  {schluessel}")

    bester, _ = mehrfach[0]
    knoten = beispiele[bester]
    print(f"\n  Beispiel für {bester}:")
    for tag in ("h1", "h2", "h3", "h4"):
        for treffer in knoten.find_all(tag)[:2]:  # type: ignore[union-attr]
            print(f"    {tag}: {signatur(treffer)} -> {treffer.get_text(' ', strip=True)[:80]!r}")
    for treffer in knoten.find_all("a")[:3]:  # type: ignore[union-attr]
        print(f"    a:  {signatur(treffer)} -> href={treffer.get('href')!r}")
    for treffer in knoten.find_all("img")[:2]:  # type: ignore[union-attr]
        print(f"    img: {signatur(treffer)} -> src={treffer.get('src')!r}")
    innere = {
        signatur(kind)
        for kind in knoten.find_all(["span", "div", "p", "time"])  # type: ignore[union-attr]
        if kind.get("class")
    }
    if innere:
        print("    Klassen im Eintrag (Kandidaten für brand/max_refund/deadline):")
        for eintrag in sorted(innere)[:20]:
            print(f"      {eintrag}")


def pruefe_selektoren(html: str, quelle: dict) -> None:
    suppe = BeautifulSoup(html, "lxml")
    selektoren = quelle.get("selectors", {})
    print("\n  Was die konfigurierten Selektoren treffen:")
    for feld, selektor in selektoren.items():
        rein = selektor.split("@")[0].strip()
        try:
            treffer = suppe.select(rein) if rein else []
        except Exception as fehler:  # noqa: BLE001
            print(f"    {feld:<14} {selektor!r}: ungültiger Selektor ({fehler})")
            continue
        hinweis = "" if treffer else "   <-- trifft nichts"
        print(f"    {feld:<14} {selektor!r}: {len(treffer)} Treffer{hinweis}")

    parser = hole_parser(quelle.get("parser", "css_listing"))
    if parser:
        aktionen = parser(html, quelle)
        print(f"\n  Parser-Ergebnis: {len(aktionen)} Aktionen")
        for aktion in aktionen[:5]:
            print(
                f"    - {aktion.title[:60]!r} | Marke={aktion.brand!r} | "
                f"Betrag={aktion.max_refund_cents} | Frist={aktion.submission_deadline}"
            )


def main(argv: list[str] | None = None) -> int:
    zerleger = argparse.ArgumentParser(description=__doc__)
    zerleger.add_argument("--source", help="Name einer Quelle aus sources.yaml")
    zerleger.add_argument("--url", help="einzelne Adresse statt einer Quelle")
    zerleger.add_argument("--datei", type=Path, help="lokale HTML-Datei statt Abruf")
    zerleger.add_argument("--speichern", type=Path, help="HTML als Fixture ablegen")
    argumente = zerleger.parse_args(argv)

    if argumente.datei:
        seiten = [(str(argumente.datei), argumente.datei.read_text(encoding="utf-8"))]
        quelle = {"name": "datei", "selectors": {}, "parser": "css_listing"}
        if argumente.source:
            quelle = next(
                (q for q in lade_quellen(QUELLEN) if q["name"] == argumente.source), quelle
            )
    else:
        fetcher = Fetcher(delay=2.0)
        if argumente.url:
            quelle = {"name": "adhoc", "base_url": argumente.url, "selectors": {}}
            adressen = [argumente.url]
        elif argumente.source:
            quellen = lade_quellen(QUELLEN)
            quelle = next((q for q in quellen if q["name"] == argumente.source), None)
            if quelle is None:
                print(f"Quelle {argumente.source!r} steht nicht in sources.yaml")
                return 1
            adressen = quelle.get("listing_urls", [])
        else:
            zerleger.error("--source, --url oder --datei angeben")
            return 2

        seiten = []
        for adresse in adressen:
            erlaubt = fetcher.darf(adresse)
            print(f"robots.txt erlaubt {adresse}: {erlaubt}")
            html = fetcher.hole(adresse)
            if html is None:
                print(f"  Abruf fehlgeschlagen: {adresse}")
                continue
            seiten.append((adresse, html))

    if not seiten:
        print("Nichts geladen.")
        return 1

    for adresse, html in seiten:
        print(f"\n=== {adresse} ({len(html)} Zeichen) ===")
        if argumente.speichern:
            argumente.speichern.parent.mkdir(parents=True, exist_ok=True)
            argumente.speichern.write_text(html, encoding="utf-8")
            print(f"  gespeichert unter {argumente.speichern}")
        suppe = BeautifulSoup(html, "lxml")
        titel = suppe.find("title")
        print(f"  <title>: {titel.get_text(strip=True)[:90]!r}" if titel else "  kein <title>")
        zeige_kandidaten(suppe)
        pruefe_selektoren(html, quelle)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
