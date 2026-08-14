"""Tests der Textbausteine. Laufen offline, ohne Netz."""

from __future__ import annotations

import pytest

from gzg_scraper.parsing import (
    art_aus_text,
    betrag_in_cent,
    datum_iso,
    eans_aus,
    haendler_aus,
    pruefziffer_stimmt,
    saeubere,
)


class TestBetrag:
    @pytest.mark.parametrize(
        ("text", "erwartet"),
        [
            ("3,99 €", 399),
            ("bis zu 4,99 EUR", 499),
            ("10 Euro", 1000),
            ("1.234,50 €", 123450),
            ("0,99€", 99),
            ("2,5 €", 250),
            ("Preis: 12,00 € inkl. MwSt.", 1200),
            ("5.99", 599),
        ],
    )
    def test_liest_betraege(self, text, erwartet):
        assert betrag_in_cent(text) == erwartet

    @pytest.mark.parametrize("text", ["", None, "kein Betrag", "gratis"])
    def test_ohne_betrag_none(self, text):
        assert betrag_in_cent(text) is None

    def test_nimmt_den_ersten_betrag(self):
        assert betrag_in_cent("statt 5,99 € nur 3,99 €") == 599

    @pytest.mark.parametrize(
        "text",
        [
            # Aus den echten Portalseiten: ohne Absicherung las die Erkennung
            # hier 30,08 € beziehungsweise 1,45 € — beides frei erfunden.
            "Eingetragen am: 10.08.2026",
            "Zeitraum: 10.08.2026 – 30.08.2026",
            "Gültig bis 30.08.2026",
            "Laut Community noch etwa 1.450 Einlösungen",
            "Bestellnummer 12.3456",
            # Füllmengen und Maße stehen in fast jedem Produkttitel.
            "SACHSEN QUELLE medium+ lemon 0,75l",
            "Flasche 0,75 l",
            "Kabel 1,5 m lang",
            "Packung 0,25 kg",
        ],
    )
    def test_haelt_datum_menge_und_lange_zahl_nicht_fuer_geld(self, text):
        assert betrag_in_cent(text) is None

    def test_ein_wort_nach_dem_betrag_ist_keine_einheit(self):
        # "im" faengt mit einem Buchstaben an, ist aber keine Einheit — der
        # Betrag muss trotzdem durchkommen.
        assert betrag_in_cent("nur 3,99 im Angebot") == 399

    def test_findet_den_betrag_trotzdem_wenn_ein_datum_danebensteht(self):
        assert betrag_in_cent("Gültig bis 30.08.2026, du bekommst 4,99 € zurück") == 499


class TestDatum:
    @pytest.mark.parametrize(
        ("text", "erwartet"),
        [
            ("31.12.2026", "2026-12-31"),
            ("Einsendeschluss: 14.10.2026", "2026-10-14"),
            ("1.1.2027", "2027-01-01"),
            ("31.12.26", "2026-12-31"),
            ("2026-09-30", "2026-09-30"),
            ("30. September 2026", "2026-09-30"),
            ("5. Mai 2026", "2026-05-05"),
            ("bis zum 1. März 2027", "2027-03-01"),
        ],
    )
    def test_liest_daten(self, text, erwartet):
        assert datum_iso(text) == erwartet

    @pytest.mark.parametrize("text", ["", None, "demnächst", "irgendwann 2026"])
    def test_ohne_datum_none(self, text):
        assert datum_iso(text) is None

    def test_unmoegliches_datum_wird_verworfen(self):
        assert datum_iso("31.02.2026") is None


class TestEan:
    def test_findet_gueltige_ean13(self):
        assert eans_aus("Artikel EAN 4005900123459 im Regal") == ["4005900123459"]

    def test_verwirft_falsche_pruefziffer(self):
        # Gleiche Ziffernfolge, letzte Stelle verdreht.
        assert eans_aus("EAN 4005900123456") == []

    def test_findet_mehrere_ohne_dubletten(self):
        text = "4005900123459 und 4005900123459 und 96385074"
        assert eans_aus(text) == ["4005900123459", "96385074"]

    @pytest.mark.parametrize(
        ("code", "gueltig"),
        [
            ("4005900123459", True),
            ("96385074", True),
            ("4006381333931", True),
            ("4005900123456", False),
            ("1234567890123", False),
            ("12345", False),
            ("abcdefgh", False),
        ],
    )
    def test_pruefziffer(self, code, gueltig):
        assert pruefziffer_stimmt(code) is gueltig

    def test_ohne_text_leer(self):
        assert eans_aus(None) == []


class TestArt:
    def test_erkennt_teilbetrag(self):
        assert art_aus_text("2 Euro Cashback auf jede Packung") == "cashback_teilbetrag"
        assert art_aus_text("Teilbetrag zurück") == "cashback_teilbetrag"

    def test_gratis_testen_ist_der_regelfall(self):
        assert art_aus_text("Produkt gratis testen") == "gratis_testen"
        assert art_aus_text("") == "gratis_testen"
        assert art_aus_text(None) == "gratis_testen"

    def test_gratis_schlaegt_cashback(self):
        assert art_aus_text("Gratis testen per Cashback-Aktion") == "gratis_testen"


class TestHilfen:
    def test_saeubert_leerraum(self):
        assert saeubere("  viel   Platz \n hier ") == "viel Platz hier"
        assert saeubere("   ") is None
        assert saeubere(None) is None

    def test_findet_bekannte_haendler(self):
        text = "Erhältlich bei dm und Rossmann, nicht bei Karstadt"
        assert haendler_aus(text, ["dm", "Rossmann", "Edeka"]) == ["dm", "Rossmann"]

    def test_ohne_text_keine_haendler(self):
        assert haendler_aus(None, ["dm"]) == []
