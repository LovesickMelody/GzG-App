package de.gzgtracker.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Die Texte stammen aus dem, was Kassenbons wirklich hergeben: Kopfzeile,
 * Positionen, Summe, Zahlungsart, Rückgeld, Steuertabelle. Genau in dieser
 * Umgebung muss die Auswertung den einen Betrag finden, der zählt.
 */
class KassenbonTest {

    private val heute = LocalDate.of(2026, 8, 14)

    private val rewe = """
        REWE Markt GmbH
        Musterstraße 1, 12345 Musterstadt
        14.08.2026 17:42 Bon-Nr. 4711

        BONDUELLE SALAT        2,49 A
        MILCH 1,5%             1,19 B
        BROT                   2,29 B

        SUMME EUR              5,97
        Geg. BAR              10,00
        Rückgeld               4,03

        MwSt A 19,00%  0,40
        MwSt B  7,00%  0,23
    """.trimIndent()

    @Test
    fun `findet die Summe zwischen zwanzig anderen Zahlen`() {
        assertEquals(597, Kassenbon.lesePreis(rewe))
    }

    @Test
    fun `nimmt nicht den gegebenen Schein`() {
        // "Geg. BAR 10,00" ist der groesste Betrag auf dem Bon — und nie der Preis.
        val preis = Kassenbon.lesePreis(rewe)
        assertEquals(597, preis)
    }

    @Test
    fun `findet das Kaufdatum in der Kopfzeile`() {
        assertEquals(LocalDate.of(2026, 8, 14), Kassenbon.leseDatum(rewe, heute))
    }

    @Test
    fun `erkennt zu zahlen`() {
        val text = """
            EDEKA
            Zu zahlen              12,34
        """.trimIndent()
        assertEquals(1234, Kassenbon.lesePreis(text))
    }

    @Test
    fun `liest den Betrag aus der naechsten Zeile wenn er umgebrochen ist`() {
        // Schmale Bons brechen um, die Texterkennung erst recht.
        val text = """
            SUMME
            8,45
        """.trimIndent()
        assertEquals(845, Kassenbon.lesePreis(text))
    }

    @Test
    fun `nimmt bei mehreren Betraegen in einer Zeile den rechten`() {
        // Links stehen Stueckzahl und Einzelpreis, rechts der Gesamtbetrag.
        assertEquals(1497, Kassenbon.lesePreis("Summe  3 x 4,99      14,97"))
    }

    @Test
    fun `verwechselt die Steuerzeile nicht mit dem Preis`() {
        val text = """
            Gesamt                 4,00
            MwSt 19,00%            0,64
        """.trimIndent()
        assertEquals(400, Kassenbon.lesePreis(text))
    }

    @Test
    fun `gibt lieber nichts zurueck als etwas Falsches`() {
        // Kein Schluesselwort: Der groesste Betrag waere geraten, und ein
        // falscher Vorschlag faellt erst beim Einreichen auf.
        val text = """
            ARTIKEL A              2,49
            ARTIKEL B              1,19
        """.trimIndent()
        assertNull(Kassenbon.lesePreis(text))
    }

    @Test
    fun `verwirft einen Nullbetrag`() {
        assertNull(Kassenbon.lesePreis("Summe  0,00"))
    }

    @Test
    fun `nimmt das juengste plausible Datum`() {
        // Auf dem Bon steht auch die Frist eines Gutscheins in der Zukunft und
        // ein altes Datum aus der Fusszeile.
        val text = """
            01.02.2026 Kundenkarte seit
            14.08.2026 17:42
            Gutschein gültig bis 31.12.2027
        """.trimIndent()
        assertEquals(LocalDate.of(2026, 8, 14), Kassenbon.leseDatum(text, heute))
    }

    @Test
    fun `ignoriert Daten in der Zukunft`() {
        assertNull(Kassenbon.leseDatum("Mindestens haltbar bis 30.09.2027", heute))
    }

    @Test
    fun `ignoriert Daten weiter als ein Jahr zurueck`() {
        assertNull(Kassenbon.leseDatum("Ausgestellt am 01.01.2020", heute))
    }

    @Test
    fun `versteht zweistellige Jahre`() {
        assertEquals(LocalDate.of(2026, 8, 14), Kassenbon.leseDatum("14.08.26", heute))
    }

    @Test
    fun `verkraftet ein unmoegliches Datum`() {
        // Schlecht erkannte Bons liefern so etwas regelmaessig.
        assertNull(Kassenbon.leseDatum("31.02.2026", heute))
    }

    @Test
    fun `leerer Text ergibt keinen Vorschlag`() {
        val ergebnis = Kassenbon.auswerten("", heute)
        assertNull(ergebnis.preisCents)
        assertNull(ergebnis.datum)
        assertEquals(false, ergebnis.hatVorschlag)
    }

    @Test
    fun `wertet Preis und Datum zusammen aus`() {
        val ergebnis = Kassenbon.auswerten(rewe, heute)
        assertEquals(597, ergebnis.preisCents)
        assertEquals(LocalDate.of(2026, 8, 14), ergebnis.datum)
        assertEquals(true, ergebnis.hatVorschlag)
    }
}
