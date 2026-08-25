"""
Tests fuer das Abwicklerverzeichnis.

Der Weg ist entstanden, nachdem die Entdeckung ueber Zertifikatsprotokolle
ausfiel: crt.sh und certspotter verbieten den Abruf beide per robots.txt, und
eine Kampagnenuebersicht veroeffentlicht ein Abwickler nicht. Uebrig blieb, was
uns die Portale ohnehin sagen — die Adresse beim Abwickler.
"""

from __future__ import annotations

import json

from gzg_scraper.discovery import gelernt
from gzg_scraper.models import Action


def aktion(submit_url: str | None, titel: str = "Testaktion") -> Action:
    return Action(title=titel, source="mydealz", submit_url=submit_url)


class TestLernen:
    def test_lernt_den_host_einer_aktion(self):
        verzeichnis = gelernt.lerne(
            {}, [aktion("https://airwick.justsnap.eu/de/aktion")], heute="2026-08-25"
        )
        assert "airwick.justsnap.eu" in verzeichnis
        eintrag = verzeichnis["airwick.justsnap.eu"]
        assert eintrag.adresse == "https://airwick.justsnap.eu/de/aktion"
        assert eintrag.zuerst_gesehen == "2026-08-25"

    def test_behaelt_das_erste_datum(self):
        alt = gelernt.lerne({}, [aktion("https://a.example/eins")], heute="2026-08-01")
        neu = gelernt.lerne(alt, [aktion("https://a.example/zwei")], heute="2026-08-25")
        eintrag = neu["a.example"]
        assert eintrag.zuerst_gesehen == "2026-08-01"
        assert eintrag.zuletzt_gesehen == "2026-08-25"
        # Die neuere Kampagne unter demselben Host ist die interessantere.
        assert eintrag.adresse == "https://a.example/zwei"

    def test_lernt_portale_und_cashback_plattformen_nicht(self):
        # Dort steht keine einzelne Aktion, sondern eine App-Anmeldung.
        verzeichnis = gelernt.lerne(
            {},
            [
                aktion("https://www.mydealz.de/deals/irgendwas-123"),
                aktion("https://www.marktguru.de/mb/cashback-6818"),
                aktion("https://scondoo.de/?cashbackDetail=81788"),
            ],
            heute="2026-08-25",
        )
        assert verzeichnis == {}

    def test_ignoriert_aktionen_ohne_adresse(self):
        assert gelernt.lerne({}, [aktion(None)], heute="2026-08-25") == {}

    def test_ignoriert_unsinnige_adressen(self):
        verzeichnis = gelernt.lerne(
            {},
            [aktion("javascript:alert(1)"), aktion("nur-text"), aktion("mailto:a@b.de")],
            heute="2026-08-25",
        )
        assert verzeichnis == {}

    def test_veraendert_das_uebergebene_verzeichnis_nicht(self):
        alt = gelernt.lerne({}, [aktion("https://a.example/eins")], heute="2026-08-01")
        gelernt.lerne(alt, [aktion("https://b.example/zwei")], heute="2026-08-25")
        assert set(alt) == {"a.example"}

    def test_versteht_auch_dicts(self):
        # Nach dem Zusammenfuehren liegen die Aktionen als JSON-Dicts vor.
        verzeichnis = gelernt.lerne(
            {}, [{"submit_url": "https://c.example/x"}], heute="2026-08-25"
        )
        assert "c.example" in verzeichnis


class TestSchreibenUndLesen:
    def test_schreibt_und_liest_zurueck(self, tmp_path):
        pfad = tmp_path / "abwickler.json"
        verzeichnis = gelernt.lerne({}, [aktion("https://a.example/x")], heute="2026-08-25")

        assert gelernt.schreibe_verzeichnis(pfad, verzeichnis) is True
        zurueck = gelernt.lies_verzeichnis(pfad)
        assert zurueck["a.example"].adresse == "https://a.example/x"

    def test_schreibt_nicht_wenn_sich_nur_der_zeitstempel_dreht(self, tmp_path):
        """Sonst gäbe es jeden Tag einen Commit ohne inhaltliche Änderung."""
        pfad = tmp_path / "abwickler.json"
        gelernt.schreibe_verzeichnis(
            pfad, gelernt.lerne({}, [aktion("https://a.example/x")], heute="2026-08-01")
        )
        vorher = gelernt.lies_verzeichnis(pfad)

        nachher = gelernt.lerne(vorher, [aktion("https://a.example/x")], heute="2026-08-25")
        assert gelernt.schreibe_verzeichnis(pfad, nachher) is False

    def test_schreibt_bei_einem_neuen_host(self, tmp_path):
        pfad = tmp_path / "abwickler.json"
        gelernt.schreibe_verzeichnis(
            pfad, gelernt.lerne({}, [aktion("https://a.example/x")], heute="2026-08-01")
        )
        erweitert = gelernt.lerne(
            gelernt.lies_verzeichnis(pfad), [aktion("https://b.example/y")], heute="2026-08-25"
        )
        assert gelernt.schreibe_verzeichnis(pfad, erweitert) is True

    def test_kaputte_datei_blockiert_nicht(self, tmp_path):
        pfad = tmp_path / "abwickler.json"
        pfad.write_text("{kein json", encoding="utf-8")
        assert gelernt.lies_verzeichnis(pfad) == {}

    def test_fehlende_datei_ist_kein_fehler(self, tmp_path):
        assert gelernt.lies_verzeichnis(tmp_path / "gibtsnicht.json") == {}

    def test_sortiert_stabil(self, tmp_path):
        """Sonst wechselt die Reihenfolge und jeder Lauf erzeugt einen Diff."""
        pfad = tmp_path / "abwickler.json"
        verzeichnis = gelernt.lerne(
            {},
            [aktion("https://z.example/x"), aktion("https://a.example/y")],
            heute="2026-08-25",
        )
        gelernt.schreibe_verzeichnis(pfad, verzeichnis)
        hosts = [e["host"] for e in json.loads(pfad.read_text(encoding="utf-8"))["abwickler"]]
        assert hosts == sorted(hosts)


class TestKandidaten:
    def test_gibt_die_gelernten_adressen_zurueck(self, tmp_path):
        pfad = tmp_path / "abwickler.json"
        gelernt.schreibe_verzeichnis(
            pfad,
            gelernt.lerne(
                {},
                [aktion("https://a.example/x"), aktion("https://b.example/y")],
                heute="2026-08-25",
            ),
        )
        kandidaten = gelernt.finde(pfad, quellenname="abwickler")
        assert {k.url for k in kandidaten} == {"https://a.example/x", "https://b.example/y"}
        assert all(k.entdeckt_ueber.startswith("abwickler:") for k in kandidaten)

    def test_ohne_datei_keine_kandidaten(self, tmp_path):
        assert gelernt.finde(tmp_path / "gibtsnicht.json") == []


class TestAnmeldeseiten:
    """
    Eine Anmeldeseite ist keine Kampagnenseite.

    Aufgefallen beim ersten Lauf gegen die echten Daten: ForMe fuehrt ueber
    `konto.for-me-online.de/u/login?state=hKFo2SBPdl9…` — dahinter steht ein
    Formular, und die Adresse traegt einen Sitzungsschluessel, der in einem
    oeffentlichen Repo nichts zu suchen hat.
    """

    def test_lernt_keine_anmeldeseite(self):
        verzeichnis = gelernt.lerne(
            {},
            [aktion("https://konto.for-me-online.de/u/login?state=hKFo2SBPdl9BSzg0eVVT")],
            heute="2026-08-25",
        )
        assert verzeichnis == {}

    def test_erkennt_die_gaengigen_schreibweisen(self):
        for pfad in ("/login", "/signin", "/sign-in", "/anmelden", "/auth/start", "/mein-konto"):
            verzeichnis = gelernt.lerne(
                {}, [aktion(f"https://a.example{pfad}")], heute="2026-08-25"
            )
            assert verzeichnis == {}, f"{pfad} sollte nicht gelernt werden"

    def test_eine_echte_kampagnenseite_bleibt(self):
        verzeichnis = gelernt.lerne(
            {}, [aktion("https://andros-be-nuts.de/pages/gratistesten")], heute="2026-08-25"
        )
        assert "andros-be-nuts.de" in verzeichnis
