package de.gzgtracker.core

import java.time.Instant
import java.time.LocalDate

/** Bausteine fuer die Tests — bewusst knapp, damit die Testfaelle lesbar bleiben. */

fun konto(
    id: Long,
    name: String = "Konto $id",
    aktiv: Boolean = true,
) = Account(
    id = id,
    name = name,
    ibanLast4 = id.toString().padStart(4, '0'),
    colorHex = "#16181C",
    isActive = aktiv,
)

fun aktion(
    id: String,
    maxRefundCents: Int? = null,
    type: PromoActionType = PromoActionType.GRATIS_TESTEN,
) = PromoAction(
    id = id,
    title = "Aktion $id",
    brand = "Marke",
    type = type,
    maxRefundCents = maxRefundCents,
)

fun einreichung(
    id: Long,
    actionId: String,
    accountId: Long,
    pricePaidCents: Int = 399,
    status: SubmissionStatus = SubmissionStatus.EINGEREICHT,
    refundedAmountCents: Int? = null,
    createdAtEpochSecond: Long = id,
    purchaseDate: LocalDate = LocalDate.of(2026, 8, 1),
) = Submission(
    id = id,
    actionId = actionId,
    accountId = accountId,
    productName = "Produkt $id",
    pricePaidCents = pricePaidCents,
    purchaseDate = purchaseDate,
    status = status,
    refundedAmountCents = refundedAmountCents,
    createdAt = Instant.ofEpochSecond(createdAtEpochSecond),
)
