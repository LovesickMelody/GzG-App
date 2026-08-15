"""Tests fuer die Erkennung eines Nutzungsvorbehalts nach § 44b UrhG."""

from __future__ import annotations

from gzg_scraper.tdm import (
    Vorbehaltspruefer,
    vorbehalt_im_kopf,
    vorbehalt_im_text,
    vorbehalt_in_tdmrep,
)


class TestNatuerlicheSprache:
    """
    Das LG Hamburg hat entschieden, dass ein Vorbehalt auch in Worten wirksam
    ist. Deshalb genuegt es nicht, die robots.txt zu lesen.
    """

    def test_klassische_formulierung(self):
        assert vorbehalt_im_text(
            "Text und Data Mining im Sinne des § 44b UrhG ist untersagt."
        )

    def test_umgekehrte_reihenfolge(self):
        assert vorbehalt_im_text(
            "Wir untersagen ausdrücklich jede Form von automatisiertem Auslesen."
        )

    def test_ueber_zeilenumbruch_hinweg(self):
        assert vorbehalt_im_text("Data Mining\n\n    ist nicht gestattet.")

    def test_scraping_verboten(self):
        assert vorbehalt_im_text("Scraping unserer Inhalte ist verboten.")

    def test_ki_training_vorbehalten(self):
        assert vorbehalt_im_text(
            "Die Nutzung dieser Inhalte für KI-Training behalten wir uns vor."
        )

    def test_alle_rechte_vorbehalten_reicht_nicht(self):
        """Steht in jedem Impressum und darf allein nichts auslösen."""
        assert not vorbehalt_im_text("© 2026 Beispiel GmbH. Alle Rechte vorbehalten.")

    def test_gewoehnlicher_aktionstext_loest_nicht_aus(self):
        assert not vorbehalt_im_text(
            "Kaufe das Produkt, lade den Kassenbon hoch und erhalte 4,99 € zurück. "
            "Die Teilnahme ist auf einmal pro Haushalt beschränkt."
        )

    def test_weit_auseinander_loest_nicht_aus(self):
        weit = "Data Mining " + ("Fülltext " * 40) + " untersagt"
        assert not vorbehalt_im_text(weit)

    def test_leerer_text(self):
        assert vorbehalt_im_text(None) is None
        assert vorbehalt_im_text("") is None

    def test_fundstelle_bleibt_logtauglich(self):
        """Der Abstand begrenzt den Treffer — er kann nie die halbe Seite sein."""
        lang = "Text und Data Mining " + ("x" * 100) + " untersagt"
        gefunden = vorbehalt_im_text(lang)
        assert gefunden is not None and len(gefunden) < 200


class TestMetaAngaben:
    def test_tdm_reservation_eins(self):
        assert vorbehalt_im_kopf(
            '<html><head><meta name="tdm-reservation" content="1"></head></html>'
        )

    def test_tdm_reservation_null_ist_erlaubnis(self):
        """0 heisst ausdrücklich 'erlaubt' — das darf nicht als Verbot zählen."""
        assert not vorbehalt_im_kopf(
            '<html><head><meta name="tdm-reservation" content="0"></head></html>'
        )

    def test_robots_noai(self):
        assert vorbehalt_im_kopf(
            '<html><head><meta name="robots" content="index, follow, noai"></head></html>'
        )

    def test_gewoehnliche_robots_angabe(self):
        assert not vorbehalt_im_kopf(
            '<html><head><meta name="robots" content="index, follow"></head></html>'
        )


class TestTdmrep:
    def test_vorbehalt_fuer_ganze_domain(self):
        inhalt = '[{"location": "/", "tdm-reservation": 1}]'
        assert vorbehalt_in_tdmrep(inhalt, "/aktion/airwick")

    def test_laengster_treffer_gewinnt(self):
        """Ein Haus darf ein Unterverzeichnis freigeben und den Rest sperren."""
        inhalt = (
            '[{"location": "/", "tdm-reservation": 1},'
            ' {"location": "/presse/", "tdm-reservation": 0}]'
        )
        assert vorbehalt_in_tdmrep(inhalt, "/presse/mitteilung") is None
        assert vorbehalt_in_tdmrep(inhalt, "/aktion/airwick")

    def test_ohne_passenden_eintrag(self):
        inhalt = '[{"location": "/intern/", "tdm-reservation": 1}]'
        assert vorbehalt_in_tdmrep(inhalt, "/aktion/airwick") is None

    def test_kaputtes_json_ist_kein_verbot(self):
        assert vorbehalt_in_tdmrep("{kaputt", "/") is None

    def test_fehlende_datei(self):
        assert vorbehalt_in_tdmrep(None, "/") is None


class FetcherAttrappe:
    def __init__(self, antworten: dict[str, str]):
        self.antworten = antworten
        self.abrufe: list[str] = []

    def hole(self, url: str, still: bool = False) -> str | None:
        self.abrufe.append(url)
        return self.antworten.get(url)


class TestPruefer:
    def test_tdmrep_schlaegt_seiteninhalt(self):
        fetcher = FetcherAttrappe(
            {
                "https://x.invalid/.well-known/tdmrep.json": (
                    '[{"location": "/", "tdm-reservation": 1}]'
                )
            }
        )
        grund = Vorbehaltspruefer().pruefe(
            "https://x.invalid/aktion", "<html></html>", fetcher
        )
        assert grund and "tdmrep.json" in grund

    def test_tdmrep_wird_je_host_nur_einmal_geholt(self):
        """Bei dreissig Kampagnen einer Plattform sonst dreissig Abrufe."""
        fetcher = FetcherAttrappe({})
        pruefer = Vorbehaltspruefer()
        pruefer.pruefe("https://x.invalid/a", "<html></html>", fetcher)
        pruefer.pruefe("https://x.invalid/b", "<html></html>", fetcher)
        assert fetcher.abrufe.count("https://x.invalid/.well-known/tdmrep.json") == 1

    def test_saubere_seite_hat_keinen_vorbehalt(self):
        fetcher = FetcherAttrappe({})
        html = "<html><body><p>Jetzt 4,99 € zurückholen.</p></body></html>"
        assert Vorbehaltspruefer().pruefe("https://x.invalid/a", html, fetcher) is None

    def test_skript_inhalt_loest_nicht_aus(self):
        """Minifiziertes JavaScript enthält alle möglichen Wortkombinationen."""
        fetcher = FetcherAttrappe({})
        html = (
            "<html><body><script>var a='data mining';var b='untersagt';</script>"
            "<p>Aktion läuft</p></body></html>"
        )
        assert Vorbehaltspruefer().pruefe("https://x.invalid/a", html, fetcher) is None
