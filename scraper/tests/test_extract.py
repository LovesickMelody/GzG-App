"""Tests fuer die generische Extraktion: JSON-LD, Modell und ihr Zusammenspiel."""

from __future__ import annotations

import json
from pathlib import Path

from gzg_scraper.extract import extrahiere, sichtbarer_text
from gzg_scraper.extract import jsonld
from gzg_scraper.extract.modell import Modellextraktor, baue_aktion

FIXTURES = Path(__file__).parent / "fixtures"
MIT_JSONLD = (FIXTURES / "aktion_jsonld.html").read_text(encoding="utf-8")
OHNE_JSONLD = (FIXTURES / "aktion_ohne_jsonld.html").read_text(encoding="utf-8")


class TestJsonLd:
    def test_liest_die_eckdaten(self):
        aktion = jsonld.lies(MIT_JSONLD, "justsnap", "https://x.invalid/a")
        assert aktion is not None
        assert aktion.title == "Air Wick Duftöl Starter-Set gratis testen"
        assert aktion.brand == "Air Wick"
        assert aktion.submission_deadline == "2026-09-30"
        assert aktion.valid_from == "2026-08-01"
        assert aktion.image_url == "https://cdn.example.invalid/airwick-set.jpg"

    def test_uebernimmt_den_preis_nicht(self):
        """
        Der JSON-LD-Preis ist der Ladenpreis, nicht die Erstattung. Bei einer
        gedeckelten Aktion waeren wir damit nach oben daneben.
        """
        aktion = jsonld.lies(MIT_JSONLD, "justsnap", "https://x.invalid/a")
        assert aktion is not None and aktion.max_refund_cents is None

    def test_seite_ohne_jsonld(self):
        assert jsonld.lies(OHNE_JSONLD, "x", "https://x.invalid/a") is None

    def test_kaputtes_jsonld_stuerzt_nicht_ab(self):
        html = '<html><script type="application/ld+json">{kaputt</script></html>'
        assert jsonld.lies(html, "x", "https://x.invalid/a") is None

    def test_nur_titel_ist_zu_duenn(self):
        html = (
            '<html><script type="application/ld+json">'
            '{"@type": "Product", "name": "Irgendwas"}</script></html>'
        )
        assert jsonld.lies(html, "x", "https://x.invalid/a") is None

    def test_graph_und_liste_werden_aufgefaltet(self):
        html = (
            '<html><script type="application/ld+json">'
            '{"@graph": [{"@type": "Organization", "name": "Firma"},'
            ' {"@type": "Offer", "name": "Reis gratis", "validThrough": "2026-10-14"}]}'
            "</script></html>"
        )
        aktion = jsonld.lies(html, "x", "https://x.invalid/a")
        assert aktion is not None and aktion.submission_deadline == "2026-10-14"


class TestModellantwort:
    """
    Geprueft wird der Schritt *nach* dem Modell: Aus Zitaten werden Werte, und
    zwar mit unseren eigenen Parsern.
    """

    def test_baut_aktion_aus_zitaten(self):
        aktion = baue_aktion(
            {
                "ist_aktion": True,
                "titel": "Hoffmann's Reis gratis testen",
                "marke": "Hoffmann's",
                "betrag_zitat": "2,49 €",
                "frist_zitat": "Teilnahmeschluss: 14.10.2026",
                "bedingungen": "Kassenbon hochladen und Bankverbindung angeben.",
                "haendler": ["dm"],
            },
            "https://x.invalid/a",
            "plattform",
        )
        assert aktion is not None
        assert aktion.max_refund_cents == 249
        assert aktion.submission_deadline == "2026-10-14"
        assert aktion.requirements == ["bonfoto", "iban"]
        assert aktion.retailers == ["dm"]

    def test_keine_aktion_wird_verworfen(self):
        assert (
            baue_aktion(
                {"ist_aktion": False, "titel": "Übersicht aller Aktionen"},
                "https://x.invalid/",
                "plattform",
            )
            is None
        )

    def test_fuellmenge_wird_kein_betrag(self):
        """
        Das Modell zitiert, unser Parser entscheidet. 'medium+ lemon 0,75l' hat
        beim ersten echten Lauf 0,75 € ergeben — genau dagegen ist das hier.
        """
        aktion = baue_aktion(
            {"ist_aktion": True, "titel": "Test", "betrag_zitat": "0,75 l"},
            "https://x.invalid/a",
            "plattform",
        )
        assert aktion is not None and aktion.max_refund_cents is None

    def test_datum_wird_kein_betrag(self):
        aktion = baue_aktion(
            {"ist_aktion": True, "titel": "Test", "betrag_zitat": "30.08.2026"},
            "https://x.invalid/a",
            "plattform",
        )
        assert aktion is not None and aktion.max_refund_cents is None

    def test_ohne_titel_verworfen(self):
        assert (
            baue_aktion({"ist_aktion": True, "titel": "  "}, "https://x/", "p") is None
        )

    def test_link_faellt_auf_die_seite_zurueck(self):
        aktion = baue_aktion(
            {"ist_aktion": True, "titel": "Test", "einreichungslink": None},
            "https://x.invalid/a",
            "plattform",
        )
        assert aktion is not None and aktion.submit_url == "https://x.invalid/a"


class ClientAttrappe:
    """Steht fuer ``anthropic.Anthropic`` — die Tests brauchen kein Netz."""

    def __init__(self, nutzlast: dict, stop_reason: str = "end_turn"):
        self.nutzlast = nutzlast
        self.stop_reason = stop_reason
        self.aufrufe: list[dict] = []
        self.messages = self

    def create(self, **argumente):
        self.aufrufe.append(argumente)

        class Block:
            type = "text"
            text = json.dumps(self.nutzlast, ensure_ascii=False)

        class Antwort:
            content = [Block()]
            stop_reason = self.stop_reason

        return Antwort()


class TestModellextraktor:
    def test_ohne_schluessel_kein_aufruf(self, monkeypatch):
        """Die CI läuft ohne Schlüssel — und muss trotzdem grün sein."""
        monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
        extraktor = Modellextraktor()
        assert extraktor.lies("Irgendein Text", "https://x.invalid/", "p") is None

    def test_liest_ueber_die_attrappe(self):
        client = ClientAttrappe(
            {
                "ist_aktion": True,
                "titel": "Hoffmann's Reis gratis testen",
                "betrag_zitat": "2,49 €",
                "frist_zitat": "14.10.2026",
            }
        )
        aktion = Modellextraktor(client=client).lies(
            sichtbarer_text(OHNE_JSONLD), "https://x.invalid/a", "plattform"
        )
        assert aktion is not None and aktion.max_refund_cents == 249

    def test_ablehnung_wird_nicht_ausgewertet(self):
        client = ClientAttrappe({"ist_aktion": True, "titel": "X"}, stop_reason="refusal")
        assert Modellextraktor(client=client).lies("Text", "https://x/", "p") is None

    def test_schickt_schema_und_effort_mit(self):
        client = ClientAttrappe({"ist_aktion": False, "titel": "X"})
        Modellextraktor(client=client, effort="low").lies("Text", "https://x/", "p")
        argumente = client.aufrufe[0]
        assert argumente["output_config"]["effort"] == "low"
        assert argumente["output_config"]["format"]["type"] == "json_schema"

    def test_leerer_text_ruft_nicht_auf(self):
        client = ClientAttrappe({"ist_aktion": True, "titel": "X"})
        assert Modellextraktor(client=client).lies("   ", "https://x/", "p") is None
        assert client.aufrufe == []


class TestZusammenspiel:
    def test_jsonld_hat_vorrang_vor_dem_modell(self):
        client = ClientAttrappe({"ist_aktion": True, "titel": "Vom Modell"})
        aktion = extrahiere(
            MIT_JSONLD, "https://x.invalid/a", "justsnap", Modellextraktor(client=client)
        )
        assert aktion is not None
        assert aktion.title == "Air Wick Duftöl Starter-Set gratis testen"
        assert client.aufrufe == [], "Modell darf gar nicht erst gefragt werden"

    def test_betrag_kommt_aus_dem_seitentext(self):
        """JSON-LD liefert keinen Erstattungsbetrag — der Text schon."""
        aktion = extrahiere(MIT_JSONLD, "https://x.invalid/a", "justsnap")
        assert aktion is not None and aktion.max_refund_cents == 899

    def test_anforderungen_kommen_aus_dem_seitentext(self):
        aktion = extrahiere(MIT_JSONLD, "https://x.invalid/a", "justsnap")
        assert aktion is not None
        assert "bonfoto" in aktion.requirements
        assert "zusammen_fotografieren" in aktion.requirements
        assert "handy_verifizierung" in aktion.requirements

    def test_ohne_jsonld_und_ohne_modell_nichts(self):
        assert extrahiere(OHNE_JSONLD, "https://x.invalid/a", "p") is None

    def test_modell_springt_ein(self):
        client = ClientAttrappe(
            {"ist_aktion": True, "titel": "Hoffmann's Reis", "betrag_zitat": "2,49 €"}
        )
        aktion = extrahiere(
            OHNE_JSONLD, "https://x.invalid/a", "p", Modellextraktor(client=client)
        )
        assert aktion is not None and aktion.title == "Hoffmann's Reis"

    def test_sichtbarer_text_ohne_skripte(self):
        text = sichtbarer_text("<html><script>var x=1</script><p>Hallo</p></html>")
        assert "Hallo" in text and "var x" not in text
