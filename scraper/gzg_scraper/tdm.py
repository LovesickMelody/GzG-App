"""
Nutzungsvorbehalt nach § 44b UrhG erkennen.

Automatisiertes Auswerten fremder Seiten — Text und Data Mining — ist nach
§ 44b UrhG erlaubt, **solange der Rechteinhaber es nicht in maschinenlesbarer
Form untersagt hat**. Der Vorbehalt gilt unabhaengig davon, ob das Projekt Geld
verdient; eine Ausnahme fuer private oder kostenlose Nutzung gibt es nicht.

Wichtig fuer die Umsetzung: Das LG Hamburg hat im Verfahren Kneschke ./. LAION
entschieden, dass ein solcher Vorbehalt **auch in natuerlicher Sprache** wirksam
erklaert werden kann — mit der Begruendung, dass Maschinen natuerliche Sprache
heute verstehen. Ein Satz in den Nutzungsbedingungen reicht also aus. Die
``robots.txt`` allein abzufragen, wie es ``fetch.Fetcher`` tut, genuegt seitdem
nicht mehr.

Deshalb prueft dieses Modul drei Ebenen:

1. ``/.well-known/tdmrep.json`` — das TDM Reservation Protocol, die
   ausdruecklich dafuer gemachte Datei.
2. ``<meta name="tdm-reservation">`` und ``<meta name="robots" content="noai">``
   im Seitenkopf.
3. Formulierungen im Seitentext, die einen Vorbehalt aussprechen.

**Die Pruefung ist absichtlich schief eingestellt.** Ein falscher Alarm kostet
uns eine Quelle — aergerlich, mehr nicht. Ein uebersehener Vorbehalt kostet die
Rechtsgrundlage. Im Zweifel gilt deshalb: Vorbehalt erkannt.
"""

from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass, field
from urllib.parse import urljoin, urlparse

from bs4 import BeautifulSoup

log = logging.getLogger(__name__)

# Woruerber gesprochen wird. Bewusst breit — es geht um das, was wir tun, und
# das hat viele Namen.
_GEGENSTAND = (
    r"text[\s-]*(?:und|and|&)[\s-]*data[\s-]*mining"
    r"|data[\s-]*mining"
    r"|\btdm\b"
    r"|screen[\s-]*scraping"
    r"|\bscraping\b"
    r"|\bcrawl\w*"
    r"|automatisiert\w*\s+(?:ausles\w+|auswert\w+|abruf\w*|zugriff\w*|verarbeit\w+)"
    r"|maschinell\w*\s+(?:ausles\w+|auswert\w+|verarbeit\w+)"
    r"|ki[\s-]*training|ai[\s-]*training"
    r"|künstlich\w*\s+intelligenz"
)

# Was darueber gesagt wird, damit es ein Verbot ist.
_VERBOT = (
    # Gebeugte Formen mitnehmen: "untersagt", aber auch "wir untersagen".
    r"untersag\w*|nicht\s+gestattet|nicht\s+erlaubt|verboten|unzulässig"
    r"|widersprochen|widerspricht|widerspruch"
    r"|vorbehalten|behäl[tn]\s+(?:wir|sich)|behalten\s+wir\s+uns"
    r"|nutzungsvorbehalt|verwendungsvorbehalt"
)

# Beide Richtungen, hoechstens 120 Zeichen auseinander. Die Naehe ist der ganze
# Trick: "Alle Rechte vorbehalten" steht in jedem Impressum und darf allein
# nichts ausloesen — erst zusammen mit dem Gegenstand wird daraus ein Vorbehalt.
_ABSTAND = 120
_VORBEHALT_SATZ = re.compile(
    rf"(?:(?:{_GEGENSTAND}).{{0,{_ABSTAND}}}?(?:{_VERBOT}))"
    rf"|(?:(?:{_VERBOT}).{{0,{_ABSTAND}}}?(?:{_GEGENSTAND}))",
    re.I | re.S,
)

# Meta-Angaben, die dasselbe formal erklaeren.
_META_NAMEN = ("tdm-reservation", "tdmrep", "tdm-policy")
_ROBOTS_WERTE = ("noai", "noimageai", "notrain", "noml")


def vorbehalt_im_text(text: str | None) -> str | None:
    """
    Sucht einen in Worten erklaerten Vorbehalt.

    Gibt die gefundene Stelle gekuerzt zurueck, damit im Log nachvollziehbar
    ist, *warum* eine Quelle uebersprungen wurde — sonst sucht man beim
    naechsten leeren Feed vergeblich nach dem Grund.
    """
    if not text:
        return None

    # Zeilenumbrueche und geschuetzte Leerzeichen zusammenziehen, sonst reisst
    # ein Umbruch mitten im Satz die Naehe der beiden Teile auseinander.
    geglaettet = re.sub(r"[\s   ]+", " ", text)

    treffer = _VORBEHALT_SATZ.search(geglaettet)
    if not treffer:
        return None

    # Keine Kuerzung noetig: Der Ausdruck laesst zwischen beiden Teilen
    # hoechstens _ABSTAND Zeichen zu, der Treffer bleibt damit von selbst
    # kurz genug fuer eine Logzeile.
    return treffer.group(0).strip()


def vorbehalt_im_kopf(html: str | None) -> str | None:
    """Liest die Meta-Angaben, mit denen sich ein Vorbehalt formal erklaeren laesst."""
    if not html:
        return None

    suppe = BeautifulSoup(html, "lxml")

    for meta in suppe.find_all("meta"):
        name = (meta.get("name") or meta.get("property") or "").strip().casefold()
        inhalt = (meta.get("content") or "").strip().casefold()

        if name in _META_NAMEN:
            # tdm-reservation: 0 heisst ausdruecklich "erlaubt". Nur 1 verbietet.
            if name == "tdm-reservation" and inhalt in ("", "0"):
                continue
            return f"<meta name={name!r} content={inhalt!r}>"

        if name in ("robots", "googlebot") and any(
            wert in inhalt for wert in _ROBOTS_WERTE
        ):
            return f"<meta name={name!r} content={inhalt!r}>"

    return None


def vorbehalt_in_tdmrep(inhalt: str | None, pfad: str) -> str | None:
    """
    Wertet ``/.well-known/tdmrep.json`` fuer einen Pfad aus.

    Aufbau nach dem TDM Reservation Protocol: eine Liste von Eintraegen mit
    ``location`` und ``tdm-reservation`` (1 = vorbehalten). Es gilt der Eintrag
    mit dem laengsten passenden Praefix, damit ein Haus, das nur ein
    Unterverzeichnis schuetzt, nicht die ganze Domain sperrt.
    """
    if not inhalt:
        return None

    try:
        daten = json.loads(inhalt)
    except (json.JSONDecodeError, TypeError):
        log.info("tdmrep.json nicht lesbar — als 'kein Vorbehalt' gewertet")
        return None

    if isinstance(daten, dict):
        daten = [daten]
    if not isinstance(daten, list):
        return None

    bester: tuple[int, dict] | None = None
    for eintrag in daten:
        if not isinstance(eintrag, dict):
            continue
        ort = str(eintrag.get("location") or "/")
        if not ort.startswith("/"):
            ort = "/" + ort
        if pfad.startswith(ort) and (bester is None or len(ort) > bester[0]):
            bester = (len(ort), eintrag)

    if bester is None:
        return None

    if str(bester[1].get("tdm-reservation", "0")).strip() == "1":
        richtlinie = bester[1].get("tdm-policy")
        zusatz = f", Richtlinie {richtlinie}" if richtlinie else ""
        return f"tdmrep.json: tdm-reservation=1 für {bester[1].get('location', '/')}{zusatz}"

    return None


@dataclass
class Vorbehaltspruefer:
    """
    Prueft je Quelle einmal und merkt sich das Ergebnis.

    ``/.well-known/tdmrep.json`` haengt am Host und aendert sich nicht zwischen
    zwei Seiten desselben Anbieters — sie je Aktion erneut zu holen waere bei
    einer Plattform mit dreissig Kampagnen dreissig ueberfluessige Abrufe.
    """

    _je_host: dict[str, str | None] = field(default_factory=dict, repr=False)

    def pruefe(self, url: str, html: str | None, fetcher) -> str | None:
        """
        Gibt den Grund zurueck, wenn ein Vorbehalt vorliegt — sonst ``None``.

        ``fetcher`` ist ein ``fetch.Fetcher``; als Parameter statt als Feld,
        damit dieses Modul in den Tests ohne Netz auskommt.
        """
        grund = self._tdmrep_fuer(url, fetcher)
        if grund:
            return grund

        return vorbehalt_im_kopf(html) or vorbehalt_im_text(_nur_text(html))

    def _tdmrep_fuer(self, url: str, fetcher) -> str | None:
        teile = urlparse(url)
        host = f"{teile.scheme}://{teile.netloc}"

        if host not in self._je_host:
            adresse = urljoin(host, "/.well-known/tdmrep.json")
            # Die Datei fehlt auf den allermeisten Seiten. Ihr Fehlen ist der
            # Normalfall und keine Meldung wert.
            inhalt = fetcher.hole(adresse, still=True)
            self._je_host[host] = inhalt
            if inhalt:
                log.info("%s hat eine tdmrep.json", host)

        return vorbehalt_in_tdmrep(self._je_host[host], teile.path or "/")


def _nur_text(html: str | None) -> str | None:
    """Sichtbarer Text ohne Skripte — sonst durchsucht man minifiziertes JavaScript."""
    if not html:
        return None
    suppe = BeautifulSoup(html, "lxml")
    for stoerer in suppe(["script", "style", "noscript"]):
        stoerer.decompose()
    return suppe.get_text(" ", strip=True)
