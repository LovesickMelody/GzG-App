package de.gzgtracker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {

    @Test
    fun `formatiert Cent als deutschen Betrag`() {
        assertEquals("3,99 €", Money.format(399))
        assertEquals("0,05 €", Money.format(5))
        assertEquals("0,00 €", Money.format(0))
        assertEquals("10,00 €", Money.format(1000))
    }

    @Test
    fun `setzt Tausenderpunkte`() {
        assertEquals("1.234,56 €", Money.format(123456))
        assertEquals("12.345,67 €", Money.format(1234567))
        assertEquals("1.234.567,89 €", Money.format(123456789))
        assertEquals("999,99 €", Money.format(99999))
    }

    @Test
    fun `formatiert negative Betraege`() {
        assertEquals("-3,99 €", Money.format(-399))
        assertEquals("-1.000,00 €", Money.format(-100000))
    }

    @Test
    fun `liest deutsche Eingaben`() {
        assertEquals(399, Money.parseOrNull("3,99"))
        assertEquals(399, Money.parseOrNull("3.99"))
        assertEquals(399, Money.parseOrNull(" 3,99 € "))
        assertEquals(390, Money.parseOrNull("3,9"))
        assertEquals(300, Money.parseOrNull("3"))
        assertEquals(5, Money.parseOrNull("0,05"))
        assertEquals(0, Money.parseOrNull("0"))
    }

    @Test
    fun `liest Betraege mit Tausendertrenner`() {
        assertEquals(123456, Money.parseOrNull("1.234,56"))
        assertEquals(123400, Money.parseOrNull("1.234"))
        assertEquals(1234567, Money.parseOrNull("12.345,67"))
    }

    @Test
    fun `weist Unsinn zurueck`() {
        assertNull(Money.parseOrNull(""))
        assertNull(Money.parseOrNull("   "))
        assertNull(Money.parseOrNull("abc"))
        assertNull(Money.parseOrNull("3,99,50"))
        assertNull(Money.parseOrNull("-"))
        assertNull(Money.parseOrNull("€"))
    }

    @Test
    fun `format und parse sind zueinander invers`() {
        listOf(0, 1, 99, 100, 399, 12345, 99999, 100000, 123456789).forEach { cents ->
            assertEquals(cents, Money.parseOrNull(Money.formatPlain(cents)), "Roundtrip für $cents")
        }
    }
}
