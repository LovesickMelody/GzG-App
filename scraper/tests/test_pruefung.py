"""Tests fuer die Eingangspruefung — die Schicht, die Falschangaben aufhaelt."""

from __future__ import annotations

from datetime import date

from gzg_scraper.models import Action
from gzg_scraper.pruefung import Kontext, pruefe, pruefe_liste

HEUTE = date(2026, 8, 15)


def aktion(**felder) -> Action:
    grund = {
        "title": "Duschgel gratis testen",
        "source": "justsnap",
        "url": "https://airwick.justsnap.invalid/",
    }
    grund.update(felder)
    return Action(**grund)


class TestPflichtfelder:
    def test_ohne_titel_abgelehnt(self):
        befund = pruefe(aktion(title=""))
        assert not befund.darf_veroeffentlichen
        assert "kein Titel" in befund.verstoesse[0]

    def test_ohne_ziel_abgelehnt(self):
        befund = pruefe(aktion(url=None, submit_url=None))
        assert not befund.darf_veroeffentlichen

    def test_javascript_adresse_abgelehnt(self):
        """Ein Link, den die App nicht öffnen kann, ist schlimmer als keiner."""
        befund = pruefe(aktion(url="javascript:void(0)"))
        assert not befund.darf_veroeffentlichen

    def test_vollstaendige_aktion_geht_durch(self):
        assert pruefe(aktion()).darf_veroeffentlichen


class TestBetragBelegt:
    """Die wichtigste Regel: Kein Betrag ohne Beleg auf der Seite."""

    def test_betrag_im_text_geht_durch(self):
        befund = pruefe(
            aktion(max_refund_cents=899),
            Kontext(seitentext="Du erhältst bis zu 8,99 € zurück."),
        )
        assert befund.darf_veroeffentlichen

    def test_erfundener_betrag_faellt_durch(self):
        """Der Fall, für den die ganze Schicht gebaut ist."""
        befund = pruefe(
            aktion(max_refund_cents=1299),
            Kontext(seitentext="Du erhältst bis zu 8,99 € zurück."),
        )
        assert not befund.darf_veroeffentlichen
        assert "12.99" in befund.verstoesse[0]

    def test_punkt_statt_komma_zaehlt_auch(self):
        befund = pruefe(
            aktion(max_refund_cents=899), Kontext(seitentext="Refund: 8.99 EUR")
        )
        assert befund.darf_veroeffentlichen

    def test_runder_betrag_braucht_waehrung(self):
        """Eine nackte '5' steht auf jeder Seite und darf nichts durchwinken."""
        abgelehnt = pruefe(
            aktion(max_refund_cents=500),
            Kontext(seitentext="Schritt 5 von 6: Bankverbindung angeben"),
        )
        assert not abgelehnt.darf_veroeffentlichen

        angenommen = pruefe(
            aktion(max_refund_cents=500), Kontext(seitentext="Wir erstatten 5 €.")
        )
        assert angenommen.darf_veroeffentlichen

    def test_geschuetztes_leerzeichen_stoert_nicht(self):
        befund = pruefe(
            aktion(max_refund_cents=499), Kontext(seitentext="Preis: 4,99 €")
        )
        assert befund.darf_veroeffentlichen

    def test_ohne_seitentext_wird_nicht_geprueft(self):
        """Die Portal-Quellen haben keinen Seitentext — sie dürfen nicht leerlaufen."""
        assert pruefe(aktion(max_refund_cents=1299)).darf_veroeffentlichen


class TestGestartet:
    """Der Vorab-Leak: CT-Logs kennen Kampagnen vor ihrem Start."""

    def test_kuenftiger_start_abgelehnt(self):
        befund = pruefe(aktion(valid_from="2026-12-01"), Kontext(heute=HEUTE))
        assert not befund.darf_veroeffentlichen
        assert "startet erst" in befund.verstoesse[0]

    def test_laufende_aktion_geht_durch(self):
        befund = pruefe(aktion(valid_from="2026-08-01"), Kontext(heute=HEUTE))
        assert befund.darf_veroeffentlichen

    def test_start_heute_geht_durch(self):
        befund = pruefe(aktion(valid_from="2026-08-15"), Kontext(heute=HEUTE))
        assert befund.darf_veroeffentlichen

    def test_ohne_startdatum_keine_regel(self):
        assert pruefe(aktion(), Kontext(heute=HEUTE)).darf_veroeffentlichen


class TestFristPlausibel:
    def test_absurde_frist_abgelehnt(self):
        befund = pruefe(
            aktion(submission_deadline="2231-12-31"), Kontext(heute=HEUTE)
        )
        assert not befund.darf_veroeffentlichen

    def test_normale_frist_geht_durch(self):
        befund = pruefe(
            aktion(submission_deadline="2026-09-30"), Kontext(heute=HEUTE)
        )
        assert befund.darf_veroeffentlichen

    def test_unlesbares_datum_wird_behalten(self):
        """Gleiche Haltung wie in filtere_abgelaufene: lieber behalten."""
        befund = pruefe(aktion(submission_deadline="demnächst"), Kontext(heute=HEUTE))
        assert befund.darf_veroeffentlichen


class TestVorbehalt:
    def test_vorbehalt_blockt(self):
        befund = pruefe(aktion(), Kontext(vorbehalt="Data Mining untersagt"))
        assert not befund.darf_veroeffentlichen


class TestListe:
    def test_filtert_und_behaelt_reihenfolge(self):
        aktionen = [
            aktion(title="Erste"),
            aktion(title=""),
            aktion(title="Dritte"),
        ]
        behalten = pruefe_liste(aktionen, quellenname="test")
        assert [a.title for a in behalten] == ["Erste", "Dritte"]

    def test_sammelt_alle_verstoesse_einer_aktion(self):
        """Ein Lauf soll alle Gründe zeigen, nicht nur den ersten."""
        befund = pruefe(
            aktion(title="", valid_from="2026-12-01", max_refund_cents=1299),
            Kontext(seitentext="nichts davon", heute=HEUTE),
        )
        assert len(befund.verstoesse) == 3
