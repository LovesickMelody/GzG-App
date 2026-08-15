"""Tests fuer die Entdeckung: Zertifikatsprotokolle und Sitemaps."""

from __future__ import annotations

from pathlib import Path

from gzg_scraper.discovery import ct_logs, sitemap

FIXTURES = Path(__file__).parent / "fixtures"
CRT = (FIXTURES / "crt_justsnap.json").read_text(encoding="utf-8")


class FetcherAttrappe:
    def __init__(self, antworten: dict[str, str]):
        self.antworten = antworten
        self.abrufe: list[str] = []

    def hole(self, url: str, still: bool = False) -> str | None:
        self.abrufe.append(url)
        return self.antworten.get(url)


class TestCtLogs:
    def test_findet_die_kampagnen(self):
        kandidaten = ct_logs.lies_antwort(CRT, "justsnap.eu")
        adressen = {k.url for k in kandidaten}
        assert adressen == {
            "https://airwick.justsnap.eu/",
            "https://hoffmanns.justsnap.eu/",
        }

    def test_platzhalter_faellt_weg(self):
        """'*.justsnap.eu' ist keine Adresse, die man abrufen kann."""
        kandidaten = ct_logs.lies_antwort(CRT, "justsnap.eu")
        assert not any("*" in k.url for k in kandidaten)

    def test_infrastruktur_faellt_weg(self):
        kandidaten = ct_logs.lies_antwort(CRT, "justsnap.eu")
        adressen = {k.url for k in kandidaten}
        for name in ("www", "api", "staging"):
            assert f"https://{name}.justsnap.eu/" not in adressen

    def test_mehrstufige_namen_fallen_weg(self):
        kandidaten = ct_logs.lies_antwort(CRT, "justsnap.eu")
        assert "https://db.intern.justsnap.eu/" not in {k.url for k in kandidaten}

    def test_basis_selbst_ist_keine_kampagne(self):
        kandidaten = ct_logs.lies_antwort(CRT, "justsnap.eu")
        assert "https://justsnap.eu/" not in {k.url for k in kandidaten}

    def test_fruehester_zeitpunkt_gewinnt(self):
        """
        Zertifikate werden alle 90 Tage erneuert. Interessant ist, wann die
        Kampagne entstand — nicht, wann ihr Zertifikat zuletzt erneuert wurde.
        """
        kandidaten = {k.url: k for k in ct_logs.lies_antwort(CRT, "justsnap.eu")}
        airwick = kandidaten["https://airwick.justsnap.eu/"]
        assert airwick.zuerst_gesehen == "2026-04-02T08:14:32"

    def test_herkunft_steht_drin(self):
        kandidaten = ct_logs.lies_antwort(CRT, "justsnap.eu")
        assert all(k.entdeckt_ueber == "ct:justsnap.eu" for k in kandidaten)

    def test_kaputte_antwort(self):
        assert ct_logs.lies_antwort("{kein array", "justsnap.eu") == []
        assert ct_logs.lies_antwort(None, "justsnap.eu") == []
        assert ct_logs.lies_antwort("[]", "justsnap.eu") == []

    def test_abruf_ueber_den_fetcher(self):
        adresse = "https://crt.sh/?q=%25.justsnap.eu&output=json"
        fetcher = FetcherAttrappe({adresse: CRT})
        kandidaten = ct_logs.finde("justsnap.eu", fetcher)
        assert len(kandidaten) == 2
        assert fetcher.abrufe == [adresse]

    def test_ausfall_gibt_leere_liste(self):
        assert ct_logs.finde("justsnap.eu", FetcherAttrappe({})) == []


class TestSitemap:
    def test_liest_adressen(self):
        xml = (FIXTURES / "sitemap_aktionen.xml").read_text(encoding="utf-8")
        adressen, verweise = sitemap.lies_adressen(xml)
        assert verweise == []
        assert "https://plattform.invalid/aktion/hoffmanns-reis" in adressen

    def test_erkennt_index(self):
        xml = (FIXTURES / "sitemap_index.xml").read_text(encoding="utf-8")
        adressen, verweise = sitemap.lies_adressen(xml)
        assert adressen == []
        assert verweise == [
            "https://plattform.invalid/sitemap-aktionen.xml",
            "https://plattform.invalid/sitemap-seiten.xml",
        ]

    def test_folgt_dem_index_eine_ebene(self):
        fetcher = FetcherAttrappe(
            {
                "https://plattform.invalid/sitemap.xml": (
                    FIXTURES / "sitemap_index.xml"
                ).read_text(encoding="utf-8"),
                "https://plattform.invalid/sitemap-aktionen.xml": (
                    FIXTURES / "sitemap_aktionen.xml"
                ).read_text(encoding="utf-8"),
            }
        )
        kandidaten = sitemap.finde("https://plattform.invalid/sitemap.xml", fetcher)
        adressen = {k.url for k in kandidaten}
        assert "https://plattform.invalid/aktion/hoffmanns-reis" in adressen

    def test_muster_filtert(self):
        fetcher = FetcherAttrappe(
            {
                "https://plattform.invalid/sitemap.xml": (
                    FIXTURES / "sitemap_aktionen.xml"
                ).read_text(encoding="utf-8")
            }
        )
        kandidaten = sitemap.finde(
            "https://plattform.invalid/sitemap.xml", fetcher, muster=r"/aktion/"
        )
        adressen = {k.url for k in kandidaten}
        assert "https://plattform.invalid/impressum" not in adressen
        assert len(adressen) == 2

    def test_dubletten_fallen_weg(self):
        fetcher = FetcherAttrappe(
            {
                "https://plattform.invalid/sitemap.xml": (
                    FIXTURES / "sitemap_aktionen.xml"
                ).read_text(encoding="utf-8")
            }
        )
        kandidaten = sitemap.finde("https://plattform.invalid/sitemap.xml", fetcher)
        assert len(kandidaten) == len({k.url for k in kandidaten})

    def test_ungueltiges_muster_stuerzt_nicht_ab(self):
        fetcher = FetcherAttrappe(
            {
                "https://plattform.invalid/sitemap.xml": (
                    FIXTURES / "sitemap_aktionen.xml"
                ).read_text(encoding="utf-8")
            }
        )
        assert sitemap.finde("https://plattform.invalid/sitemap.xml", fetcher, "[") == []

    def test_ausfall_gibt_leere_liste(self):
        assert sitemap.finde("https://x.invalid/sitemap.xml", FetcherAttrappe({})) == []
