"""Tests fuer die Entdeckung: Zertifikatsprotokolle und Sitemaps."""

from __future__ import annotations

from pathlib import Path

from gzg_scraper.discovery import ct_logs, sitemap

FIXTURES = Path(__file__).parent / "fixtures"
CRT = (FIXTURES / "crt_justsnap.json").read_text(encoding="utf-8")
CERTSPOTTER = (FIXTURES / "certspotter_justsnap.json").read_text(encoding="utf-8")


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
        kandidaten = ct_logs.finde("justsnap.eu", fetcher, "crt.sh")
        assert len(kandidaten) == 2
        assert fetcher.abrufe == [adresse]

    def test_ausfall_gibt_leere_liste(self):
        assert ct_logs.finde("justsnap.eu", FetcherAttrappe({}), "crt.sh") == []


class TestCertspotter:
    """
    Der Standardweg zu denselben Protokollen.

    crt.sh verbietet den Abruf per robots.txt — der erste Probelauf lief
    deshalb ins Leere, ohne eine einzige Kampagne zu finden.
    """

    def test_findet_dieselben_kampagnen_wie_crt_sh(self):
        """Gleiche Zertifikatslage, gleiches Ergebnis — nur anderes Format."""
        ueber_certspotter = {
            k.url for k in ct_logs.lies_certspotter(CERTSPOTTER, "justsnap.eu")
        }
        ueber_crt = {k.url for k in ct_logs.lies_antwort(CRT, "justsnap.eu")}
        assert ueber_certspotter == ueber_crt == {
            "https://airwick.justsnap.eu/",
            "https://hoffmanns.justsnap.eu/",
        }

    def test_fruehester_zeitpunkt_gewinnt(self):
        kandidaten = {
            k.url: k for k in ct_logs.lies_certspotter(CERTSPOTTER, "justsnap.eu")
        }
        airwick = kandidaten["https://airwick.justsnap.eu/"]
        assert airwick.zuerst_gesehen == "2026-04-02T08:14:32Z"

    def test_kaputte_antwort(self):
        assert ct_logs.lies_certspotter("{kein array", "justsnap.eu") == []
        assert ct_logs.lies_certspotter(None, "justsnap.eu") == []
        assert ct_logs.lies_certspotter("[]", "justsnap.eu") == []

    def test_eintrag_ohne_namensliste_stoert_nicht(self):
        assert ct_logs.lies_certspotter('[{"id":"1"}]', "justsnap.eu") == []

    def test_ist_der_standardweg(self):
        """Ohne ausdrückliche Angabe geht die Abfrage an certspotter."""
        erste = ct_logs.CERTSPOTTER.format("justsnap.eu")
        fetcher = FetcherAttrappe({erste: CERTSPOTTER})
        kandidaten = ct_logs.finde("justsnap.eu", fetcher)
        assert len(kandidaten) == 2
        assert fetcher.abrufe[0] == erste

    def test_folgt_der_seitenaufteilung(self):
        """
        Die API liefert höchstens 100 Einträge je Abruf. Ohne Weiterblättern
        fehlten bei einer großen Plattform genau die Kampagnen, die den
        Ausschlag geben.
        """
        erste = ct_logs.CERTSPOTTER.format("justsnap.eu")
        zweite = f"{erste}&after=9210000006"
        seite2 = (
            '[{"id":"9210000007","dns_names":["persil.justsnap.eu"],'
            '"not_before":"2026-08-10T00:00:00Z"}]'
        )
        dritte = f"{erste}&after=9210000007"
        fetcher = FetcherAttrappe({erste: CERTSPOTTER, zweite: seite2, dritte: "[]"})

        adressen = {k.url for k in ct_logs.finde("justsnap.eu", fetcher)}
        assert "https://persil.justsnap.eu/" in adressen
        assert fetcher.abrufe == [erste, zweite, dritte]

    def test_bricht_nach_der_obergrenze_ab(self, caplog):
        """Abschneiden ja — still abschneiden nein."""
        erste = ct_logs.CERTSPOTTER.format("justsnap.eu")

        class ImmerVoll:
            abrufe: list[str] = []

            def hole(self, url, still=False):
                self.abrufe.append(url)
                nummer = 9000000 + len(self.abrufe)
                return (
                    f'[{{"id":"{nummer}","dns_names":["a{nummer}.justsnap.eu"],'
                    '"not_before":"2026-08-10T00:00:00Z"}]'
                )

        fetcher = ImmerVoll()
        with caplog.at_level("WARNING"):
            ct_logs.finde("justsnap.eu", fetcher)

        assert len(fetcher.abrufe) == ct_logs.MAX_SEITEN
        assert "abgebrochen" in caplog.text
        assert erste in fetcher.abrufe[0]

    def test_ausfall_gibt_leere_liste(self):
        assert ct_logs.finde("justsnap.eu", FetcherAttrappe({})) == []


class TestLeeresErgebnisErklaertSich:
    """
    "2 Einträge → 0 Kampagnen" ist keine Antwort.

    Dahinter stecken drei sehr verschiedene Fälle: Die Plattform hat gerade
    keine Kampagne, sie benutzt ein Platzhalterzertifikat (dann taucht keine
    Kampagne je einzeln auf), oder unser Filter ist zu streng. Unterscheiden
    lässt sich das nur an den verworfenen Namen — also gehören sie ins Log.
    """

    def test_nennt_die_verworfenen_namen(self, caplog):
        nur_platzhalter = (
            '[{"id":"1","dns_names":["*.justsnap.eu","justsnap.eu"],'
            '"not_before":"2026-08-01T00:00:00Z"}]'
        )
        with caplog.at_level("INFO"):
            assert ct_logs.lies_certspotter(nur_platzhalter, "justsnap.eu") == []

        assert "*.justsnap.eu" in caplog.text
        assert "justsnap.eu" in caplog.text

    def test_schweigt_wenn_etwas_gefunden_wurde(self, caplog):
        with caplog.at_level("INFO"):
            ct_logs.lies_certspotter(CERTSPOTTER, "justsnap.eu")
        assert "nichts übrig" not in caplog.text

    def test_nennt_den_abgedeckten_zeitraum(self, caplog):
        """
        Ohne den Zeitraum ist "wenige Einträge" nicht von "nur ein kleines
        Zeitfenster geliefert bekommen" zu unterscheiden.
        """
        with caplog.at_level("INFO"):
            ct_logs.lies_certspotter(CERTSPOTTER, "justsnap.eu")
        assert "2026-01-05T00:00:00Z bis 2026-08-01T10:02:09Z" in caplog.text


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
