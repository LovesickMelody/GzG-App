"""Tests fuer Zusammenfuehren, Schreiben und die stabile Id."""

from __future__ import annotations

import json

from gzg_scraper.models import Action, stable_id
from gzg_scraper.run import fuehre_zusammen, lade_bestand, schreibe_wenn_geaendert


def aktion(titel: str, quelle: str = "portal-a", frist: str | None = "2026-10-14") -> Action:
    return Action(title=titel, source=quelle, brand="Marke", submission_deadline=frist)


class TestStabileId:
    def test_gleiche_angaben_gleiche_id(self):
        assert stable_id("Duschgel", "Nivea", "2026-10-14") == stable_id(
            "Duschgel", "Nivea", "2026-10-14"
        )

    def test_id_ueberlebt_kosmetische_aenderungen(self):
        """Ein zusätzliches Leerzeichen oder andere Schreibweise darf die Id nicht drehen."""
        basis = stable_id("Duschgel Sensitive", "Nivea", "2026-10-14")
        assert stable_id("  Duschgel   Sensitive ", "nivea", "2026-10-14") == basis
        assert stable_id("Duschgel-Sensitive", "NIVEA", "2026-10-14") == basis

    def test_umlaute_und_umschrift_treffen_sich(self):
        assert stable_id("Müller Milch", None, None) == stable_id("Mueller Milch", None, None)

    def test_andere_frist_andere_id(self):
        assert stable_id("Duschgel", "Nivea", "2026-10-14") != stable_id(
            "Duschgel", "Nivea", "2026-11-14"
        )

    def test_id_ist_kurz_und_stabil_geformt(self):
        kennung = stable_id("Duschgel", "Nivea", "2026-10-14")
        assert len(kennung) == 12
        assert kennung.isalnum()


class TestZusammenfuehren:
    def test_uebernimmt_neue_aktionen(self):
        ergebnis = fuehre_zusammen(
            bestand={"actions": []},
            neu_je_quelle={"portal-a": [aktion("Erste"), aktion("Zweite")]},
            ausgefallen=set(),
        )
        assert [e["title"] for e in ergebnis] == ["Erste", "Zweite"]

    def test_behaelt_daten_ausgefallener_quellen(self):
        """Der eigentliche Zweck: ein Portalausfall darf keine Daten löschen."""
        alt = aktion("Alte Aktion", quelle="portal-b").to_json()
        ergebnis = fuehre_zusammen(
            bestand={"actions": [alt]},
            neu_je_quelle={"portal-a": [aktion("Neue Aktion")]},
            ausgefallen={"portal-b"},
        )
        titel = {e["title"] for e in ergebnis}
        assert titel == {"Alte Aktion", "Neue Aktion"}

    def test_ersetzt_daten_erfolgreicher_quellen(self):
        alt = aktion("Verschwundene Aktion").to_json()
        ergebnis = fuehre_zusammen(
            bestand={"actions": [alt]},
            neu_je_quelle={"portal-a": [aktion("Aktuelle Aktion")]},
            ausgefallen=set(),
        )
        assert [e["title"] for e in ergebnis] == ["Aktuelle Aktion"]

    def test_wirft_aktionen_entfernter_quellen_weg(self):
        alt = aktion("Aus alter Quelle", quelle="portal-weg").to_json()
        ergebnis = fuehre_zusammen(
            bestand={"actions": [alt]},
            neu_je_quelle={"portal-a": [aktion("Aktuell")]},
            ausgefallen=set(),
        )
        assert [e["title"] for e in ergebnis] == ["Aktuell"]

    def test_sortiert_deterministisch(self):
        eins = fuehre_zusammen(
            {"actions": []},
            {"b": [aktion("Zebra", "b")], "a": [aktion("Alpha", "a")]},
            set(),
        )
        zwei = fuehre_zusammen(
            {"actions": []},
            {"a": [aktion("Alpha", "a")], "b": [aktion("Zebra", "b")]},
            set(),
        )
        assert eins == zwei

    def test_entfernt_doppelte_ids(self):
        ergebnis = fuehre_zusammen(
            {"actions": []},
            {"portal-a": [aktion("Doppelt"), aktion("Doppelt")]},
            set(),
        )
        assert len(ergebnis) == 1


class TestSchreiben:
    def test_legt_datei_an(self, tmp_path):
        ziel = tmp_path / "actions.json"
        geaendert = schreibe_wenn_geaendert(ziel, [aktion("Erste").to_json()])
        assert geaendert is True
        assert ziel.exists()

        inhalt = json.loads(ziel.read_text(encoding="utf-8"))
        assert inhalt["generated_at"].endswith("Z")
        assert len(inhalt["actions"]) == 1

    def test_schreibt_nicht_ohne_aenderung(self, tmp_path):
        """Sonst gäbe es jeden Tag einen Commit, der nur den Zeitstempel dreht."""
        ziel = tmp_path / "actions.json"
        aktionen = [aktion("Erste").to_json()]

        schreibe_wenn_geaendert(ziel, aktionen)
        vorher = ziel.read_text(encoding="utf-8")

        assert schreibe_wenn_geaendert(ziel, aktionen) is False
        assert ziel.read_text(encoding="utf-8") == vorher

    def test_schreibt_bei_echter_aenderung(self, tmp_path):
        ziel = tmp_path / "actions.json"
        schreibe_wenn_geaendert(ziel, [aktion("Erste").to_json()])
        assert schreibe_wenn_geaendert(ziel, [aktion("Zweite").to_json()]) is True

    def test_umlaute_bleiben_lesbar(self, tmp_path):
        ziel = tmp_path / "actions.json"
        schreibe_wenn_geaendert(ziel, [aktion("Müllermilch für alle").to_json()])
        assert "Müllermilch für alle" in ziel.read_text(encoding="utf-8")

    def test_kaputte_datei_blockiert_nicht(self, tmp_path):
        ziel = tmp_path / "actions.json"
        ziel.write_text("{kein json", encoding="utf-8")
        assert lade_bestand(ziel) == {"generated_at": None, "actions": []}
        assert schreibe_wenn_geaendert(ziel, [aktion("Neu").to_json()]) is True


class TestJsonForm:
    def test_haelt_die_schluesselreihenfolge(self):
        schluessel = list(aktion("Titel").to_json().keys())
        assert schluessel == [
            "id", "title", "brand", "type", "max_refund_cents",
            "valid_from", "valid_to", "submission_deadline", "url", "submit_url",
            "requirements", "retailers", "eans", "image_url",
            "limit_anzahl", "limit_zeitraum", "limit_reset", "limit_erschoepft",
            "source",
        ]

    def test_sortiert_listen(self):
        eintrag = Action(
            title="Titel",
            source="portal",
            retailers=["Rossmann", "dm"],
            eans=["4005900123456", "96385074"],
        ).to_json()
        assert eintrag["retailers"] == ["Rossmann", "dm"]
        assert eintrag["eans"] == ["4005900123456", "96385074"]
