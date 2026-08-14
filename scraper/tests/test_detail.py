"""Tests der Detailseiten-Anreicherung — gegen eine Fixture, ohne Netz."""

from __future__ import annotations

from gzg_scraper.detail import reichere_an
from gzg_scraper.models import Action
from gzg_scraper.run import filtere_arten

DETAILSEITE = """
<html><body>
  <main>
    <h1>Bonduelle Frische Salate</h1>
    <p>Scondoo Account erforderlich (App oder Website)</p>
    <a class="button" href="/teilnahmebedingungen">Teilnahmebedingungen</a>
    <a class="button" href="https://scondoo.de/?cashbackDetail=81788">Zur Aktion</a>
    <p>Kaufe einen Salat, bewahre den Kassenbon auf und fotografiere alles zusammen.</p>
  </main>
</body></html>
"""


class FetcherAttrappe:
    """Liefert immer dieselbe Seite und merkt sich, was abgerufen wurde."""

    def __init__(self, html: str | None = DETAILSEITE):
        self.html = html
        self.abgerufen: list[str] = []

    def hole(self, url: str) -> str | None:
        self.abgerufen.append(url)
        return self.html


QUELLE = {
    "name": "testportal",
    "base_url": "https://portal.example/",
    "detail": {
        "enabled": True,
        "submit_link": "a.button@href",
        "submit_link_text": "Zur Aktion",
    },
}


def aktion(url: str | None = "https://portal.example/aktion/salat") -> Action:
    return Action(title="Salat", source="testportal", url=url)


class TestAnreicherung:
    def test_nimmt_den_link_mit_der_richtigen_beschriftung(self):
        # Dieselbe Knopf-Klasse fuehrt auch zu den Teilnahmebedingungen — die
        # Beschriftung entscheidet.
        aktionen = [aktion()]
        reichere_an(aktionen, QUELLE, FetcherAttrappe())
        assert aktionen[0].submit_url == "https://scondoo.de/?cashbackDetail=81788"

    def test_baut_die_checkliste_aus_der_detailseite(self):
        aktionen = [aktion()]
        reichere_an(aktionen, QUELLE, FetcherAttrappe())
        assert aktionen[0].requirements == [
            "produktfoto",
            "bonfoto",
            "zusammen_fotografieren",
            "app",
        ]

    def test_ohne_detail_block_wird_nichts_abgerufen(self):
        fetcher = FetcherAttrappe()
        aktionen = [aktion()]
        reichere_an(aktionen, {"name": "ohne"}, fetcher)
        assert fetcher.abgerufen == []
        assert aktionen[0].submit_url is None

    def test_ueberschreibt_einen_vorhandenen_link_nicht(self):
        # Steht der Link schon in der Uebersicht, ist er der genauere.
        vorhanden = aktion()
        vorhanden.submit_url = "https://direkt.example/formular"
        reichere_an([vorhanden], QUELLE, FetcherAttrappe())
        assert vorhanden.submit_url == "https://direkt.example/formular"

    def test_gescheiterter_abruf_laesst_die_aktion_stehen(self):
        # Eine Aktion ohne Einreichungslink ist besser als keine Aktion.
        aktionen = [aktion()]
        reichere_an(aktionen, QUELLE, FetcherAttrappe(html=None))
        assert aktionen[0].title == "Salat"
        assert aktionen[0].submit_url is None

    def test_aktion_ohne_url_wird_uebersprungen(self):
        fetcher = FetcherAttrappe()
        reichere_an([aktion(url=None)], QUELLE, fetcher)
        assert fetcher.abgerufen == []


class TestArtenFilter:
    def _aktionen(self) -> list[Action]:
        return [
            Action(title="Gratis", source="q", type="gratis_testen"),
            Action(title="Teil", source="q", type="cashback_teilbetrag"),
        ]

    def test_behaelt_standardmaessig_nur_volle_erstattungen(self):
        behalten = filtere_arten(self._aktionen(), {"name": "q"})
        assert [a.title for a in behalten] == ["Gratis"]

    def test_leere_liste_heisst_alles_behalten(self):
        behalten = filtere_arten(self._aktionen(), {"name": "q", "nur_arten": []})
        assert len(behalten) == 2

    def test_mehrere_arten_lassen_sich_erlauben(self):
        quelle = {"name": "q", "nur_arten": ["gratis_testen", "cashback_teilbetrag"]}
        assert len(filtere_arten(self._aktionen(), quelle)) == 2
