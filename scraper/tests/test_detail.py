"""Tests der Detailseiten-Anreicherung — gegen eine Fixture, ohne Netz."""

from __future__ import annotations

from gzg_scraper.detail import reichere_an
from gzg_scraper.models import Action
from gzg_scraper.run import filtere_abgelaufene, filtere_arten

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


class TestAbgelaufeneFilter:
    from datetime import date as _date

    HEUTE = _date(2026, 8, 14)

    def test_wirft_abgelaufene_weg(self):
        aktionen = [
            Action(title="Vorbei", source="q", submission_deadline="2026-07-29"),
            Action(title="Läuft", source="q", submission_deadline="2026-09-30"),
        ]
        behalten = filtere_abgelaufene(aktionen, "q", heute=self.HEUTE)
        assert [a.title for a in behalten] == ["Läuft"]

    def test_heute_zaehlt_noch(self):
        # Am letzten Tag kann man noch einreichen.
        aktionen = [Action(title="Heute", source="q", submission_deadline="2026-08-14")]
        assert len(filtere_abgelaufene(aktionen, "q", heute=self.HEUTE)) == 1

    def test_ohne_frist_bleibt_die_aktion_stehen(self):
        # "Keine Frist bekannt" heisst nicht "abgelaufen" — eine der Quellen
        # liefert grundsaetzlich keine.
        aktionen = [Action(title="Ohne", source="q")]
        assert len(filtere_abgelaufene(aktionen, "q", heute=self.HEUTE)) == 1

    def test_faellt_auf_valid_to_zurueck(self):
        aktionen = [Action(title="Ende", source="q", valid_to="2026-07-01")]
        assert filtere_abgelaufene(aktionen, "q", heute=self.HEUTE) == []

    def test_unlesbares_datum_wirft_nichts_weg(self):
        aktionen = [Action(title="Krumm", source="q", submission_deadline="demnächst")]
        assert len(filtere_abgelaufene(aktionen, "q", heute=self.HEUTE)) == 1


class TestBedingungenVonDerAktionsseite:
    """
    Die Checkliste sah bei jeder Aktion gleich aus, weil sie aus immer gleichem
    Portaltext kam. Die echten Bedingungen stehen beim Anbieter.
    """

    AKTIONSSEITE = """
    <html><body>
      <script>var egal = "produktfoto";</script>
      <h1>Jacobs gratis testen</h1>
      <p>Kaufe das Produkt und fotografiere Produkt und Kassenbon zusammen.</p>
      <p>Wir senden dir einen Bestätigungscode per SMS an deine Handynummer.</p>
      <p>Anschließend IBAN angeben.</p>
    </body></html>
    """

    QUELLE = {"name": "testportal", "bedingungen_von_aktionsseite": True}

    def test_liest_die_bedingungen_beim_anbieter(self):
        aktionen = [aktion()]
        aktionen[0].submit_url = "https://anbieter.example/aktion"
        reichere_an(aktionen, self.QUELLE, FetcherAttrappe(html=self.AKTIONSSEITE))
        assert aktionen[0].requirements == [
            "produktfoto",
            "bonfoto",
            "zusammen_fotografieren",
            "handy_verifizierung",
            "iban",
        ]

    def test_ruft_die_aktionsseite_ab_nicht_die_portalseite(self):
        fetcher = FetcherAttrappe(html=self.AKTIONSSEITE)
        eintrag = aktion(url="https://portal.example/artikel")
        eintrag.submit_url = "https://anbieter.example/aktion"
        reichere_an([eintrag], self.QUELLE, fetcher)
        assert fetcher.abgerufen == ["https://anbieter.example/aktion"]

    def test_ersetzt_die_schwaechere_angabe_aus_dem_portal(self):
        eintrag = aktion()
        eintrag.submit_url = "https://anbieter.example/aktion"
        eintrag.requirements = ["bonfoto"]
        reichere_an([eintrag], self.QUELLE, FetcherAttrappe(html=self.AKTIONSSEITE))
        assert "zusammen_fotografieren" in eintrag.requirements

    def test_ohne_treffer_bleibt_die_alte_angabe_stehen(self):
        # Schlechter darf es nie werden.
        eintrag = aktion()
        eintrag.submit_url = "https://anbieter.example/aktion"
        eintrag.requirements = ["bonfoto"]
        reichere_an([eintrag], self.QUELLE, FetcherAttrappe(html="<html><body>Hallo</body></html>"))
        assert eintrag.requirements == ["bonfoto"]

    def test_ohne_einreichungslink_wird_nichts_abgerufen(self):
        fetcher = FetcherAttrappe(html=self.AKTIONSSEITE)
        reichere_an([aktion()], self.QUELLE, fetcher)
        assert fetcher.abgerufen == []

    def test_skripte_zaehlen_nicht_als_text(self):
        # Im <script> steht "produktfoto" — als Wort fuer Maschinen, nicht fuer
        # Menschen. Es darf keinen Haken ausloesen.
        eintrag = aktion()
        eintrag.submit_url = "https://anbieter.example/aktion"
        seite = '<html><body><script>var x = "Produkt fotografieren";</script>' \
                '<p>Nur den Kassenbon hochladen.</p></body></html>'
        reichere_an([eintrag], self.QUELLE, FetcherAttrappe(html=seite))
        assert eintrag.requirements == ["bonfoto"]
