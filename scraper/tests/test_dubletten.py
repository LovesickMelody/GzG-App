"""Zusammenfassen derselben Aktion aus mehreren Portalen."""

from __future__ import annotations

from gzg_scraper.run import fasse_dubletten_zusammen


def eintrag(**felder):
    grund = {
        "id": "a1",
        "title": "Bonduelle Frische Salate",
        "brand": None,
        "type": "gratis_testen",
        "max_refund_cents": None,
        "valid_from": None,
        "valid_to": None,
        "submission_deadline": None,
        "url": None,
        "submit_url": None,
        "requirements": [],
        "retailers": [],
        "eans": [],
        "image_url": None,
        "source": "quelle-a",
    }
    return {**grund, **felder}


class TestZusammenfassen:
    def test_gleiche_einreichungsadresse_wird_zusammengefasst(self):
        eintraege = [
            eintrag(id="a1", submit_url="https://scondoo.de/?cashbackDetail=81788"),
            eintrag(id="b1", source="quelle-b", submit_url="https://scondoo.de?cashbackDetail=81788"),
        ]
        assert len(fasse_dubletten_zusammen(eintraege)) == 1

    def test_ergaenzt_fehlende_felder_aus_der_anderen_quelle(self):
        # Die eine Quelle kennt die Frist, die andere die Bedingungen.
        eintraege = [
            eintrag(
                id="a1",
                submit_url="https://scondoo.de/x",
                requirements=["bonfoto", "app"],
            ),
            eintrag(
                id="b1",
                source="quelle-b",
                submit_url="https://scondoo.de/x",
                submission_deadline="2026-08-30",
                brand="Bonduelle",
            ),
        ]
        [zusammen] = fasse_dubletten_zusammen(eintraege)
        assert zusammen["requirements"] == ["bonfoto", "app"]
        assert zusammen["submission_deadline"] == "2026-08-30"
        assert zusammen["brand"] == "Bonduelle"

    def test_vereinigt_haendler_und_eans(self):
        eintraege = [
            eintrag(id="a1", submit_url="https://x.de/a", retailers=["Rewe"], eans=["4005900123459"]),
            eintrag(id="b1", source="quelle-b", submit_url="https://x.de/a", retailers=["Edeka"]),
        ]
        [zusammen] = fasse_dubletten_zusammen(eintraege)
        assert zusammen["retailers"] == ["Edeka", "Rewe"]
        assert zusammen["eans"] == ["4005900123459"]

    def test_die_quelle_bleibt_einfach(self):
        # Die App raeumt je Quelle auf; ein Wert wie "a+b" wuerde dabei nie
        # wieder getroffen und der Eintrag bliebe ewig stehen.
        eintraege = [
            eintrag(id="a1", submit_url="https://x.de/a", brand="Marke"),
            eintrag(id="b1", source="quelle-b", submit_url="https://x.de/a"),
        ]
        [zusammen] = fasse_dubletten_zusammen(eintraege)
        assert zusammen["source"] in ("quelle-a", "quelle-b")
        assert "+" not in zusammen["source"]

    def test_ohne_einreichungsadresse_wird_nichts_zusammengeworfen(self):
        # Nur identische Adressen zaehlen. Aehnliche Titel zu vergleichen wuerde
        # mal richtig und mal falsch zusammenwerfen.
        eintraege = [
            eintrag(id="a1", title="Bonduelle Frische Salate"),
            eintrag(id="b1", source="quelle-b", title="Bonduelle Salat Gratis Testen"),
        ]
        assert len(fasse_dubletten_zusammen(eintraege)) == 2

    def test_verschiedene_adressen_bleiben_getrennt(self):
        eintraege = [
            eintrag(id="a1", submit_url="https://scondoo.de/?cashbackDetail=1"),
            eintrag(id="b1", source="quelle-b", submit_url="https://scondoo.de/?cashbackDetail=2"),
        ]
        assert len(fasse_dubletten_zusammen(eintraege)) == 2

    def test_der_vollstaendigere_eintrag_ist_die_grundlage(self):
        mager = eintrag(id="a1", submit_url="https://x.de/a")
        reich = eintrag(
            id="b1",
            source="quelle-b",
            submit_url="https://x.de/a",
            brand="Marke",
            submission_deadline="2026-09-01",
            image_url="https://x.de/bild.jpg",
        )
        [zusammen] = fasse_dubletten_zusammen([mager, reich])
        assert zusammen["id"] == "b1"

    def test_ist_zwischen_zwei_laeufen_stabil(self):
        # Wechselte der Gewinner, fuehrte die App dieselbe Aktion staendig als neu.
        a = eintrag(id="a1", submit_url="https://x.de/a")
        b = eintrag(id="b1", source="quelle-b", submit_url="https://x.de/a")
        assert fasse_dubletten_zusammen([a, b]) == fasse_dubletten_zusammen([b, a])
