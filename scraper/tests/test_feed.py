"""Tests des Feed-Parsers gegen eine gespeicherte XML-Fixture — kein Netz noetig."""

from __future__ import annotations

from pathlib import Path

import pytest

from gzg_scraper.parsers.feed import parse

FIXTURES = Path(__file__).parent / "fixtures"

QUELLE = {
    "name": "testfeed",
    "parser": "feed",
    "keywords": ["geld zurück", "gratis testen", "cashback", "kaufpreis erstattet"],
    "ausschluss": ["gewinnspiel", "verlosung"],
    "brand_trenner": ":",
}


@pytest.fixture
def aktionen():
    xml = (FIXTURES / "feed_typisch.xml").read_text(encoding="utf-8")
    return parse(xml, QUELLE)


class TestTypischerFeed:
    def test_nimmt_nur_die_passenden_eintraege(self, aktionen):
        # Fuenf Eintraege: drei Aktionen, ein Gewinnspiel, einer ohne Titel.
        assert len(aktionen) == 3

    def test_sortiert_das_gewinnspiel_aus(self, aktionen):
        assert all("Gewinnspiel" not in a.title for a in aktionen)

    def test_liest_titel_und_link(self, aktionen):
        erste = aktionen[0]
        assert erste.title == "Valess: 100 % Geld zurück beim Kauf"
        assert erste.url == "https://www.beispiel.de/aktion/valess"

    def test_trennt_die_marke_am_doppelpunkt(self, aktionen):
        assert aktionen[0].brand == "Valess"

    def test_laesst_die_marke_leer_ohne_trenner(self, aktionen):
        assert aktionen[1].brand is None

    def test_liest_den_betrag_aus_der_eingepackten_beschreibung(self, aktionen):
        # Der Betrag steht in HTML-Tags verpackt; ohne Auspacken faende man ihn nicht.
        assert aktionen[0].max_refund_cents == 399

    def test_liest_die_frist_in_beiden_schreibweisen(self, aktionen):
        assert aktionen[0].submission_deadline == "2026-09-30"
        assert aktionen[1].submission_deadline == "2026-12-15"

    def test_liest_haendler_und_ean(self, aktionen):
        assert aktionen[0].retailers == ["Edeka", "Rewe"]
        assert aktionen[0].eans == ["4005900123459"]
        assert aktionen[1].retailers == ["dm", "Rossmann"]

    def test_liest_das_bild_aus_dem_media_element(self, aktionen):
        assert aktionen[0].image_url == "https://www.beispiel.de/bilder/valess.jpg"
        assert aktionen[1].image_url is None

    def test_erkennt_teilbetraege_als_cashback(self, aktionen):
        assert aktionen[0].type == "gratis_testen"
        assert aktionen[2].type == "cashback_teilbetrag"

    def test_ohne_betrag_bleibt_das_feld_leer(self, aktionen):
        assert aktionen[1].max_refund_cents is None


class TestSonderfaelle:
    def test_leerer_feed_ergibt_leere_liste(self):
        assert parse("<rss><channel></channel></rss>", QUELLE) == []

    def test_ohne_schluesselwoerter_zaehlt_jeder_eintrag(self):
        xml = (FIXTURES / "feed_typisch.xml").read_text(encoding="utf-8")
        # Ohne Filter bleiben alle Eintraege mit Titel uebrig — auch das Gewinnspiel.
        assert len(parse(xml, {"name": "roh", "parser": "feed"})) == 4


class TestMydealzFeed:
    """
    Gegen den echten Aufbau von mydealz: Marke als eigenes Element, Zeitraum in
    einer Zeile, Kennzeichnung vorn im Titel.
    """

    QUELLE = {
        "name": "mydealz",
        "parser": "feed",
        "titel_entfernen": r"^(?:\s*(?:\[[^\]]*\]|\([^)]*\)|GZG\s*[-–]))+\s*",
        "ausschluss": ["gewinnspiel", "verlosung"],
    }

    @pytest.fixture
    def aktionen(self):
        xml = (FIXTURES / "feed_mydealz.xml").read_text(encoding="utf-8")
        return parse(xml, self.QUELLE)

    def test_sortiert_das_gewinnspiel_aus(self, aktionen):
        assert len(aktionen) == 3

    def test_liest_die_marke_aus_dem_eigenen_element(self, aktionen):
        # Verlaesslicher als jedes Raten am Titel.
        assert aktionen[0].brand == "JACOBS Kaffee"
        assert aktionen[1].brand == "Coca-Cola"

    def test_schneidet_die_kennzeichnung_vom_titel(self, aktionen):
        assert aktionen[0].title == "Jacobs 3in1/2in1 gratis testen ab dem 17.08-26 - 30.09.26"
        assert aktionen[1].title == "100 % zurück auf Pantene Pro-V"

    def test_liest_den_zeitraum_aus_einer_zeile(self, aktionen):
        assert aktionen[0].valid_from == "2026-08-17"
        assert aktionen[0].valid_to == "2026-09-30"
        # Der Einsendeschluss ist das spätere der beiden Daten.
        assert aktionen[0].submission_deadline == "2026-09-30"

    def test_baut_die_checkliste_aus_der_beschreibung(self, aktionen):
        # "zusammen fotografieren" heisst zwangslaeufig: beide Fotos noetig.
        assert aktionen[0].requirements == [
            "produktfoto",
            "bonfoto",
            "zusammen_fotografieren",
            "app",
        ]
        assert aktionen[1].requirements == ["bonfoto", "iban"]

    def test_erkennt_100_prozent_als_gratis_testen(self, aktionen):
        assert aktionen[1].type == "gratis_testen"

    def test_erkennt_den_teilbetrag(self, aktionen):
        assert aktionen[2].type == "cashback_teilbetrag"

    def test_ohne_erkennbare_bedingungen_bleibt_die_liste_leer(self, aktionen):
        # Lieber ehrlich leer als ein erfundener Haken — sonst steht man mit
        # dem falschen Foto da.
        assert aktionen[2].requirements == []


class TestAnbieterfeld:
    """
    Was mydealz als ``pepper:merchant`` liefert, ist mal die Marke und mal der
    Händler oder die Plattform — der Einsteller entscheidet das.
    """

    def _parse(self, anbieter: str):
        xml = f"""<?xml version="1.0"?>
        <rss xmlns:pepper="https://about.pepper.com/rss"><channel><item>
          <pepper:merchant name="{anbieter}"/>
          <title>Produkt gratis testen</title>
          <description>Kaufpreis erstattet.</description>
          <link>https://example.org/a</link>
        </item></channel></rss>"""
        return parse(xml, {"name": "mydealz", "parser": "feed"})[0]

    def test_echte_marke_bleibt_marke(self):
        aktion = self._parse("JACOBS Kaffee")
        assert aktion.brand == "JACOBS Kaffee"
        assert aktion.retailers == []

    def test_haendler_wandert_ins_haendlerfeld(self):
        aktion = self._parse("ROSSMANN")
        assert aktion.brand is None
        # Kanonische Schreibweise, damit der Filter in der App zusammenfindet.
        assert aktion.retailers == ["Rossmann"]

    def test_einreichplattform_ist_weder_noch(self):
        # Sonst hiesse die Hälfte aller Aktionen "scondoo".
        aktion = self._parse("scondoo")
        assert aktion.brand is None
        assert aktion.retailers == []


class TestEinreichungslinkAusAdresse:
    """
    mydealz haelt keinen Anbieterlink im Feed, hat aber eine eigene
    Weiterleitung: /visit/threadmain/<id> landet direkt auf der Aktionsseite.
    """

    QUELLE = {
        "name": "mydealz",
        "parser": "feed",
        "submit_url_aus_link": {
            "muster": r"-(\d+)/?$",
            "vorlage": "https://www.mydealz.de/visit/threadmain/{}",
        },
    }

    def _parse(self, link: str, quelle: dict | None = None):
        xml = f"""<?xml version="1.0"?>
        <rss><channel><item>
          <title>Produkt gratis testen</title>
          <description>Kaufpreis erstattet.</description>
          <link>{link}</link>
        </item></channel></rss>"""
        return parse(xml, quelle or self.QUELLE)[0]

    def test_baut_die_weiterleitung_aus_der_kennnummer(self):
        aktion = self._parse("https://www.mydealz.de/deals/jacobs-gratis-testen-2823305")
        assert aktion.submit_url == "https://www.mydealz.de/visit/threadmain/2823305"
        # Die Portaladresse bleibt daneben stehen — dorthin gehören Kommentare
        # und Rückfragen der Gemeinschaft.
        assert aktion.url == "https://www.mydealz.de/deals/jacobs-gratis-testen-2823305"

    def test_ohne_kennnummer_bleibt_es_leer(self):
        assert self._parse("https://www.mydealz.de/deals/ohne-nummer").submit_url is None

    def test_ohne_regel_passiert_nichts(self):
        aktion = self._parse(
            "https://www.mydealz.de/deals/jacobs-2823305",
            {"name": "roh", "parser": "feed"},
        )
        assert aktion.submit_url is None
