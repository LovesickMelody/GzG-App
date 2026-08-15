"""
Extraktion per Sprachmodell — die Auffanglösung hinter JSON-LD.

Ein Prompt für alle Quellen: Statt je Portal Selektoren abzulesen, bekommt das
Modell den Seitentext und gibt ein festes JSON-Schema zurück. Ein Umbau der
Seite bricht damit nichts mehr — genau das ist der Zweck.

**Der wichtigste Entwurfsentscheid steht im Schema:** Das Modell liefert Betrag
und Datum als *wörtliches Zitat* aus der Seite ("4,99 €", "30.09.2026"), nicht
als fertige Zahl. Die Umrechnung machen anschliessend ``parsing.betrag_in_cent``
und ``parsing.datum_iso`` — dieselben Funktionen, die seit jeher die Portale
auswerten und die wissen, dass "0,75 l" kein Geldbetrag ist. Das Modell darf
also lesen, aber nicht rechnen. Was es trotzdem erfindet, faellt anschliessend
in ``pruefung`` durch, weil der Betrag woertlich im Seitentext stehen muss.

Ohne API-Schluessel gibt dieses Modul ``None`` zurueck und meldet das einmal.
Die CI laeuft damit gruen, ohne dass ein Schluessel hinterlegt sein muss — die
Tests arbeiten gegen eine Attrappe, nicht gegen das Netz.
"""

from __future__ import annotations

import json
import logging
import os

from ..models import Action
from ..parsing import (
    anforderungen_aus,
    betrag_in_cent,
    datum_iso,
    saeubere,
)

log = logging.getLogger(__name__)

# Vorgabe laut Projektregeln fuer neue Claude-Anbindungen. Ueber sources.yaml
# oder GZG_MODELL umstellbar — ein kleineres Modell kostet deutlich weniger und
# reicht fuer diese Aufgabe oft aus. Die Entscheidung gehoert dem Betreiber,
# nicht diesem Modul.
STANDARD_MODELL = "claude-opus-5"

# "low" genuegt: Aus einem vorliegenden Text ein Dutzend Felder abzuschreiben
# ist keine Denkaufgabe. Hoehere Stufen kosten Vielfaches ohne besseres Ergebnis.
STANDARD_EFFORT = "low"

# Wieviel Seitentext hoechstens ins Modell geht. Die Angaben stehen bei diesen
# Seiten immer oben; der Rest ist Navigation, Footer und Cookie-Hinweis.
MAX_ZEICHEN = 12_000

# Anweisung an das Modell. Bewusst stabil gehalten: Sie ist bei jedem Aufruf
# gleich und liegt vor dem wechselnden Seitentext, damit das Zwischenspeichern
# des Anbieters greift (unter 512 Token bleibt es wirkungslos, schadet aber nie).
ANWEISUNG = """\
Du liest die Seite einer deutschen Geld-zurück-Aktion ("gratis testen", \
"Cashback") und trägst die Eckdaten zusammen.

Regeln:
- Beträge und Daten gibst du WÖRTLICH so zurück, wie sie auf der Seite stehen \
("4,99 €", "bis zum 30.09.2026"). Rechne nichts um und formatiere nichts. \
Steht die Angabe nicht auf der Seite, gibst du null zurück.
- Erfinde nichts. Ein leeres Feld ist immer besser als ein geratenes.
- "ist_aktion" ist nur dann true, wenn die Seite tatsächlich eine \
Geld-zurück- oder Gratis-testen-Aktion für ein bestimmtes Produkt bewirbt. \
Übersichtsseiten, Artikel über Aktionen, Startseiten und abgelaufene Aktionen \
sind false.
- "betrag_zitat" ist der Betrag, den der Käufer zurückbekommt — nicht der \
Ladenpreis, nicht die Füllmenge, nicht eine Ersparnis in Prozent.
- "frist_zitat" ist der Einsendeschluss, "beginn_zitat" der Aktionsbeginn.
"""

SCHEMA = {
    "type": "object",
    "properties": {
        "ist_aktion": {
            "type": "boolean",
            "description": "Bewirbt diese Seite eine konkrete Geld-zurück-Aktion?",
        },
        "titel": {
            "type": "string",
            "description": "Produkt und Aktion, kurz. Ohne Portalzusätze.",
        },
        "marke": {"type": ["string", "null"]},
        "betrag_zitat": {
            "type": ["string", "null"],
            "description": "Erstattungsbetrag wörtlich von der Seite, z. B. '4,99 €'.",
        },
        "frist_zitat": {
            "type": ["string", "null"],
            "description": "Einsendeschluss wörtlich, z. B. '30.09.2026'.",
        },
        "beginn_zitat": {
            "type": ["string", "null"],
            "description": "Aktionsbeginn wörtlich, falls genannt.",
        },
        "bedingungen": {
            "type": ["string", "null"],
            "description": "Teilnahmebedingungen im Wortlaut der Seite, gekürzt.",
        },
        "einreichungslink": {
            "type": ["string", "null"],
            "description": "Adresse des Formulars, falls die Seite eine nennt.",
        },
        "haendler": {
            "type": "array",
            "items": {"type": "string"},
            "description": "Genannte Händler, z. B. dm, Rossmann.",
        },
    },
    "required": ["ist_aktion", "titel"],
    "additionalProperties": False,
}


class Modellextraktor:
    """
    Liest Aktionen aus beliebigem Seitentext.

    Der Client wird erst beim ersten Abruf gebaut, damit ein Import dieses
    Moduls ohne Schluessel und ohne installiertes SDK nicht scheitert — die
    Tests der uebrigen Parser sollen davon nichts merken.
    """

    def __init__(
        self,
        modell: str = STANDARD_MODELL,
        effort: str = STANDARD_EFFORT,
        client=None,
    ) -> None:
        self.modell = modell
        self.effort = effort
        self._client = client
        self._gemeldet = False

    @property
    def verfuegbar(self) -> bool:
        return self._client is not None or bool(os.environ.get("ANTHROPIC_API_KEY"))

    def _hole_client(self):
        if self._client is not None:
            return self._client

        if not os.environ.get("ANTHROPIC_API_KEY"):
            if not self._gemeldet:
                log.info(
                    "Kein ANTHROPIC_API_KEY gesetzt — Modell-Extraktion übersprungen. "
                    "Quellen mit JSON-LD laufen weiter."
                )
                self._gemeldet = True
            return None

        try:
            import anthropic
        except ImportError:
            if not self._gemeldet:
                log.warning(
                    "Paket 'anthropic' nicht installiert — Modell-Extraktion "
                    "übersprungen. pip install -r requirements.txt"
                )
                self._gemeldet = True
            return None

        self._client = anthropic.Anthropic()
        return self._client

    def lies(self, text: str | None, url: str, quellenname: str) -> Action | None:
        """
        Baut eine Aktion aus dem Seitentext, oder ``None``.

        ``None`` heisst hier immer "keine brauchbare Aktion" — ob wegen
        fehlendem Schluessel, Netzfehler oder weil die Seite gar keine Aktion
        bewirbt. Der Aufrufer behandelt alle drei Faelle gleich: Eintrag faellt
        weg, Lauf geht weiter.
        """
        if not text or not text.strip():
            return None

        client = self._hole_client()
        if client is None:
            return None

        gekuerzt = text[:MAX_ZEICHEN]

        try:
            antwort = client.messages.create(
                model=self.modell,
                max_tokens=4000,
                system=[
                    {
                        "type": "text",
                        "text": ANWEISUNG,
                        "cache_control": {"type": "ephemeral"},
                    }
                ],
                output_config={
                    "effort": self.effort,
                    "format": {"type": "json_schema", "schema": SCHEMA},
                },
                messages=[
                    {
                        "role": "user",
                        "content": f"Adresse: {url}\n\nSeitentext:\n{gekuerzt}",
                    }
                ],
            )
        except Exception as fehler:  # noqa: BLE001 — ein Ausfall darf den Lauf nicht kippen
            log.warning("Modell-Extraktion für %s fehlgeschlagen: %s", url, fehler)
            return None

        if antwort.stop_reason == "refusal":
            log.warning("Modell hat %s abgelehnt — übersprungen", url)
            return None

        rohtext = next(
            (block.text for block in antwort.content if block.type == "text"), None
        )
        if not rohtext:
            log.warning("Modell lieferte keinen Text für %s", url)
            return None

        try:
            daten = json.loads(rohtext)
        except json.JSONDecodeError as fehler:
            log.warning("Antwort für %s nicht lesbar: %s", url, fehler)
            return None

        return baue_aktion(daten, url, quellenname, seitentext=text)


def baue_aktion(
    daten: dict, url: str, quellenname: str, seitentext: str | None = None
) -> Action | None:
    """
    Macht aus der Modellantwort eine Aktion — mit unseren eigenen Parsern.

    Getrennt von [Modellextraktor.lies], damit die Tests diesen Schritt ohne
    Netz und ohne Attrappe pruefen koennen. Hier passiert die eigentliche
    Absicherung: Aus ``betrag_zitat`` wird ein Betrag nur, wenn
    ``parsing.betrag_in_cent`` ihn als Geldbetrag erkennt.
    """
    if not isinstance(daten, dict) or not daten.get("ist_aktion"):
        return None

    titel = saeubere(daten.get("titel"))
    if not titel:
        return None

    bedingungen = daten.get("bedingungen") or ""
    # Die Anforderungen liest die bestehende Wortliste — nicht das Modell.
    # Ein erfundener Haken schickt jemanden mit dem falschen Foto los.
    anforderungen = anforderungen_aus(bedingungen or seitentext)

    frist = datum_iso(daten.get("frist_zitat"))

    return Action(
        title=titel,
        source=quellenname,
        brand=saeubere(daten.get("marke")),
        max_refund_cents=betrag_in_cent(daten.get("betrag_zitat")),
        valid_from=datum_iso(daten.get("beginn_zitat")),
        valid_to=frist,
        submission_deadline=frist,
        url=url,
        submit_url=saeubere(daten.get("einreichungslink")) or url,
        requirements=anforderungen,
        retailers=[h for h in (daten.get("haendler") or []) if isinstance(h, str)],
    )
