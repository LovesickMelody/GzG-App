package de.gzgtracker.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CsvExportTest {

    private val aktionen = listOf(aktion("a1", maxRefundCents = 399)).associateBy { it.id }
    private val konten = listOf(konto(1, name = "DKB Giro")).associateBy { it.id }

    private fun zeilen(submissions: List<Submission>) =
        CsvExport.erzeuge(submissions, aktionen, konten)
            .removePrefix(CsvExport.BOM)
            .split("\r\n")
            .filter { it.isNotBlank() }

    @Test
    fun `beginnt mit BOM und Kopfzeile`() {
        val csv = CsvExport.erzeuge(emptyList(), aktionen, konten)
        assertTrue(csv.startsWith(CsvExport.BOM), "BOM fuer Excel")
        assertTrue(csv.contains("Produkt;Aktion;Marke;Konto;Status"))
    }

    @Test
    fun `schreibt eine Zeile je Einreichung`() {
        val ergebnis = zeilen(
            listOf(
                einreichung(1, "a1", 1),
                einreichung(2, "a1", 1),
            ),
        )
        assertEquals(3, ergebnis.size, "Kopfzeile plus zwei Datenzeilen")
    }

    @Test
    fun `schreibt Betraege mit Komma`() {
        val zeile = zeilen(listOf(einreichung(1, "a1", 1, pricePaidCents = 399)))[1]
        assertTrue(zeile.contains("3,99"), "Kaufpreis deutsch formatiert: $zeile")
    }

    @Test
    fun `schreibt Datum deutsch`() {
        val zeile = zeilen(
            listOf(
                einreichung(1, "a1", 1).copy(purchaseDate = LocalDate.of(2026, 8, 4)),
            ),
        )[1]
        assertTrue(zeile.contains("04.08.2026"), zeile)
    }

    @Test
    fun `maskiert Semikolon und Anfuehrungszeichen`() {
        val zeile = zeilen(
            listOf(einreichung(1, "a1", 1).copy(productName = "Duschgel; 250ml \"neu\"")),
        )[1]
        assertTrue(zeile.startsWith("\"Duschgel; 250ml \"\"neu\"\"\""), zeile)
    }

    @Test
    fun `maskiert Zeilenumbrueche im Notizfeld`() {
        val csv = CsvExport.erzeuge(
            listOf(einreichung(1, "a1", 1).copy(note = "Zeile 1\nZeile 2")),
            aktionen,
            konten,
        )
        assertTrue(csv.contains("\"Zeile 1\nZeile 2\""), "Umbruch bleibt im Feld erhalten")
    }

    @Test
    fun `entschaerft Formeln im Produktnamen`() {
        val zeile = zeilen(
            listOf(einreichung(1, "a1", 1).copy(productName = "=1+1")),
        )[1]
        assertTrue(zeile.startsWith("'=1+1"), zeile)
    }

    @Test
    fun `laesst negative Betraege als Zahl stehen`() {
        val zeile = zeilen(
            listOf(
                einreichung(
                    1,
                    "a1",
                    1,
                    status = SubmissionStatus.ERSTATTET,
                    refundedAmountCents = -150,
                ),
            ),
        )[1]
        assertTrue(zeile.contains("-1,50"), zeile)
        assertFalse(zeile.contains("'-1,50"), "Zahl darf nicht als Text entschaerft werden")
    }

    @Test
    fun `schreibt den erwarteten Erstattungsbetrag`() {
        val zeile = zeilen(
            listOf(einreichung(1, "a1", 1, pricePaidCents = 599)),
        )[1]
        // Kaufpreis 5,99, gedeckelt auf 3,99 durch maxRefundCents.
        assertTrue(zeile.contains("5,99"), zeile)
        assertTrue(zeile.contains("3,99"), zeile)
    }

    @Test
    fun `nutzt die Aktions-Id wenn die Aktion fehlt`() {
        val zeile = zeilen(listOf(einreichung(1, "unbekannt", 1)))[1]
        assertTrue(zeile.contains("unbekannt"), zeile)
    }
}
