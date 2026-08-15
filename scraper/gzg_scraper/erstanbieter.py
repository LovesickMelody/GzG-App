"""
Quellenart „Erstanbieter": Kampagnen bei ihrem Urheber lesen statt bei einem Portal.

Das ist die Pipeline aus Entdeckung, Extraktion und Pruefung::

    ct_logs + sitemap  →  Kandidaten-Adressen   (kein Portal beteiligt)
            ↓
    fetch + tdm-Prüfung
            ↓
    extract (JSON-LD, sonst Modell)             (kein Selektor beteiligt)
            ↓
    pruefung                                    (was nicht belegt ist, fliegt raus)

Warum das eine eigene Quellenart ist und kein weiterer Parser: Ein Parser
bekommt fertiges HTML und gibt viele Aktionen zurueck. Hier ist es umgekehrt —
wir muessen erst herausfinden, *welche* Seiten es gibt, und bekommen dann je
Seite hoechstens *eine* Aktion. Das in die ``parse(html, quelle)``-Form zu
pressen haette nur verschleiert, dass es etwas anderes ist.

Der Preis: ein Abruf je Kandidat. Deshalb ``max_kandidaten`` — und deshalb
sortiert die Entdeckung die neuesten nach vorn.
"""

from __future__ import annotations

import logging

from .extract import Modellextraktor, extrahiere, sichtbarer_text
from .discovery import Kandidat, ct_logs, sitemap
from .fetch import Fetcher
from .models import Action
from .pruefung import Kontext, pruefe_liste
from .tdm import Vorbehaltspruefer

log = logging.getLogger(__name__)

# Wieviele Kandidatenseiten je Quelle und Lauf hoechstens abgerufen werden.
# Bei drei Sekunden Pause je Host sind 40 Abrufe rund zwei Minuten — vertretbar
# fuer einen Lauf am Tag. Was darueber liegt, kommt beim naechsten Mal dran.
STANDARD_MAX_KANDIDATEN = 40


def entdecke(quelle: dict, fetcher: Fetcher) -> list[Kandidat]:
    """Fuehrt alle konfigurierten Entdecker aus und fasst sie zusammen."""
    gefunden: list[Kandidat] = []

    for basis in quelle.get("ct_logs") or []:
        gefunden.extend(ct_logs.finde(basis, fetcher))

    for eintrag in quelle.get("sitemaps") or []:
        if isinstance(eintrag, str):
            eintrag = {"url": eintrag}
        adresse = eintrag.get("url")
        if not adresse:
            continue
        gefunden.extend(
            sitemap.finde(
                adresse,
                fetcher,
                muster=eintrag.get("muster"),
                quellenname=f"sitemap:{quelle['name']}",
            )
        )

    # Dieselbe Adresse kann aus zwei Entdeckern kommen. Der erste Fund gewinnt,
    # damit die Herkunft im Log stabil bleibt.
    gesehen: set[str] = set()
    eindeutig: list[Kandidat] = []
    for kandidat in gefunden:
        if kandidat.url in gesehen:
            continue
        gesehen.add(kandidat.url)
        eindeutig.append(kandidat)

    # Neueste zuerst: Wenn die Obergrenze greift, sollen die frischen Kampagnen
    # drin sein und nicht die von vorletztem Jahr. Ohne Datum ans Ende.
    eindeutig.sort(key=lambda k: k.zuerst_gesehen or "", reverse=True)
    return eindeutig


def sammle(
    quelle: dict,
    fetcher: Fetcher,
    extraktor: Modellextraktor | None = None,
    pruefer: Vorbehaltspruefer | None = None,
) -> list[Action] | None:
    """
    Holt alle laufenden Aktionen einer Erstanbieter-Quelle.

    Gibt ``None`` zurueck, wenn die Entdeckung selbst ausfiel — dann bleibt der
    bisherige Stand dieser Quelle stehen, genau wie bei einem Portalausfall.
    Eine leere Liste heisst dagegen: erfolgreich gesucht, gerade laeuft nichts.
    """
    name = quelle["name"]
    pruefer = pruefer or Vorbehaltspruefer()

    kandidaten = entdecke(quelle, fetcher)
    if not kandidaten:
        log.error("Quelle %s: keine Kandidaten gefunden — alter Stand bleibt", name)
        return None

    grenze = int(quelle.get("max_kandidaten", STANDARD_MAX_KANDIDATEN))
    if len(kandidaten) > grenze:
        log.warning(
            "Quelle %s: %s Kandidaten gefunden, nur die %s neuesten werden geprüft",
            name,
            len(kandidaten),
            grenze,
        )
        kandidaten = kandidaten[:grenze]

    gesammelt: list[Action] = []

    for kandidat in kandidaten:
        seite = fetcher.hole_seite(kandidat.url)
        if seite is None:
            continue
        html, gelandet = seite

        vorbehalt = pruefer.pruefe(gelandet, html, fetcher)
        if vorbehalt:
            log.info("%s: Nutzungsvorbehalt erkannt — %s", gelandet, vorbehalt)
            continue

        aktion = extrahiere(html, gelandet, name, extraktor)
        if aktion is None:
            continue

        befunde = pruefe_liste(
            [aktion],
            # nur_gestartete: Hier — und nur hier — kennen wir Kampagnen aus
            # Zertifikaten, die noch niemand angekuendigt hat.
            Kontext(seitentext=sichtbarer_text(html), nur_gestartete=True),
            quellenname=name,
        )
        gesammelt.extend(befunde)

    log.info(
        "Quelle %s: %s Aktion(en) aus %s Kandidaten",
        name,
        len(gesammelt),
        len(kandidaten),
    )
    return gesammelt
