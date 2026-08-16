"""Tests fuer die Erstanbieter-Pipeline: Entdeckung → Extraktion → Pruefung."""

from __future__ import annotations

from datetime import date
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


class TestWiederverwendung:
    """
    Bekannte Kampagnen nicht erneut abrufen und auswerten.

    Ohne das kostet jeder Lauf das Volle: Eine gestern gefundene Kampagne
    bekaeme heute wieder einen Abruf und einen Modellaufruf, obwohl sie
    unveraendert in ``actions.json`` steht.
    """

    HEUTE = date(2026, 8, 15)

    def bestand(self, frist: str = "2026-09-30") -> dict:
        return {
            "actions": [
                {
                    "id": "abc",
                    "title": "Air Wick aus dem Bestand",
                    "source": "justsnap",
                    "url": "https://airwick.justsnap.eu/",
                    "submit_url": "https://airwick.justsnap.eu/teilnehmen",
                    "submission_deadline": frist,
                    "max_refund_cents": 899,
                },
                {
                    "id": "xyz",
                    "title": "Fremde Quelle",
                    "source": "mydealz",
                    "url": "https://airwick.justsnap.eu/",
                    "submission_deadline": frist,
                },
            ]
        }

    def test_verzeichnis_nimmt_nur_die_eigene_quelle(self):
        verzeichnis = erstanbieter.bekannte_adressen(self.bestand(), "justsnap")
        assert all(e["source"] == "justsnap" for e in verzeichnis.values())

    def test_verzeichnis_kennt_beide_adressfelder(self):
        verzeichnis = erstanbieter.bekannte_adressen(self.bestand(), "justsnap")
        assert "airwick.justsnap.eu" in verzeichnis
        assert "airwick.justsnap.eu/teilnehmen" in verzeichnis

    def test_bekannte_aktion_wird_nicht_abgerufen(self):
        fetcher = fetcher_mit({"https://airwick.justsnap.eu/": AKTION})
        aktionen = erstanbieter.sammle(
            {**QUELLE, "auffrischen_tage": 3650},
            fetcher,
            bekannt=erstanbieter.bekannte_adressen(self.bestand(), "justsnap"),
            heute=self.HEUTE,
        )
        assert aktionen is not None
        assert [a.title for a in aktionen] == ["Air Wick aus dem Bestand"]
        assert "https://airwick.justsnap.eu/" not in fetcher.abrufe

    def test_abgelaufene_bekannte_aktion_wird_neu_gelesen(self):
        """Sonst schleppt der Feed eine tote Kampagne ewig mit."""
        fetcher = fetcher_mit({"https://airwick.justsnap.eu/": AKTION})
        erstanbieter.sammle(
            {**QUELLE, "auffrischen_tage": 3650},
            fetcher,
            bekannt=erstanbieter.bekannte_adressen(
                self.bestand(frist="2026-07-01"), "justsnap"
            ),
            heute=self.HEUTE,
        )
        assert "https://airwick.justsnap.eu/" in fetcher.abrufe

    def test_ohne_frist_wird_neu_gelesen(self):
        ohne_frist = {"actions": [
            {"title": "X", "source": "justsnap", "url": "https://airwick.justsnap.eu/"}
        ]}
        fetcher = fetcher_mit({"https://airwick.justsnap.eu/": AKTION})
        erstanbieter.sammle(
            {**QUELLE, "auffrischen_tage": 3650},
            fetcher,
            bekannt=erstanbieter.bekannte_adressen(ohne_frist, "justsnap"),
            heute=self.HEUTE,
        )
        assert "https://airwick.justsnap.eu/" in fetcher.abrufe

    def test_auffrischen_ist_stabil_und_verteilt(self):
        """
        Dieselbe Adresse trifft immer denselben Tag — sonst waere die
        Wiederverwendung zufaellig. Und nicht alle treffen denselben, sonst
        faellt die ganze Last an einem Tag an.
        """
        adressen = [f"https://marke{i}.justsnap.eu/" for i in range(40)]
        faellig = {
            a: [
                erstanbieter._auffrischen_faellig(a, date.fromordinal(o), 7)
                for o in range(self.HEUTE.toordinal(), self.HEUTE.toordinal() + 7)
            ]
            for a in adressen
        }
        # Genau ein Tag je Woche und Adresse.
        assert all(sum(tage) == 1 for tage in faellig.values())
        # Zweimal derselbe Tag fuer dieselbe Adresse.
        for a in adressen:
            assert erstanbieter._auffrischen_faellig(
                a, self.HEUTE, 7
            ) == erstanbieter._auffrischen_faellig(a, self.HEUTE, 7)
        # Und nicht alle am selben Tag.
        erste_tage = {tage.index(True) for tage in faellig.values()}
        assert len(erste_tage) > 1

    def test_ohne_bestand_laeuft_alles_wie_bisher(self):
        fetcher = fetcher_mit({"https://airwick.justsnap.eu/": AKTION})
        aktionen = erstanbieter.sammle(QUELLE, fetcher, heute=self.HEUTE)
        assert aktionen is not None and len(aktionen) == 1
        assert "https://airwick.justsnap.eu/" in fetcher.abrufe


class TestUntergeschobenerLink:
    """
    Die Regel muss in der Pipeline scharf sein, nicht nur in pruefung.py.

    Eine Kampagnenseite kann im Text behaupten, wo eingereicht wird — und die
    App füllt dort auf Knopfdruck IBAN und Anschrift ins Formular.
    """

    def test_fremdes_ziel_faellt_durch(self):
        untergeschoben = AKTION.replace(
            'href="https://airwick.justsnap.invalid/teilnehmen"',
            'href="https://boeses.invalid/formular"',
        ).replace(
            '"@type": "Product"',
            '"@type": "Product", "url": "https://boeses.invalid/formular"',
        )
        fetcher = fetcher_mit({"https://airwick.justsnap.eu/": untergeschoben})
        aktionen = erstanbieter.sammle(QUELLE, fetcher)
        # Der Einreichungslink zeigt nach aussen — entweder faellt die Aktion
        # durch, oder er wurde gar nicht erst uebernommen. Beides ist in
        # Ordnung; was nicht sein darf, ist ein fremdes Ziel im Ergebnis.
        assert aktionen is not None
        for a in aktionen:
            assert "boeses.invalid" not in (a.submit_url or "")
