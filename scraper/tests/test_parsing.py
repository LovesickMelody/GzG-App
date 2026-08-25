"""Tests der Textbausteine. Laufen offline, ohne Netz."""

from __future__ import annotations

import pytest

from gzg_scraper.parsing import (
    art_aus_text,
    betrag_in_cent,
    datum_iso,
    eans_aus,
    haendler_aus,
    kontingent_aus,
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

    @pytest.mark.parametrize(
        "text",
        [
            # Echte Titel aus dem mydealz-Feed. "100 % Cashback" ist gratis
            # testen — das Wort Cashback allein sagt nichts über die Höhe.
            "[GzG] 100% Cashback auf PET Einweg Einzelflaschen",
            "GZG - 100 % zurück auf Pantene Pro-V Repair & Care",
            "Kaufpreis erstattet nach Einsendung",
            "Du bekommst den vollen Kaufpreis zurück",
        ],
    )
    def test_volle_erstattung_ist_gratis_testen(self, text):
        assert art_aus_text(text) == "gratis_testen"

    def test_haelt_50_prozent_auseinander(self):
        assert art_aus_text("Naturals 50 % Cashback-Aktion") == "cashback_teilbetrag"


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


class TestKontingent:
    """
    Viele Aktionen sind gedeckelt und werden zu einem festen Zeitpunkt
    zurueckgesetzt. Wer das nicht weiss, kauft das Produkt und stellt beim
    Einreichen fest, dass er zu spaet dran war.
    """

    def test_liest_anzahl_und_zeitraum(self):
        angaben = kontingent_aus(
            "Die Aktion ist auf 1.000 Teilnahmen pro Woche begrenzt."
        )
        assert angaben["anzahl"] == 1000
        assert angaben["zeitraum"] == "woche"

    def test_liest_den_zeitpunkt_der_zuruecksetzung(self):
        angaben = kontingent_aus(
            "Das Kontingent wird jeden Montag um 09:00 Uhr zurückgesetzt."
        )
        assert angaben["zuruecksetzung"] == "Montags um 09:00 Uhr"

    def test_versteht_taegliche_zuruecksetzung(self):
        angaben = kontingent_aus(
            "Täglich um 0 Uhr wird das Kontingent neu freigeschaltet."
        )
        assert angaben["zuruecksetzung"] == "Täglich um 00:00 Uhr"

    def test_ohne_uhrzeit_bleibt_der_tag(self):
        angaben = kontingent_aus("Montags wird das Kontingent zurückgesetzt.")
        assert angaben["zuruecksetzung"] == "Montags"

    def test_haelt_oeffnungszeiten_heraus(self):
        # "Montag 9 Uhr" ohne ein Wort vom Zuruecksetzen ist keine Angabe zum
        # Kontingent, sondern meistens eine Oeffnungszeit.
        angaben = kontingent_aus("Unsere Hotline erreichen Sie Montag ab 9 Uhr.")
        assert angaben["zuruecksetzung"] is None

    def test_ignoriert_einen_teilnehmerzaehler(self):
        # "Schon 30.652 Teilnahmen!" ist Werbung, keine Obergrenze. Der erste
        # Anlauf hat genau das als Kontingent ausgegeben.
        angaben = kontingent_aus("Schon 30.652 Teilnahmen! Mach jetzt mit.")
        assert angaben["anzahl"] is None

    def test_braucht_ein_wort_das_die_zahl_zur_grenze_macht(self):
        assert kontingent_aus("Es stehen insgesamt 25.000 Teilnahmen zur Verfügung.")["anzahl"] == 25000
        assert kontingent_aus("Bisher 25.000 Teilnahmen.")["anzahl"] is None

    def test_ignoriert_kleine_zahlen(self):
        # "2 Teilnahmen je Haushalt" ist eine andere Aussage als ein Kontingent.
        angaben = kontingent_aus("Pro Haushalt sind 2 Teilnahmen möglich.")
        assert angaben["anzahl"] is None

    def test_erkennt_ein_erschoepftes_kontingent(self):
        assert kontingent_aus("Das Kontingent ist für heute erschöpft.")["erschoepft"] is True

    def test_bedingung_zaehlt_auch_ueber_satzgrenzen_hinweg_nicht(self):
        # Seitentexte haben keine verlaesslichen Satzgrenzen. Geprueft wird
        # deshalb der unmittelbare Zusammenhang, nicht der ganze Satz.
        angaben = kontingent_aus(
            "Teilnahmebedingungen Sobald das Kontingent erschöpft ist endet die Aktion "
            "Weitere Hinweise finden Sie unten"
        )
        assert angaben["erschoepft"] is False

    def test_haelt_die_bedingung_aus_dem_zustand_heraus(self):
        # Dieser Satz steht in fast jeden Teilnahmebedingungen und bedeutet das
        # Gegenteil: Die Aktion laeuft noch.
        angaben = kontingent_aus(
            "Sobald das Kontingent erschöpft ist, endet die Aktion vorzeitig."
        )
        assert angaben["erschoepft"] is False

    def test_solange_der_vorrat_reicht_ist_keine_aussage(self):
        angaben = kontingent_aus("Gratis testen, solange der Vorrat reicht.")
        assert angaben["erschoepft"] is False
        assert angaben["anzahl"] is None

    def test_leerer_text_ergibt_leere_angaben(self):
        assert kontingent_aus("") == {
            "anzahl": None,
            "zeitraum": None,
            "zuruecksetzung": None,
            "erschoepft": False,
        }


class TestKontingentAusEchtenSeiten:
    """
    Formulierungen, die im Sammellauf wirklich vorkamen.

    Die erste Fassung des Musters entstand am Schreibtisch und griff bei keiner
    davon. Erst das Log der echten Seiten zeigte, wie die Anbieter schreiben.
    """

    # Der Satz, um den es von Anfang an ging — woertlich aus dem Sammellauf.
    SENSODYNE = (
        "Teilnahmezeitraum 03.08.2026 - 31.10.2026 Nachweis Bon und Produkt auf einem "
        "Foto Aktionspackung Aktionssticker Limits Teilnahmelimit 1 Einlösung pro Person "
        "Kaufmenge 1 Einlöselimit pro Woche 1.000 montags ab 08:00 Uhr Länder"
    )

    def test_beschriftung_vor_der_zahl(self):
        # Die 25.000 des Gesamtkontingents stehen woanders auf derselben Seite —
        # entscheidend ist diese Zeile.
        angaben = kontingent_aus(self.SENSODYNE)
        assert angaben["anzahl"] == 1000
        assert angaben["zeitraum"] == "woche"

    def test_zuruecksetzung_ohne_das_wort_zurueckgesetzt(self):
        # "1.000 montags ab 08:00 Uhr" — kein Wort von Zuruecksetzen, und doch
        # genau das. Es reicht, dass der Satz von einem Limit handelt.
        assert kontingent_aus(self.SENSODYNE)["zuruecksetzung"] == "Montags um 08:00 Uhr"

    def test_wochentag_ohne_limit_ist_keine_zuruecksetzung(self):
        # Aus dem Fernsehprogramm einer O2-Aktion. Wochentag und Uhrzeit allein
        # sagen nichts ueber ein Kontingent.
        angaben = kontingent_aus(
            "Villa der Versuchung, Sat.1, ab 3.8 jeden Montag Exklusiv, RTL, "
            "Montag bis Freitag um 18:30 Uhr"
        )
        assert angaben["zuruecksetzung"] is None

    def test_weiches_trennzeichen_im_wort(self):
        # BiFi: Im HTML steht "Teilnahme\u00adkontingent" mit weichem Trennstrich.
        angaben = kontingent_aus(
            "Leider ist das Teilnahme\u00adkontingent für die Kaufpreis-Erstattungen "
            "vollständig ausgeschöpft."
        )
        assert angaben["erschoepft"] is True

    def test_bedingung_mit_verb_am_satzanfang(self):
        # Deli Reform: "Ist das ... ausgeschöpft, kannst du ..." — eine Bedingung
        # ohne das Wort "wenn". Sie sagt gerade nicht, dass es jetzt so ist.
        angaben = kontingent_aus(
            "Ist das tägliche Kontingent der Gratis-Testen-Aktion ausgeschöpft "
            "kannst du hier das Gewinnspiel-Formular absenden."
        )
        assert angaben["erschoepft"] is False

    def test_freigeschaltet_ohne_das_wort_neu(self):
        # Valess. Beachte den Tippfehler auf der Seite: "Wochenkontigent".
        angaben = kontingent_aus(
            "Das nächste Wochenkontigent wird kommenden Montag um 8:00 Uhr freigeschaltet."
        )
        assert angaben["zuruecksetzung"] == "Montags um 08:00 Uhr"

    def test_heutiges_kontingent_ist_ausgeschoepft(self):
        # Jacobs.
        angaben = kontingent_aus(
            "Heutiges Kontingent ist ausgeschöpft – bitte komm morgen wieder"
        )
        assert angaben["erschoepft"] is True

    def test_teilnahmelimit_mit_woertern_dazwischen(self):
        # BiFi: "erreicht" statt "erschöpft", und "für heute" dazwischen.
        assert kontingent_aus("Leider ist das Teilnahmelimit für heute erreicht.")["erschoepft"]

    def test_restzaehler_ist_keine_obergrenze(self):
        # "Noch 7.585 verfügbar" sagt, wie viel uebrig ist — nicht, wie viel es
        # insgesamt gab. Genau diese Zahl stand faelschlich im Feed.
        assert kontingent_aus("Noch 7.585 Teilnahmen verfügbar")["anzahl"] is None

    def test_pro_haushalt_bleibt_draussen(self):
        # funny-frisch.
        angaben = kontingent_aus("Bis zu vier Teilnahmen pro Haushalt und deutscher IBAN.")
        assert angaben["anzahl"] is None
