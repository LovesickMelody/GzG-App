"""
Eingangspruefung: Was hier durchfaellt, kommt nicht in ``actions.json``.

Warum es diese Schicht gibt: Bis hierher hat jeder Parser selbst entschieden,
was er weitergibt. Solange die Selektoren von Hand am Markup abgelesen wurden,
ging das — ein falscher Selektor faellt beim Gegenlesen im Log auf. Sobald aber
ein Modell den Text auswertet (siehe ``extract/``), reicht das nicht mehr: Ein
Modell erfindet einen Betrag lieber, als keinen zu nennen, und der erfundene
sieht genauso plausibel aus wie der echte.

Deshalb muss jede Aktion vor der Veroeffentlichung **belegen**, was sie
behauptet. Die Regeln sind bewusst stur und einzeln abschaltbar; jede hat einen
konkreten Schaden, den sie verhindert:

===========================  ==================================================
Regel                        Was ohne sie passiert
===========================  ==================================================
``pflichtfelder``            Ein Eintrag ohne Titel oder Ziel steht als leere
                             Zeile in der App und laesst sich nicht oeffnen.
``betrag_belegt``            "4,99 € zurueck" steht im Feed, der Hersteller
                             erstattet 2 €. Wer danach eingekauft hat, hat
                             wegen unserer Angabe Geld verloren.
``frist_plausibel``          Eine Frist im Jahr 2231 (verlesenes Datum) haelt
                             eine tote Aktion fuer immer in der Liste.
``gestartet``                Die CT-Log-Entdeckung findet Kampagnen an dem Tag,
                             an dem ihr Zertifikat ausgestellt wird — oft Wochen
                             vor dem Start. Wer sie dann anzeigt, verraet die
                             Marketingplanung des Herstellers. Gilt **nur** fuer
                             entdeckte Quellen: Was ein Portal ankuendigt, ist
                             veroeffentlicht und darf vorgemerkt werden.
``kein_vorbehalt``           Wir werten eine Quelle aus, die das ausdruecklich
                             untersagt hat (§ 44b UrhG, siehe ``tdm.py``).
``einreichung_am_ort``       Eine fremde Seite bestimmt, wohin die App zum
                             Einreichen fuehrt — und dort fuellt sie auf
                             Knopfdruck IBAN und Anschrift ins Formular.
===========================  ==================================================

Bewusst **nicht** hier: der Ablauf-Filter. Den macht ``run.filtere_abgelaufene``
seit jeher und ist dort getestet; zwei Stellen fuer dieselbe Frage waeren eine
zu viel. Diese Schicht prueft nur, was dort nicht geprueft wird.
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field
from datetime import date
from urllib.parse import urlparse

from .models import Action

log = logging.getLogger(__name__)

# Wie weit eine Frist hoechstens in der Zukunft liegen darf. Echte Aktionen
# laufen Wochen bis Monate; alles darueber ist fast immer ein verlesenes Datum
# ("bis 31.12.2231" aus einer Artikelnummer). Grosszuegig gesetzt, damit eine
# echte Jahresaktion nicht faelschlich rausfliegt.
MAX_FRIST_TAGE = 730


@dataclass
class Kontext:
    """
    Was die Pruefung ueber die Herkunft einer Aktion weiss.

    Alle Felder sind freiwillig. Fehlt eine Angabe, wird die zugehoerige Regel
    **uebersprungen** statt zu scheitern — sonst wuerden die gewachsenen
    Portal-Quellen, bei denen es keinen Seitentext gibt, schlagartig alles
    verlieren. Eine Regel ohne Grundlage darf nicht raten.
    """

    # Der sichtbare Text der Seite, aus der die Aktion stammt. Grundlage der
    # Betragspruefung: Was nicht auf der Seite steht, darf nicht im Feed stehen.
    seitentext: str | None = None
    # Grund eines erkannten Nutzungsvorbehalts, sonst None (siehe tdm.py).
    vorbehalt: str | None = None
    heute: date | None = None
    # Ob ``submit_url`` zur Herkunft der Seite passen muss. Standard ist "nein":
    # Bei den Portalquellen ist der Unterschied ja der Zweck — ``url`` zeigt auf
    # den Artikel im Portal, ``submit_url`` auf das Formular des Herstellers.
    # Nur bei entdeckten Quellen stammen beide aus derselben Seite, und nur dort
    # kann ein fremder Seitentext das Ziel bestimmen.
    eigene_herkunft: bool = False
    # Ob noch nicht gestartete Aktionen abgelehnt werden. Standard ist "nein":
    # Ein Portal, das eine Aktion vorab ankuendigt, veroeffentlicht sie damit
    # selbst — da gibt es nichts zu verraten, und wer vormerken will, soll das
    # koennen. Nur die Entdeckung ueber Zertifikate sieht Dinge, die *niemand*
    # angekuendigt hat; dort setzt ``erstanbieter`` das Feld auf True.
    nur_gestartete: bool = False


@dataclass
class Befund:
    """Ergebnis der Pruefung einer einzelnen Aktion."""

    aktion: Action
    verstoesse: list[str] = field(default_factory=list)

    @property
    def darf_veroeffentlichen(self) -> bool:
        return not self.verstoesse


def _pflichtfelder(aktion: Action, kontext: Kontext) -> str | None:
    if not (aktion.title or "").strip():
        return "kein Titel"

    ziel = aktion.submit_url or aktion.url
    if not ziel:
        return "weder url noch submit_url"

    if urlparse(ziel).scheme not in ("http", "https"):
        return f"Ziel {ziel!r} ist keine Web-Adresse"

    return None


def _schreibweisen(cents: int) -> list[str]:
    """
    Alle Schreibweisen, in denen ein Betrag auf einer Seite stehen kann.

    499 → "4,99", "4.99". 500 → "5,00", "5.00", "5,-", "5,–" und "5" nur in
    Verbindung mit einer Waehrungsangabe. Der letzte Fall ist der Grund fuer die
    Sonderbehandlung: Eine nackte "5" kommt auf jeder Seite vor, sie wuerde jeden
    runden Betrag durchwinken.
    """
    euro, rest = divmod(cents, 100)
    formen = [f"{euro},{rest:02d}", f"{euro}.{rest:02d}"]

    if rest == 0:
        formen += [f"{euro},-", f"{euro},–"]
        formen += [f"{euro}{trenner}{zeichen}" for trenner in ("", " ") for zeichen in ("€", "EUR", "Euro")]

    return formen


def _gleiche_herkunft(einer: str, anderer: str) -> bool:
    """
    Gehoeren zwei Hosts zusammen?

    Gleich, oder der eine eine Unterdomaene des anderen:
    ``airwick.justsnap.eu`` und ``justsnap.eu`` gehoeren zusammen,
    ``airwick.justsnap.eu`` und ``boeses.example`` nicht.

    Bewusst ohne Public-Suffix-Liste — die waere eine weitere Abhaengigkeit fuer
    einen Vergleich, den diese Regel nicht braucht. Der Preis: Zwei Hosts unter
    derselben oeffentlichen Endung gaelten als verwandt, wenn einer davon *nur*
    die Endung waere. Deshalb muss jeder Host mindestens einen Punkt haben.
    """
    einer, anderer = einer.casefold(), anderer.casefold()
    if "." not in einer or "." not in anderer:
        return False
    return (
        einer == anderer
        or einer.endswith("." + anderer)
        or anderer.endswith("." + einer)
    )


def _einreichung_am_ort(aktion: Action, kontext: Kontext) -> str | None:
    """
    Der Einreichungslink muss zur Seite gehoeren, aus der die Aktion stammt.

    Bei entdeckten Quellen liest ein Modell den Seitentext — und den schreibt
    nicht wir, sondern wer auch immer die Seite betreibt. Ohne diese Regel
    koennte eine Seite bestimmen, wohin die App zum Einreichen fuehrt.

    Das waere kein theoretischer Schaden: Auf der Einreichungsseite fuellt die
    App auf Knopfdruck IBAN, Bankverbindung, Geburtsdatum und Anschrift in die
    Formularfelder. Ein untergeschobenes Ziel bekaeme genau das.
    """
    if not kontext.eigene_herkunft or not aktion.submit_url or not aktion.url:
        return None

    ziel = urlparse(aktion.submit_url).netloc.removeprefix("www.")
    quelle = urlparse(aktion.url).netloc.removeprefix("www.")
    if not ziel or not quelle:
        return None

    if _gleiche_herkunft(ziel, quelle):
        return None

    return f"Einreichungslink {ziel!r} gehört nicht zu {quelle!r}"


def _betrag_belegt(aktion: Action, kontext: Kontext) -> str | None:
    """
    Der Betrag muss woertlich auf der Seite stehen.

    Das ist die wichtigste Regel dieser Datei. Sie kostet uns gelegentlich einen
    korrekten Betrag, der nur als Bild oder erst nach einem Klick auftaucht —
    und verhindert dafuer, dass jemand ein Produkt kauft, weil bei uns eine Zahl
    stand, die es nirgends gab.

    Ohne ``seitentext`` ist nichts zu pruefen: Die Portal-Parser lesen den Betrag
    ohnehin aus genau dem Text, den sie sehen, und schreiben ihn nicht frei.
    """
    if aktion.max_refund_cents is None or kontext.seitentext is None:
        return None

    if aktion.max_refund_cents <= 0:
        return f"Betrag {aktion.max_refund_cents} ist kein Geldbetrag"

    # Geschuetzte und schmale Leerzeichen kommen in Preisangaben staendig vor
    # ("4 999,00 €") und wuerden den Vergleich sonst platzen lassen.
    text = re.sub(r"[\s   ]+", " ", kontext.seitentext)

    if any(form in text for form in _schreibweisen(aktion.max_refund_cents)):
        return None

    return (
        f"Betrag {aktion.max_refund_cents / 100:.2f} € steht nicht im Seitentext"
    )


def _frist_plausibel(aktion: Action, kontext: Kontext) -> str | None:
    """
    Eine erkannte Frist muss in einem sinnvollen Zeitraum liegen.

    Nur *lesbare* Fristen werden geprueft. Ein unlesbares Datum ist kein Grund
    zum Wegwerfen — dieselbe Haltung wie in ``run.filtere_abgelaufene``, und aus
    demselben Grund: Bei einer der Quellen fehlt die Frist grundsaetzlich.
    """
    frist = aktion.submission_deadline or aktion.valid_to
    if not frist:
        return None

    try:
        gelesen = date.fromisoformat(frist)
    except ValueError:
        return None

    stichtag = kontext.heute or date.today()
    if (gelesen - stichtag).days > MAX_FRIST_TAGE:
        return f"Frist {frist} liegt mehr als {MAX_FRIST_TAGE} Tage in der Zukunft"

    return None


def _gestartet(aktion: Action, kontext: Kontext) -> str | None:
    """
    Bei entdeckten Quellen darf eine Aktion erst in den Feed, wenn sie laeuft.

    Der Grund steht im Modulkopf: Die Entdeckung ueber Certificate-Transparency-
    Logs findet eine Kampagne, sobald ihr Zertifikat ausgestellt ist. Das ist
    regelmaessig Wochen vor dem Start — vor jeder Ankuendigung, vor jedem
    Handelsgespraech. So eine Aktion anzuzeigen verraet die Planung des
    Herstellers.

    **Fuer Portale gilt das ausdruecklich nicht.** Was mydealz ankuendigt, ist
    veroeffentlicht; die Aktion zu verschweigen nimmt der Merkliste ihren Zweck.
    Dass man vor dem Start nicht kaufen darf, ist keine Frage des Feeds, sondern
    der Anzeige — die App weist eine kuenftige Aktion als solche aus
    (``PromoAction.startetErst``).

    Ohne ``valid_from`` greift die Regel ohnehin nicht. "Kein Startdatum
    bekannt" heisst bei den meisten Quellen schlicht, dass keins ausgewiesen ist.
    """
    if not kontext.nur_gestartete or not aktion.valid_from:
        return None

    try:
        beginn = date.fromisoformat(aktion.valid_from)
    except ValueError:
        return None

    stichtag = kontext.heute or date.today()
    if beginn > stichtag:
        return f"startet erst am {aktion.valid_from}"

    return None


def _kein_vorbehalt(aktion: Action, kontext: Kontext) -> str | None:
    if kontext.vorbehalt:
        return f"Nutzungsvorbehalt der Quelle: {kontext.vorbehalt}"
    return None


# Reihenfolge = Reihenfolge im Log. Erst die billigen Regeln, dann die, die
# einen Seitentext durchsuchen.
REGELN = [
    ("pflichtfelder", _pflichtfelder),
    ("kein_vorbehalt", _kein_vorbehalt),
    ("einreichung_am_ort", _einreichung_am_ort),
    ("gestartet", _gestartet),
    ("frist_plausibel", _frist_plausibel),
    ("betrag_belegt", _betrag_belegt),
]


def pruefe(aktion: Action, kontext: Kontext | None = None) -> Befund:
    """Prueft eine Aktion gegen alle Regeln und sammelt *alle* Verstoesse."""
    kontext = kontext or Kontext()
    befund = Befund(aktion=aktion)

    for name, regel in REGELN:
        grund = regel(aktion, kontext)
        if grund:
            befund.verstoesse.append(f"{name}: {grund}")

    return befund


def pruefe_liste(
    aktionen: list[Action],
    kontext: Kontext | None = None,
    quellenname: str = "?",
) -> list[Action]:
    """
    Filtert eine Liste und schreibt jede Ablehnung mit Begruendung ins Log.

    Das Log ist hier kein Beiwerk: Eine still verschwundene Aktion ist von einer
    nie gefundenen nicht zu unterscheiden. Wer im Actions-Lauf nachsieht, soll
    lesen koennen, *warum* eine Aktion fehlt.
    """
    behalten: list[Action] = []
    abgelehnt = 0

    for aktion in aktionen:
        befund = pruefe(aktion, kontext)
        if befund.darf_veroeffentlichen:
            behalten.append(aktion)
            continue

        abgelehnt += 1
        log.warning(
            "Quelle %s: %r abgelehnt — %s",
            quellenname,
            (aktion.title or "ohne Titel")[:60],
            "; ".join(befund.verstoesse),
        )

    if abgelehnt:
        log.info(
            "Quelle %s: %s von %s Aktionen von der Prüfung abgelehnt",
            quellenname,
            abgelehnt,
            len(aktionen),
        )

    return behalten
