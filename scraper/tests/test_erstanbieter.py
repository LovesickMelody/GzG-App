"""Tests fuer die Erstanbieter-Pipeline: Entdeckung → Extraktion → Pruefung."""

from __future__ import annotations

from pathlib import Path

from gzg_scraper import erstanbieter

FIXTURES = Path(__file__).parent / "fixtures"
CRT = (FIXTURES / "crt_justsnap.json").read_text(encoding="utf-8")
AKTION = (FIXTURES / "aktion_jsonld.html").read_text(encoding="utf-8")

CRT_ADRESSE = "https://crt.sh/?q=%25.justsnap.eu&output=json"

QUELLE = {"name": "justsnap", "parser": "erstanbieter", "ct_logs": ["justsnap.eu"]}


class FetcherAttrappe:
    def __init__(self, antworten: dict[str, str]):
        self.antworten = antworten
        self.abrufe: list[str] = []

    def hole(self, url: str, still: bool = False) -> str | None:
        self.abrufe.append(url)
        return self.antworten.get(url)

    def hole_seite(self, url: str, still: bool = False):
        inhalt = self.hole(url)
        return None if inhalt is None else (inhalt, url)


def fetcher_mit(seiten: dict[str, str]) -> FetcherAttrappe:
    return FetcherAttrappe({CRT_ADRESSE: CRT, **seiten})


class TestEntdeckung:
    def test_fasst_entdecker_zusammen(self):
        kandidaten = erstanbieter.entdecke(QUELLE, fetcher_mit({}))
        assert {k.url for k in kandidaten} == {
            "https://airwick.justsnap.eu/",
            "https://hoffmanns.justsnap.eu/",
        }

    def test_neueste_zuerst(self):
        """Greift die Obergrenze, sollen die frischen Kampagnen drin sein."""
        kandidaten = erstanbieter.entdecke(QUELLE, fetcher_mit({}))
        assert kandidaten[0].url == "https://hoffmanns.justsnap.eu/"

    def test_dubletten_aus_zwei_entdeckern(self):
        quelle = {
            **QUELLE,
            "sitemaps": [{"url": "https://plattform.invalid/sitemap.xml"}],
        }
        fetcher = fetcher_mit(
            {
                "https://plattform.invalid/sitemap.xml": (
                    '<?xml version="1.0"?><urlset '
                    'xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">'
                    "<url><loc>https://airwick.justsnap.eu/</loc></url></urlset>"
                )
            }
        )
        kandidaten = erstanbieter.entdecke(quelle, fetcher)
        assert len(kandidaten) == len({k.url for k in kandidaten})


class TestSammeln:
    def test_findet_die_aktion(self):
        fetcher = fetcher_mit(
            {
                "https://airwick.justsnap.eu/": AKTION,
                "https://hoffmanns.justsnap.eu/": "<html><body>Nichts</body></html>",
            }
        )
        aktionen = erstanbieter.sammle(QUELLE, fetcher)
        assert aktionen is not None and len(aktionen) == 1
        assert aktionen[0].title == "Air Wick Duftöl Starter-Set gratis testen"
        assert aktionen[0].max_refund_cents == 899

    def test_ohne_kandidaten_gilt_die_quelle_als_ausgefallen(self):
        """``None`` heisst: alter Stand bleibt stehen — wie bei Portalausfall."""
        assert erstanbieter.sammle(QUELLE, FetcherAttrappe({})) is None

    def test_kandidaten_ohne_aktion_liefern_leere_liste(self):
        fetcher = fetcher_mit(
            {
                "https://airwick.justsnap.eu/": "<html><body>Bald hier</body></html>",
                "https://hoffmanns.justsnap.eu/": "<html><body>Bald hier</body></html>",
            }
        )
        assert erstanbieter.sammle(QUELLE, fetcher) == []

    def test_vorbehalt_ueberspringt_die_seite(self):
        mit_vorbehalt = AKTION.replace(
            "<body>", "<body><p>Text und Data Mining ist untersagt.</p>"
        )
        fetcher = fetcher_mit({"https://airwick.justsnap.eu/": mit_vorbehalt})
        assert erstanbieter.sammle(QUELLE, fetcher) == []

    def test_nicht_gestartete_aktion_wird_nicht_veroeffentlicht(self):
        """
        Der Vorab-Leak: Das Zertifikat existiert, die Kampagne startet erst.
        Anzeigen wuerde die Planung des Herstellers verraten.
        """
        kuenftig = AKTION.replace('"validFrom": "2026-08-01"', '"validFrom": "2099-01-01"')
        fetcher = fetcher_mit({"https://airwick.justsnap.eu/": kuenftig})
        assert erstanbieter.sammle(QUELLE, fetcher) == []

    def test_obergrenze_wird_eingehalten(self):
        quelle = {**QUELLE, "max_kandidaten": 1}
        fetcher = fetcher_mit(
            {
                "https://airwick.justsnap.eu/": AKTION,
                "https://hoffmanns.justsnap.eu/": AKTION,
            }
        )
        erstanbieter.sammle(quelle, fetcher)
        seitenabrufe = [a for a in fetcher.abrufe if a.endswith(".justsnap.eu/")]
        assert len(seitenabrufe) == 1

    def test_unerreichbare_seite_kippt_den_lauf_nicht(self):
        fetcher = fetcher_mit({"https://airwick.justsnap.eu/": AKTION})
        aktionen = erstanbieter.sammle(QUELLE, fetcher)
        assert aktionen is not None and len(aktionen) == 1
