"""Parser-Tests gegen gespeicherte HTML-Fixtures — kein Netz noetig."""

from __future__ import annotations

from pathlib import Path

import pytest

from gzg_scraper.parsers.css_listing import parse

FIXTURES = Path(__file__).parent / "fixtures"

QUELLE = {
    "name": "testportal",
    "base_url": "https://www.beispiel.de/",
    "parser": "css_listing",
    "selectors": {
        "item": "article.promo-card",
        "title": "h2.promo-title",
        "link": "a.promo-link@href",
        "brand": "span.promo-brand",
        "max_refund": "span.promo-amount",
        "deadline": "span.promo-deadline",
        "image": "img.promo-image@src",
    },
}


@pytest.fixture
def aktionen():
    html = (FIXTURES / "listing_typisch.html").read_text(encoding="utf-8")
    return parse(html, QUELLE)


class TestTypischeSeite:
    def test_findet_alle_vollstaendigen_eintraege(self, aktionen):
        # Vier Karten im HTML, eine davon ohne Titel und damit unbrauchbar.
        assert len(aktionen) == 3

    def test_liest_titel_und_marke(self, aktionen):
        erste = aktionen[0]
        assert erste.title == "Duschgel Sensitive gratis testen"
        assert erste.brand == "Nivea"

    def test_liest_betrag_als_cent(self, aktionen):
        assert aktionen[0].max_refund_cents == 399
        assert aktionen[1].max_refund_cents == 200
        assert aktionen[2].max_refund_cents == 449

    def test_liest_frist(self, aktionen):
        assert aktionen[0].submission_deadline == "2026-10-14"
        assert aktionen[2].submission_deadline == "2026-11-01"

    def test_nimmt_das_aktionsende_als_ersatzfrist(self, aktionen):
        # Der zweite Eintrag nennt keinen Einsendeschluss, nur ein Laufzeitende.
        assert aktionen[1].submission_deadline == "2026-09-30"

    def test_macht_relative_links_absolut(self, aktionen):
        assert aktionen[0].url == "https://www.beispiel.de/aktion/duschgel-sensitive"
        assert aktionen[0].image_url == "https://www.beispiel.de/bilder/duschgel.jpg"

    def test_laesst_absolute_links_in_ruhe(self, aktionen):
        assert aktionen[1].url == "https://www.beispiel.de/aktion/kaffee"
        assert aktionen[1].image_url == "https://cdn.beispiel.de/kaffee.png"

    def test_erkennt_die_art(self, aktionen):
        assert aktionen[0].type == "gratis_testen"
        assert aktionen[1].type == "cashback_teilbetrag"

    def test_findet_haendler(self, aktionen):
        assert aktionen[0].retailers == ["dm", "Rossmann"]
        assert set(aktionen[1].retailers) == {"Edeka", "Kaufland"}

    def test_findet_ean(self, aktionen):
        assert aktionen[0].eans == ["4005900123459"]
        assert aktionen[1].eans == []

    def test_setzt_die_quelle(self, aktionen):
        assert all(aktion.source == "testportal" for aktion in aktionen)

    def test_vergibt_stabile_ids(self, aktionen):
        ids = [aktion.id for aktion in aktionen]
        assert len(set(ids)) == 3
        assert all(len(kennung) == 12 for kennung in ids)


class TestUmgebauteSeite:
    def test_liefert_nichts_statt_muell(self):
        """
        Nach einem Relaunch treffen die alten Selektoren nicht mehr. Der Parser
        muss dann leer ausgehen — der Aufrufer behaelt daraufhin den alten Stand,
        statt die Aktionsliste mit Halbfertigem zu ueberschreiben.
        """
        html = (FIXTURES / "listing_umgebaut.html").read_text(encoding="utf-8")
        assert parse(html, QUELLE) == []

    def test_meldet_den_ausfall_im_log(self, caplog):
        html = (FIXTURES / "listing_umgebaut.html").read_text(encoding="utf-8")
        with caplog.at_level("WARNING"):
            parse(html, QUELLE)
        assert any("trifft nichts" in eintrag.message or "Markup" in eintrag.message
                   for eintrag in caplog.records)


class TestRobustheit:
    def test_leeres_html(self):
        assert parse("", QUELLE) == []

    def test_html_ohne_passende_container(self):
        assert parse("<html><body><p>nichts</p></body></html>", QUELLE) == []

    def test_fehlende_selektoren_sind_kein_fehler(self):
        html = (FIXTURES / "listing_typisch.html").read_text(encoding="utf-8")
        knapp = {
            **QUELLE,
            "selectors": {"item": "article.promo-card", "title": "h2.promo-title"},
        }
        aktionen = parse(html, knapp)
        assert len(aktionen) == 3
        assert aktionen[0].brand is None
        assert aktionen[0].url is None
        # Ohne eigenen Betrags-Selektor wird der Eintragstext durchsucht.
        assert aktionen[0].max_refund_cents == 399
