package de.gzgtracker.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubmissionFilteringTest {

    private val aktionen = listOf(
        aktion("a1").copy(title = "Duschgel gratis testen", brand = "Nivea"),
        aktion("a2").copy(title = "Kaffee Cashback", brand = "Dallmayr"),
    ).associateBy { it.id }

    private val submissions = listOf(
        einreichung(1, "a1", 1, status = SubmissionStatus.GEKAUFT, createdAtEpochSecond = 10)
            .copy(productName = "Duschgel Sensitive", retailer = "dm", ean = "4001234567890"),
        einreichung(2, "a2", 2, status = SubmissionStatus.EINGEREICHT, createdAtEpochSecond = 20)
            .copy(productName = "Kaffee Prodomo", retailer = "Rossmann"),
        einreichung(3, "a1", 3, status = SubmissionStatus.ERSTATTET, createdAtEpochSecond = 30)
            .copy(
                productName = "Duschgel Men",
                retailer = "dm",
                purchaseDate = LocalDate.of(2026, 9, 15),
            ),
    )

    private fun filtere(filter: SubmissionFilter) =
        SubmissionFiltering.anwenden(submissions, aktionen, filter)

    @Test
    fun `ohne Filter kommt alles neueste zuerst`() {
        val ergebnis = filtere(SubmissionFilter())
        assertEquals(listOf(3L, 2L, 1L), ergebnis.map { it.id })
    }

    @Test
    fun `filtert nach Status`() {
        val ergebnis = filtere(SubmissionFilter(status = setOf(SubmissionStatus.ERSTATTET)))
        assertEquals(listOf(3L), ergebnis.map { it.id })
    }

    @Test
    fun `filtert nach mehreren Status gleichzeitig`() {
        val ergebnis = filtere(
            SubmissionFilter(
                status = setOf(SubmissionStatus.GEKAUFT, SubmissionStatus.EINGEREICHT),
            ),
        )
        assertEquals(listOf(2L, 1L), ergebnis.map { it.id })
    }

    @Test
    fun `filtert nach Konto`() {
        assertEquals(listOf(2L), filtere(SubmissionFilter(accountId = 2)).map { it.id })
    }

    @Test
    fun `filtert nach Aktion`() {
        assertEquals(listOf(3L, 1L), filtere(SubmissionFilter(actionId = "a1")).map { it.id })
    }

    @Test
    fun `filtert nach Zeitraum einschliesslich der Grenzen`() {
        val ergebnis = filtere(
            SubmissionFilter(
                von = LocalDate.of(2026, 8, 1),
                bis = LocalDate.of(2026, 8, 1),
            ),
        )
        assertEquals(listOf(2L, 1L), ergebnis.map { it.id }, "Grenztage zaehlen mit")
    }

    @Test
    fun `sucht ueber Produktname`() {
        assertEquals(listOf(3L, 1L), filtere(SubmissionFilter(suche = "duschgel")).map { it.id })
    }

    @Test
    fun `sucht ueber Haendler Marke und EAN`() {
        assertEquals(listOf(3L, 1L), filtere(SubmissionFilter(suche = "dm")).map { it.id })
        assertEquals(listOf(2L), filtere(SubmissionFilter(suche = "dallmayr")).map { it.id })
        assertEquals(listOf(1L), filtere(SubmissionFilter(suche = "4001234567890")).map { it.id })
    }

    @Test
    fun `Suche ignoriert Gross- und Kleinschreibung`() {
        assertEquals(
            filtere(SubmissionFilter(suche = "DUSCHGEL")).map { it.id },
            filtere(SubmissionFilter(suche = "duschgel")).map { it.id },
        )
    }

    @Test
    fun `Kriterien wirken zusammen`() {
        val ergebnis = filtere(
            SubmissionFilter(actionId = "a1", status = setOf(SubmissionStatus.GEKAUFT)),
        )
        assertEquals(listOf(1L), ergebnis.map { it.id })
    }

    @Test
    fun `leerer Filter gilt als inaktiv`() {
        assertTrue(!SubmissionFilter().istAktiv)
        assertEquals(0, SubmissionFilter().anzahlKriterien)
    }

    @Test
    fun `zaehlt gesetzte Kriterien`() {
        val filter = SubmissionFilter(
            status = setOf(SubmissionStatus.GEKAUFT),
            accountId = 1,
            von = LocalDate.of(2026, 1, 1),
            bis = LocalDate.of(2026, 12, 31),
        )
        assertTrue(filter.istAktiv)
        // Zeitraum zaehlt als ein Kriterium, nicht als zwei.
        assertEquals(3, filter.anzahlKriterien)
    }
}
