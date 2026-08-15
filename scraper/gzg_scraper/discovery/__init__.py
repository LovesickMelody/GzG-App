"""
Entdeckung: Kampagnen finden, ohne ein Portal zu fragen.

Drei Wege, alle passiv und alle ohne Handarbeit je Aktion:

``ct_logs``   Zertifikatsprotokolle. Legt eine Plattform je Kampagne eine
              eigene Subdomain an — JustSnap tut das, siehe ``airwick.justsnap.eu``
              in den Teilnahmebedingungen der Air-Wick-Aktion — dann steht jede
              neue Kampagne binnen Minuten in einem oeffentlichen Register.
``sitemap``   Die ``sitemap.xml`` der Plattform. Faengt die Anbieter ab, die
              ihre Kampagnen ueber Pfade statt Subdomains fuehren.
``websuche``  Stehende Suchen als Netz darunter, fuer Plattformen, die wir noch
              nicht kennen.

Gemeinsam ist ihnen: Sie liefern **Kandidaten-Adressen**, mehr nicht. Ob dahinter
eine laufende Aktion steht, entscheidet erst ``extract`` und danach ``pruefung``.
Das ist Absicht — ein Zertifikat existiert oft Wochen vor dem Kampagnenstart.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Kandidat:
    """Eine Adresse, die eine Aktionsseite sein koennte."""

    url: str
    # Woher der Hinweis kam, etwa "ct:justsnap.eu". Steht im Log, damit man
    # sieht, welcher Entdecker sich lohnt und welcher nur Rauschen liefert.
    entdeckt_ueber: str
    # Wann die Adresse zuerst auftauchte (ISO), soweit bekannt. Bei
    # Zertifikaten ist das der Ausstellungszeitpunkt — ein Hinweis auf den
    # Kampagnenstart, aber ausdruecklich kein Beleg dafuer.
    zuerst_gesehen: str | None = None


__all__ = ["Kandidat"]
