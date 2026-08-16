package de.gzgtracker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EanTest {

    @Test
    fun `nimmt eine gueltige EAN-13`() {
        // Echte Nummern von Packungen, wie sie hier vorkommen.
        assertEquals("4008400202037", Ean.pruefe("4008400202037"))
        assertEquals("5000112637922", Ean.pruefe("5000112637922"))
    }

    @Test
    fun `nimmt EAN-8 und UPC-A`() {
        assertEquals("96385074", Ean.pruefe("96385074"))
        assertEquals("036000291452", Ean.pruefe("036000291452"))
    }

    @Test
    fun `wirft eine falsche Pruefziffer weg`() {
        // Letzte Ziffer verdreht: genau der Fall, den ein Leser produziert.
        assertNull(Ean.pruefe("4008400202038"))
        assertFalse(Ean.stimmtPruefziffer("4008400202038"))
    }

    @Test
    fun `entfernt Trennzeichen`() {
        // Auf Packungen steht die Nummer haeufig in Gruppen.
        assertEquals("4008400202037", Ean.pruefe("4 008400 202037"))
        assertEquals("4008400202037", Ean.pruefe("4-008400-202037"))
    }

    @Test
    fun `weist falsche Laengen ab`() {
        assertNull(Ean.pruefe("12345"))
        assertNull(Ean.pruefe("123456789012345678"))
        assertNull(Ean.pruefe(""))
        assertNull(Ean.pruefe(null))
    }

    @Test
    fun `weist Buchstaben ab`() {
        // Ein Preis oder eine Bonnummer ist keine EAN.
        assertNull(Ean.pruefe("ABC4008400202037"))
        assertFalse(Ean.stimmtPruefziffer("40084002020X7"))
    }

    @Test
    fun `die Pruefziffer stimmt bei allen Beispielen`() {
        listOf("4008400202037", "5000112637922", "96385074", "036000291452")
            .forEach { assertTrue(Ean.stimmtPruefziffer(it), "$it sollte gültig sein") }
    }
}
