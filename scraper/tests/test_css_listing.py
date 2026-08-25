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


class TestTitelKuerzen:
    """
    Manche Portale haengen an jeden Titel dieselbe Kennzeichnung. In der App
    steht die dann unter jedem Eintrag und verdeckt den Produktnamen.
    """

    HTML = """
    <article class="promo-card">
      <h2 class="promo-title">Bonduelle Frische Salate [gratis testen, Geld zurück!]</h2>
    </article>
    <article class="promo-card">
      <h2 class="promo-title">Gillette Geld zurück – jetzt mitmachen!</h2>
    </article>
    <article class="promo-card">
      <h2 class="promo-title">[gratis testen]</h2>
    </article>
    """

    QUELLE = {
        "name": "testportal",
        "base_url": "https://www.beispiel.de/",
        "titel_entfernen": r"\s*\[[^\]]*\]\s*$",
        "selectors": {"item": "article.promo-card", "title": "h2.promo-title"},
    }

    def test_entfernt_den_zusatz_am_ende(self):
        assert parse(self.HTML, self.QUELLE)[0].title == "Bonduelle Frische Salate"

    def test_laesst_titel_ohne_zusatz_unangetastet(self):
        assert parse(self.HTML, self.QUELLE)[1].title == "Gillette Geld zurück – jetzt mitmachen!"

    def test_kuerzt_nicht_bis_zur_leere(self):
        # Bliebe nichts uebrig, waere der Eintrag in der App namenlos —
        # dann lieber der ungekuerzte Titel.
        assert parse(self.HTML, self.QUELLE)[2].title == "[gratis testen]"

    def test_ohne_muster_bleibt_alles_stehen(self):
        ohne = {**self.QUELLE}
        del ohne["titel_entfernen"]
        assert parse(self.HTML, ohne)[0].title == (
            "Bonduelle Frische Salate [gratis testen, Geld zurück!]"
        )


class TestRabattigel:
    """
    Gegen das echte Markup von rabattigel.de.

    Diese Klasse gibt es wegen eines stillen Ausfalls: Die Seite baute ihren
    Datumsblock um, `div.rgu-date` traf danach nichts mehr, und weil eine
    Aktion ohne Frist als "laeuft noch" durchgeht, fiel erst Wochen spaeter
    auf, dass die Quelle gar nichts mehr lieferte. Ein Test gegen echtes
    Markup meldet den naechsten Umbau sofort.
    """

    import yaml as _yaml

    QUELLE = next(
        q
        for q in _yaml.safe_load(
            (Path(__file__).parents[1] / "sources.yaml").read_text(encoding="utf-8")
        )["sources"]
        if q["name"] == "rabattigel"
    )

    @pytest.fixture
    def aktionen(self):
        html = (FIXTURES / "listing_rabattigel.html").read_text(encoding="utf-8")
        return parse(html, self.QUELLE)

    def test_findet_alle_karten(self, aktionen):
        assert len(aktionen) == 3

    def test_liest_den_einsendeschluss(self, aktionen):
        # "Gültig bis 15.10.2026" — nicht "Eingetragen am 22.08.2026".
        assert aktionen[0].submission_deadline == "2026-10-15"
        assert aktionen[1].submission_deadline == "2026-10-25"

    def test_verwechselt_das_eintragsdatum_nicht_mit_der_frist(self, aktionen):
        # Genau diese Verwechslung wuerde jede Aktion sofort "abgelaufen"
        # aussehen lassen, sobald das Eintragsdatum in der Vergangenheit liegt.
        for aktion in aktionen:
            assert aktion.submission_deadline != "2026-08-22"
            assert aktion.submission_deadline != "2026-08-20"

    def test_karte_ohne_datumsblock_bleibt_erhalten(self, aktionen):
        ohne = aktionen[2]
        assert ohne.title == "Fazer Aito Haferdrink"
        assert ohne.submission_deadline is None

    def test_traegt_keinen_beschreibungstext_als_datum_ein(self, aktionen):
        # Der Kurztext ist Prosa ("So einfach geht's: Packung kaufen …") und
        # war frueher als `valid_from` verdrahtet.
        for aktion in aktionen:
            assert aktion.valid_from is None

    def test_liest_titel_und_einreichungslink(self, aktionen):
        assert aktionen[0].title == "beliebiges Centrum Produkt"
        assert aktionen[0].submit_url == "https://www.erlebe-haleon.de/deals/centrum"
