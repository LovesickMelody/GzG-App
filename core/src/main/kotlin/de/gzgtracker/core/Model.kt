package de.gzgtracker.core

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Wo eine Einreichung im Erstattungsprozess steht. */
enum class SubmissionStatus {
    /** Produkt gekauft, noch nicht eingereicht. */
    GEKAUFT,

    /** Bon und Daten beim Anbieter abgeschickt. */
    EINGEREICHT,

    /** Geld ist auf dem Konto angekommen. */
    ERSTATTET,

    /** Anbieter hat abgelehnt. */
    ABGELEHNT,
    ;

    /** Zaehlt der Betrag noch als offene Forderung? */
    val isPending: Boolean
        get() = this == GEKAUFT || this == EINGEREICHT

    /** Endzustand — hier aendert sich nichts mehr von allein. */
    val isClosed: Boolean
        get() = this == ERSTATTET || this == ABGELEHNT
}

/** Art der Aktion. Bestimmt, wie viel Geld zurueckerwartet wird. */
enum class PromoActionType {
    /** Kaufpreis wird bis zum Maximalbetrag voll erstattet. */
    GRATIS_TESTEN,

    /** Nur ein fester Teilbetrag wird erstattet. */
    CASHBACK_TEILBETRAG,

    /** Aus der Quelle nicht ableitbar. */
    UNBEKANNT,
    ;

    companion object {
        /** Liest den Wert aus `actions.json`; unbekannte Strings werden nicht zum Fehler. */
        fun fromWire(raw: String?): PromoActionType = when (raw?.trim()?.lowercase()) {
            "gratis_testen" -> GRATIS_TESTEN
            "cashback_teilbetrag" -> CASHBACK_TEILBETRAG
            else -> UNBEKANNT
        }
    }
}

/**
 * Ein Zielkonto fuer Erstattungen. Die volle IBAN wird bewusst nicht gespeichert —
 * fuer das Auseinanderhalten reichen Name und die letzten vier Stellen.
 */
data class Account(
    val id: Long,
    val name: String,
    val ibanLast4: String? = null,
    val colorHex: String = "#16181C",
    val isActive: Boolean = true,
    // --- Angaben fuer die Einreichungsformulare -------------------------
    // Sie stehen am Konto, weil bei einer Erstattung ohnehin Konto und Person
    // zusammengehoeren: Das Geld geht auf dieses Konto, also traegt man auch
    // dessen Inhaber ins Formular ein. Alles davon ist freiwillig — wer nichts
    // eintraegt, fuellt eben ein Feld mehr von Hand.
    /** Vollstaendige IBAN. Bleibt wie alles andere auf dem Geraet. */
    val iban: String? = null,
    val vorname: String? = null,
    val nachname: String? = null,
    val strasse: String? = null,
    val hausnummer: String? = null,
    val plz: String? = null,
    val ort: String? = null,
    val telefon: String? = null,
    val email: String? = null,
    /** Anrede, wie die Formulare sie verlangen: "Herr", "Frau" oder "Divers". */
    val anrede: String? = null,
    val geburtsdatum: LocalDate? = null,
) {
    /** Was in der Liste unter dem Namen steht. */
    val vollerName: String?
        get() = listOfNotNull(vorname, nachname)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

    /**
     * Die letzten vier Stellen — aus der vollen IBAN, wenn sie da ist.
     *
     * So muss niemand beides pflegen, und die Anzeige stimmt automatisch,
     * sobald die IBAN eingetragen wird.
     */
    val endziffern: String?
        get() = iban?.filter { !it.isWhitespace() }?.takeLast(4)?.takeIf { it.length == 4 }
            ?: ibanLast4

    /** Wie viel vom Profil ausgefuellt ist — fuer den Hinweis in der Kontoliste. */
    val profilVollstaendig: Boolean
        get() = !iban.isNullOrBlank() && !vorname.isNullOrBlank() && !nachname.isNullOrBlank()
}

/** Eine Geld-zurueck-Aktion, entweder aus `actions.json` oder von Hand angelegt. */
data class PromoAction(
    val id: String,
    val title: String,
    val brand: String? = null,
    val type: PromoActionType = PromoActionType.UNBEKANNT,
    val maxRefundCents: Int? = null,
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
    val submissionDeadline: LocalDate? = null,
    val url: String? = null,
    /**
     * Wo man tatsaechlich einreicht — meist eine andere Adresse als [url].
     * [url] zeigt auf den Artikel im Portal, hier steht das Formular des
     * Anbieters. Die App verlinkt bevorzugt hierhin und faellt auf [url] zurueck.
     */
    val submitUrl: String? = null,
    /**
     * Was man zum Mitmachen braucht, als Schluessel aus dem Feed. Leer heisst
     * "nicht bekannt", nicht "nichts noetig" — die App sagt das auch so.
     */
    val requirements: List<String> = emptyList(),
    val retailers: List<String> = emptyList(),
    val eans: List<String> = emptyList(),
    val imageUrl: String? = null,
    val source: String = "manuell",
    val isManual: Boolean = false,
) {
    /** Die Adresse, die "Zur Einreichung" oeffnet. */
    val besteAdresse: String? get() = submitUrl ?: url

    /** True, wenn [besteAdresse] direkt zum Formular fuehrt und nicht nur zum Portal. */
    val fuehrtDirektZumFormular: Boolean get() = submitUrl != null

    /**
     * Wie viele Tage bis zum Aktionsbeginn? ``null``, wenn sie schon laeuft oder
     * keinen Beginn nennt.
     *
     * Portale kuendigen Aktionen vorab an — mydealz regelmaessig mit dem
     * Startdatum im Titel ("ab dem 17.08."). Solche Aktionen gehoeren in die
     * App, damit man sie vormerken kann. Sie duerfen aber **nicht wie laufende
     * aussehen**: Wer heute kauft, hat einen Kassenbon von heute, und der liegt
     * vor dem Aktionszeitraum — die Erstattung faellt aus. Deshalb weist die
     * Liste eine kuenftige Aktion mit ihrem Beginn aus statt mit ihrer Frist.
     */
    fun tageBisStart(heute: LocalDate = LocalDate.now()): Long? {
        val beginn = validFrom ?: return null
        val tage = ChronoUnit.DAYS.between(heute, beginn)
        return if (tage > 0) tage else null
    }
}

/** Ein gekauftes Produkt und der Stand seiner Erstattung. */
data class Submission(
    val id: Long,
    val actionId: String,
    val accountId: Long,
    val productName: String,
    val ean: String? = null,
    val pricePaidCents: Int,
    val purchaseDate: LocalDate,
    val retailer: String? = null,
    val receiptImagePath: String? = null,
    /** Foto des Produkts allein. */
    val productImagePath: String? = null,
    /** Ein Bild, auf dem Produkt und Kassenbon zusammen zu sehen sind. */
    val comboImagePath: String? = null,
    val status: SubmissionStatus = SubmissionStatus.GEKAUFT,
    val submittedAt: LocalDate? = null,
    val refundedAt: LocalDate? = null,
    val refundedAmountCents: Int? = null,
    val note: String? = null,
    val createdAt: Instant = Instant.EPOCH,
) {
    /**
     * Alle Belegfotos in fester Reihenfolge, leere Plaetze ausgelassen.
     *
     * Drei Faelle statt einer Liste, weil die Portale genau diese drei
     * verlangen: nur den Bon, nur das Produkt, oder beides zusammen auf einem
     * Bild. Ein generischer Anhang haette dieselbe Frage offengelassen, die
     * beim Einreichen zaehlt — *was* zeigt das Bild?
     */
    val belege: List<Beleg>
        get() = listOfNotNull(
            productImagePath?.let { Beleg(Belegart.PRODUKT, it) },
            receiptImagePath?.let { Beleg(Belegart.BON, it) },
            comboImagePath?.let { Beleg(Belegart.ZUSAMMEN, it) },
        )

    val hatBeleg: Boolean get() = belege.isNotEmpty()
}

/** Was ein Belegfoto zeigt. */
enum class Belegart(val label: String, val anforderung: String) {
    PRODUKT("Produkt", "produktfoto"),
    BON("Kassenbon", "bonfoto"),
    ZUSAMMEN("Produkt mit Bon", "zusammen_fotografieren"),
    ;

    /**
     * True, wenn die Aktion genau dieses Bild verlangt.
     *
     * Kennt der Feed die Bedingungen nicht, ist die Antwort ueberall `false` —
     * dann wird kein Platz hervorgehoben, aber auch keiner gesperrt. Die
     * Checkliste fuehrt; sie schreibt nichts vor.
     */
    fun wirdVerlangt(anforderungen: List<String>?): Boolean =
        anforderungen?.contains(anforderung) == true
}

/** Ein Belegfoto und was darauf zu sehen ist. */
data class Beleg(val art: Belegart, val pfad: String)
