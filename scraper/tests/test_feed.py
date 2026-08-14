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
