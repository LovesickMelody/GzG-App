package de.gzgtracker.data.local

import de.gzgtracker.core.Account
import de.gzgtracker.core.PromoAction
import de.gzgtracker.core.PromoActionType
import de.gzgtracker.core.Submission
import de.gzgtracker.core.SubmissionStatus
import java.time.Instant

/**
 * Uebersetzung zwischen Room-Entities und den Domaenenmodellen aus `:core`.
 *
 * Enums liegen in der Datenbank als Text, nicht als Ordinalzahl: Wird die
 * Reihenfolge im Enum spaeter geaendert, verrutschen sonst alle gespeicherten Werte.
 * Unbekannte Werte fallen auf einen definierten Standard zurueck statt zu werfen.
 */

fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    ibanLast4 = ibanLast4,
    colorHex = colorHex,
    isActive = isActive,
    iban = iban,
    vorname = vorname,
    nachname = nachname,
    strasse = strasse,
    hausnummer = hausnummer,
    plz = plz,
    ort = ort,
    telefon = telefon,
    email = email,
)

fun Account.toEntity(createdAt: Instant) = AccountEntity(
    id = id,
    name = name,
    ibanLast4 = ibanLast4,
    colorHex = colorHex,
    isActive = isActive,
    iban = iban,
    vorname = vorname,
    nachname = nachname,
    strasse = strasse,
    hausnummer = hausnummer,
    plz = plz,
    ort = ort,
    telefon = telefon,
    email = email,
    createdAt = createdAt,
)

fun PromoActionEntity.toDomain() = PromoAction(
    id = id,
    title = title,
    brand = brand,
    type = PromoActionType.fromWire(type),
    maxRefundCents = maxRefundCents,
    validFrom = validFrom,
    validTo = validTo,
    submissionDeadline = submissionDeadline,
    url = url,
    submitUrl = submitUrl,
    requirements = requirements,
    retailers = retailers,
    eans = eans,
    imageUrl = imageUrl,
    source = source,
    isManual = isManual,
)

fun PromoAction.toEntity(lastSeenAt: Instant? = null) = PromoActionEntity(
    id = id,
    title = title,
    brand = brand,
    type = type.wireName,
    maxRefundCents = maxRefundCents,
    validFrom = validFrom,
    validTo = validTo,
    submissionDeadline = submissionDeadline,
    url = url,
    submitUrl = submitUrl,
    requirements = requirements,
    retailers = retailers,
    eans = eans,
    imageUrl = imageUrl,
    source = source,
    isManual = isManual,
    lastSeenAt = lastSeenAt,
)

fun SubmissionEntity.toDomain() = Submission(
    id = id,
    actionId = actionId,
    accountId = accountId,
    productName = productName,
    ean = ean,
    pricePaidCents = pricePaidCents,
    purchaseDate = purchaseDate,
    retailer = retailer,
    receiptImagePath = receiptImagePath,
    productImagePath = productImagePath,
    comboImagePath = comboImagePath,
    status = runCatching { SubmissionStatus.valueOf(status) }
        .getOrDefault(SubmissionStatus.GEKAUFT),
    submittedAt = submittedAt,
    refundedAt = refundedAt,
    refundedAmountCents = refundedAmountCents,
    note = note,
    createdAt = createdAt,
)

fun Submission.toEntity() = SubmissionEntity(
    id = id,
    actionId = actionId,
    accountId = accountId,
    productName = productName,
    ean = ean,
    pricePaidCents = pricePaidCents,
    purchaseDate = purchaseDate,
    retailer = retailer,
    receiptImagePath = receiptImagePath,
    productImagePath = productImagePath,
    comboImagePath = comboImagePath,
    status = status.name,
    submittedAt = submittedAt,
    refundedAt = refundedAt,
    refundedAmountCents = refundedAmountCents,
    note = note,
    createdAt = createdAt,
)

/** Der Wert, wie er in `actions.json` steht. */
val PromoActionType.wireName: String
    get() = when (this) {
        PromoActionType.GRATIS_TESTEN -> "gratis_testen"
        PromoActionType.CASHBACK_TEILBETRAG -> "cashback_teilbetrag"
        PromoActionType.UNBEKANNT -> "unbekannt"
    }
