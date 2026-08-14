package de.gzgtracker.data.remote

import de.gzgtracker.data.local.PromoActionEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Url
import java.time.Instant
import java.time.LocalDate

/**
 * Abbild von `data/actions.json`. Alle Felder ausser `id` und `title` sind optional —
 * ein Portal, das ein Feld nicht hergibt, soll den Import nicht kippen.
 */
@Serializable
data class ActionsFeedDto(
    @SerialName("generated_at") val generatedAt: String? = null,
    val actions: List<PromoActionDto> = emptyList(),
)

@Serializable
data class PromoActionDto(
    val id: String,
    val title: String,
    val brand: String? = null,
    val type: String? = null,
    @SerialName("max_refund_cents") val maxRefundCents: Int? = null,
    @SerialName("valid_from") val validFrom: String? = null,
    @SerialName("valid_to") val validTo: String? = null,
    @SerialName("submission_deadline") val submissionDeadline: String? = null,
    val url: String? = null,
    val retailers: List<String> = emptyList(),
    val eans: List<String> = emptyList(),
    @SerialName("image_url") val imageUrl: String? = null,
    val source: String? = null,
)

/** Kaputte Datumsangaben werden verworfen, nicht geworfen. */
private fun datum(raw: String?): LocalDate? =
    raw?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun PromoActionDto.toEntity(gesehenAm: Instant) = PromoActionEntity(
    id = id,
    title = title,
    brand = brand?.takeIf { it.isNotBlank() },
    type = type ?: "unbekannt",
    maxRefundCents = maxRefundCents?.takeIf { it >= 0 },
    validFrom = datum(validFrom),
    validTo = datum(validTo),
    submissionDeadline = datum(submissionDeadline),
    url = url?.takeIf { it.isNotBlank() },
    retailers = retailers.map { it.trim() }.filter { it.isNotBlank() },
    // Nur plausible EANs uebernehmen — EAN-8 und EAN-13, reine Ziffern.
    eans = eans.map { it.trim() }.filter { (it.length == 8 || it.length == 13) && it.all(Char::isDigit) },
    imageUrl = imageUrl?.takeIf { it.isNotBlank() },
    source = source?.takeIf { it.isNotBlank() } ?: "unbekannt",
    isManual = false,
    lastSeenAt = gesehenAm,
)

interface ActionsApi {

    /**
     * Die vollstaendige URL kommt aus den Einstellungen, damit der Feed auch aus
     * einem anderen Repo oder von einem eigenen Server kommen kann.
     */
    @GET
    suspend fun ladeFeed(@Url url: String): ActionsFeedDto
}
