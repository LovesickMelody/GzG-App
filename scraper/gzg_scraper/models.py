"""Datenmodell einer Aktion und die Bildung der stabilen Id."""

from __future__ import annotations

import hashlib
import re
import unicodedata
from dataclasses import asdict, dataclass, field


@dataclass
class Action:
    """Eine Geld-zurueck-Aktion, so wie sie in ``data/actions.json`` landet."""

    title: str
    source: str
    brand: str | None = None
    type: str = "gratis_testen"
    max_refund_cents: int | None = None
    valid_from: str | None = None
    valid_to: str | None = None
    submission_deadline: str | None = None
    url: str | None = None
    retailers: list[str] = field(default_factory=list)
    eans: list[str] = field(default_factory=list)
    image_url: str | None = None

    @property
    def id(self) -> str:
        return stable_id(self.title, self.brand, self.submission_deadline)

    def to_json(self) -> dict:
        """Feste Schluesselreihenfolge, damit der Diff im Repo lesbar bleibt."""
        daten = asdict(self)
        return {
            "id": self.id,
            "title": daten["title"],
            "brand": daten["brand"],
            "type": daten["type"],
            "max_refund_cents": daten["max_refund_cents"],
            "valid_from": daten["valid_from"],
            "valid_to": daten["valid_to"],
            "submission_deadline": daten["submission_deadline"],
            "url": daten["url"],
            "retailers": sorted(set(daten["retailers"])),
            "eans": sorted(set(daten["eans"])),
            "image_url": daten["image_url"],
            "source": daten["source"],
        }


def normalisiere(wert: str | None) -> str:
    """
    Vereinheitlicht einen Text fuer die Id-Bildung.

    Umlaute werden zerlegt und Sonderzeichen entfernt, damit aus "Müller" und
    "Mueller" derselbe Schluessel wird und ein spaeter korrigiertes Leerzeichen
    im Titel nicht die Id aendert.
    """
    if not wert:
        return ""
    # Erst die deutsche Umschrift, dann erst zerlegen: NFKD wuerde das
    # Umlautzeichen abtrennen, aus "ü" wuerde "u", und "Müller" traefe sich
    # nie mit "Mueller".
    umgeschrieben = (
        wert.casefold()
        .replace("ß", "ss")
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
    )
    zerlegt = unicodedata.normalize("NFKD", umgeschrieben)
    ohne_akzente = "".join(z for z in zerlegt if not unicodedata.combining(z))
    return re.sub(r"[^a-z0-9]+", " ", ohne_akzente).strip()


def stable_id(title: str, brand: str | None, deadline: str | None) -> str:
    """
    Stabile Id aus Titel, Marke und Einsendeschluss.

    Diese drei Angaben identifizieren eine Aktion in der Praxis eindeutig und
    aendern sich zwischen zwei Laeufen nicht. Bewusst *nicht* enthalten: die URL
    (Portale haengen gern Tracking-Parameter an) und der Betrag (wird manchmal
    nachtraeglich korrigiert) — sonst bekaeme dieselbe Aktion staendig eine neue
    Id, und die App wuerde sie als neue Aktion fuehren.
    """
    schluessel = "|".join(
        [normalisiere(title), normalisiere(brand), (deadline or "").strip()]
    )
    return hashlib.sha1(schluessel.encode("utf-8")).hexdigest()[:12]
