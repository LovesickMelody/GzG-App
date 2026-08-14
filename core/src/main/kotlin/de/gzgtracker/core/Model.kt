package de.gzgtracker.core

import java.time.Instant
import java.time.LocalDate

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
    val colorHex: String,
    val isActive: Boolean = true,
)

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
    val retailers: List<String> = emptyList(),
    val eans: List<String> = emptyList(),
    val imageUrl: String? = null,
    val source: String = "manuell",
    val isManual: Boolean = false,
)

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
    val status: SubmissionStatus = SubmissionStatus.GEKAUFT,
    val submittedAt: LocalDate? = null,
    val refundedAt: LocalDate? = null,
    val refundedAmountCents: Int? = null,
    val note: String? = null,
    val createdAt: Instant = Instant.EPOCH,
)
