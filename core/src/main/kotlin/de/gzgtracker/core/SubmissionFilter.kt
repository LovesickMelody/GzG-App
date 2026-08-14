package de.gzgtracker.core

import java.time.LocalDate

/**
 * Filter der Uebersichtsliste. Alle Kriterien wirken zusammen (UND); ein leeres
 * Kriterium schraenkt nicht ein.
 */
data class SubmissionFilter(
    val status: Set<SubmissionStatus> = emptySet(),
    val accountId: Long? = null,
    val actionId: String? = null,
    val von: LocalDate? = null,
    val bis: LocalDate? = null,
    val suche: String = "",
) {
    val istAktiv: Boolean
        get() = status.isNotEmpty() ||
            accountId != null ||
            actionId != null ||
            von != null ||
            bis != null ||
            suche.isNotBlank()

    /** Zaehlt die gesetzten Kriterien — fuer das Abzeichen am Filter-Knopf. */
    val anzahlKriterien: Int
        get() = listOf(
            status.isNotEmpty(),
            accountId != null,
            actionId != null,
            von != null || bis != null,
            suche.isNotBlank(),
        ).count { it }
}

object SubmissionFiltering {

    /**
     * Wendet den Filter an und sortiert: neueste zuerst.
     *
     * Gesucht wird ueber Produktname, Marke, Aktionstitel, Haendler und EAN — wer
     * "dm" oder eine Ziffernfolge eintippt, will nicht raten muessen, welches Feld
     * durchsucht wird.
     */
    fun anwenden(
        submissions: List<Submission>,
        actionsById: Map<String, PromoAction>,
        filter: SubmissionFilter,
    ): List<Submission> {
        val suchbegriff = filter.suche.trim().lowercase()

        return submissions
            .asSequence()
            .filter { submission ->
                filter.status.isEmpty() || submission.status in filter.status
            }
            .filter { submission ->
                filter.accountId == null || submission.accountId == filter.accountId
            }
            .filter { submission ->
                filter.actionId == null || submission.actionId == filter.actionId
            }
            .filter { submission ->
                filter.von == null || !submission.purchaseDate.isBefore(filter.von)
            }
            .filter { submission ->
                filter.bis == null || !submission.purchaseDate.isAfter(filter.bis)
            }
            .filter { submission ->
                suchbegriff.isEmpty() ||
                    trefferFelder(submission, actionsById[submission.actionId])
                        .any { feld -> feld.contains(suchbegriff) }
            }
            .sortedWith(
                compareByDescending<Submission> { it.createdAt }.thenByDescending { it.id },
            )
            .toList()
    }

    private fun trefferFelder(submission: Submission, action: PromoAction?): List<String> =
        listOfNotNull(
            submission.productName,
            submission.retailer,
            submission.ean,
            submission.note,
            action?.title,
            action?.brand,
        ).map { it.lowercase() }
}
