"""
Tests fuer die robots-Ausnahme.

Die Ausnahme ist die einzige Stelle, an der der Scraper eine ``robots.txt``
uebergeht. Sie muss deshalb genau so schmal sein, wie sie aussieht — und das
laesst sich pruefen.
"""

from __future__ import annotations

import urllib.robotparser

from gzg_scraper.fetch import DOKUMENTIERTE_APIS, Fetcher, _ist_dokumentierte_api


def alles_verboten() -> urllib.robotparser.RobotFileParser:
    regeln = urllib.robotparser.RobotFileParser()
    regeln.parse(["User-agent: *", "Disallow: /"])
    return regeln


class TestDokumentierteApis:
    def test_certspotter_ist_dabei(self):
        assert _ist_dokumentierte_api(
            "https://api.certspotter.com/v1/issuances?domain=justsnap.eu"
        )

    def test_crt_sh_ist_dabei(self):
        assert _ist_dokumentierte_api("https://crt.sh/?q=%25.justsnap.eu&output=json")

    def test_gewoehnliche_seite_nicht(self):
        assert not _ist_dokumentierte_api("https://airwick.justsnap.eu/")

    def test_unterdomaene_zaehlt_nicht(self):
        """
        Sonst genügte eine Subdomain, um die Prüfung für eine beliebige Seite
        auszuhebeln.
        """
        assert not _ist_dokumentierte_api("https://boese.api.certspotter.com/")

    def test_angehaengter_name_zaehlt_nicht(self):
        assert not _ist_dokumentierte_api("https://crt.sh.boeses.invalid/x")

    def test_anmeldedaten_taeuschen_nicht(self):
        """'https://crt.sh@boeses.invalid/' führt zu boeses.invalid, nicht zu crt.sh."""
        assert not _ist_dokumentierte_api("https://crt.sh@boeses.invalid/x")

    def test_grossschreibung_stoert_nicht(self):
        assert _ist_dokumentierte_api("https://API.CertSpotter.com/v1/issuances")

    def test_die_liste_bleibt_kurz(self):
        """
        Eine Ausnahmeliste, die wächst, ist keine Ausnahme mehr. Schlägt dieser
        Test an, gehört der neue Eintrag begründet — nicht der Test angepasst.
        """
        assert DOKUMENTIERTE_APIS == {"api.certspotter.com", "crt.sh"}


class TestRobotsGreiftWeiterhin:
    def test_kampagnenseite_bleibt_geschuetzt(self, monkeypatch):
        """
        Der eigentliche Punkt: Auf den Seiten, um die es rechtlich geht, ändert
        die Ausnahme nichts.
        """
        fetcher = Fetcher()
        monkeypatch.setattr(fetcher, "_robots_fuer", lambda url: alles_verboten())
        assert fetcher.darf("https://airwick.justsnap.eu/") is False

    def test_api_wird_nicht_einmal_gefragt(self, monkeypatch):
        """Für die Schnittstellen wird die robots.txt gar nicht erst geholt."""
        fetcher = Fetcher()

        def darf_nicht_aufgerufen_werden(url):
            raise AssertionError(f"robots.txt für {url} sollte nicht geholt werden")

        monkeypatch.setattr(fetcher, "_robots_fuer", darf_nicht_aufgerufen_werden)
        assert fetcher.darf("https://api.certspotter.com/v1/issuances") is True

    def test_ohne_robots_txt_bleibt_alles_erlaubt(self, monkeypatch):
        """Unveränderte Regel: keine robots.txt heißt erlaubt."""
        fetcher = Fetcher()
        monkeypatch.setattr(fetcher, "_robots_fuer", lambda url: None)
        assert fetcher.darf("https://irgendwas.invalid/seite") is True
