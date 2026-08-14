package de.gzgtracker.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TotalsCalculatorTest {

    private val aktionen = listOf(
        aktion("gratis", maxRefundCents = 399),
        aktion("teil", maxRefundCents = 200, type = PromoActionType.CASHBACK_TEILBETRAG),
        aktion("ohne-max"),
    ).associateBy { it.id }

    // --- erwartete Erstattung ------------------------------------------------

    @Test
    fun `ohne Maximalbetrag gilt der Kaufpreis`() {
        val betrag = TotalsCalculator.erwarteteErstattungCents(
            einreichung(id = 1, actionId = "ohne-max", accountId = 1, pricePaidCents = 549),
            aktionen["ohne-max"],
        )
        assertEquals(549, betrag)
    }

    @Test
    fun `der Maximalbetrag deckelt die Erwartung`() {
        val betrag = TotalsCalculator.erwarteteErstattungCents(
            einreichung(id = 1, actionId = "teil", accountId = 1, pricePaidCents = 399),
            aktionen["teil"],
        )
        assertEquals(200, betrag)
    }

    @Test
    fun `liegt der Kaufpreis unter dem Maximum zaehlt der Kaufpreis`() {
        val betrag = TotalsCalculator.erwarteteErstattungCents(
            einreichung(id = 1, actionId = "gratis", accountId = 1, pricePaidCents = 349),
            aktionen["gratis"],
        )
        assertEquals(349, betrag)
    }

    @Test
    fun `ohne bekannte Aktion gilt der Kaufpreis`() {
        val betrag = TotalsCalculator.erwarteteErstattungCents(
            einreichung(id = 1, actionId = "unbekannt", accountId = 1, pricePaidCents = 499),
            null,
        )
        assertEquals(499, betrag)
    }

    @Test
    fun `der tatsaechlich erstattete Betrag hat Vorrang`() {
        val betrag = TotalsCalculator.effektiveErstattungCents(
            einreichung(
                id = 1,
                actionId = "gratis",
                accountId = 1,
                pricePaidCents = 399,
                status = SubmissionStatus.ERSTATTET,
                refundedAmountCents = 350,
            ),
            aktionen["gratis"],
        )
        assertEquals(350, betrag)
    }

    @Test
    fun `ohne eingetragenen Betrag zaehlt bei ERSTATTET die Erwartung`() {
        val betrag = TotalsCalculator.effektiveErstattungCents(
            einreichung(
                id = 1,
                actionId = "gratis",
                accountId = 1,
                pricePaidCents = 399,
                status = SubmissionStatus.ERSTATTET,
            ),
            aktionen["gratis"],
        )
        assertEquals(399, betrag)
    }

    // --- Summenkarte ---------------------------------------------------------

    @Test
    fun `leere Liste ergibt Nullsummen`() {
        assertEquals(Totals(), TotalsCalculator.berechne(emptyList(), aktionen))
    }

    @Test
    fun `trennt ausstehend erstattet und abgelehnt`() {
        val submissions = listOf(
            einreichung(1, "gratis", 1, 399, SubmissionStatus.GEKAUFT),
            einreichung(2, "gratis", 2, 399, SubmissionStatus.EINGEREICHT),
            einreichung(3, "gratis", 3, 399, SubmissionStatus.ERSTATTET, refundedAmountCents = 399),
            einreichung(4, "teil", 1, 399, SubmissionStatus.ABGELEHNT),
        )

        val summen = TotalsCalculator.berechne(submissions, aktionen)

        assertEquals(798, summen.ausstehendCents, "gekauft + eingereicht")
        assertEquals(399, summen.erstattetCents)
        assertEquals(200, summen.abgelehntCents, "gedeckelt auf den Teilbetrag")
        assertEquals(4, summen.anzahl)
        assertEquals(2, summen.anzahlAusstehend)
        assertEquals(1, summen.anzahlErstattet)
    }

    @Test
    fun `deckelt die Erwartung bei Teil-Cashback`() {
        val submissions = listOf(
            einreichung(1, "teil", 1, 599, SubmissionStatus.EINGEREICHT),
            einreichung(2, "teil", 2, 150, SubmissionStatus.EINGEREICHT),
        )
        // 200 (gedeckelt) + 150 (Kaufpreis unter dem Maximum)
        assertEquals(350, TotalsCalculator.berechne(submissions, aktionen).ausstehendCents)
    }

    @Test
    fun `rechnet Summen je Konto`() {
        val submissions = listOf(
            einreichung(1, "gratis", 1, 399, SubmissionStatus.EINGEREICHT),
            einreichung(2, "ohne-max", 1, 250, SubmissionStatus.EINGEREICHT),
            einreichung(3, "gratis", 2, 399, SubmissionStatus.ERSTATTET, refundedAmountCents = 399),
        )

        val jeKonto = TotalsCalculator.jeKonto(submissions, aktionen)

        assertEquals(649, jeKonto.getValue(1L).ausstehendCents)
        assertEquals(0, jeKonto.getValue(1L).erstattetCents)
        assertEquals(0, jeKonto.getValue(2L).ausstehendCents)
        assertEquals(399, jeKonto.getValue(2L).erstattetCents)
    }

    @Test
    fun `zaehlt gross ohne Ueberlauf`() {
        val submissions = (1..500).map { index ->
            einreichung(index.toLong(), "ohne-max", 1, 1999, SubmissionStatus.EINGEREICHT)
        }
        assertEquals(999_500, TotalsCalculator.berechne(submissions, aktionen).ausstehendCents)
    }
}
