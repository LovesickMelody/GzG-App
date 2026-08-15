package de.gzgtracker.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Texterkennung liefert Blöcke, keine Zeilen. Auf einem Kassenbon heisst
 * das regelmäßig: erst alle Artikelnamen untereinander, dann alle Beträge
 * untereinander. Genau daran scheiterte die Auswertung am Gerät.
 */
class BonlayoutTest {

    private fun stueck(text: String, links: Int, mitte: Int, hoehe: Int = 20) =
        Textstueck(text, links = links, oben = mitte - hoehe / 2, unten = mitte + hoehe / 2)

    @Test
    fun `setzt Name und Betrag derselben Hoehe wieder zusammen`() {
        val text = Bonlayout.zuText(
            listOf(
                stueck("BOROTALCO DEO", links = 20, mitte = 200),
                stueck("MILCH 1,5%", links = 20, mitte = 240),
                stueck("3,45", links = 400, mitte = 202),
                stueck("1,19", links = 400, mitte = 241),
            ),
        )
        assertEquals("BOROTALCO DEO   3,45\nMILCH 1,5%   1,19", text)
    }

    @Test
    fun `sortiert von oben nach unten`() {
        val text = Bonlayout.zuText(
            listOf(
                stueck("UNTEN", links = 10, mitte = 300),
                stueck("OBEN", links = 10, mitte = 100),
                stueck("MITTE", links = 10, mitte = 200),
            ),
        )
        assertEquals("OBEN\nMITTE\nUNTEN", text)
    }

    @Test
    fun `sortiert innerhalb der Zeile von links nach rechts`() {
        val text = Bonlayout.zuText(
            listOf(
                stueck("3,45", links = 400, mitte = 100),
                stueck("A", links = 500, mitte = 100),
                stueck("BOROTALCO", links = 20, mitte = 100),
            ),
        )
        assertEquals("BOROTALCO   3,45   A", text)
    }

    @Test
    fun `haelt eng stehende Zeilen auseinander`() {
        // Zwei Artikelzeilen dicht untereinander duerfen nicht verschmelzen —
        // sonst haengt der Preis am falschen Namen.
        val text = Bonlayout.zuText(
            listOf(
                stueck("ARTIKEL A", links = 20, mitte = 100, hoehe = 20),
                stueck("ARTIKEL B", links = 20, mitte = 122, hoehe = 20),
            ),
        )
        assertEquals("ARTIKEL A\nARTIKEL B", text)
    }

    @Test
    fun `nimmt unterschiedlich hohe Stuecke derselben Zeile zusammen`() {
        // Der Betrag rechts ist oft groesser gesetzt als der Name links.
        val text = Bonlayout.zuText(
            listOf(
                stueck("SUMME", links = 20, mitte = 500, hoehe = 18),
                stueck("79,00", links = 380, mitte = 504, hoehe = 30),
            ),
        )
        assertEquals("SUMME   79,00", text)
    }

    @Test
    fun `leere Stuecke fallen weg`() {
        val text = Bonlayout.zuText(
            listOf(
                stueck("  ", links = 10, mitte = 100),
                stueck("REWE", links = 20, mitte = 100),
            ),
        )
        assertEquals("REWE", text)
    }

    @Test
    fun `ohne Stuecke bleibt der Text leer`() {
        assertEquals("", Bonlayout.zuText(emptyList()))
    }

    @Test
    fun `findet den Produktpreis erst nach dem Zusammensetzen`() {
        // Der eigentliche Zweck: So kommt der Bon vom Gerät an — Namen in einem
        // Block, Beträge im nächsten.
        val stuecke = listOf(
            stueck("REWE Markt GmbH", links = 20, mitte = 40),
            stueck("14.08.2026", links = 20, mitte = 80),
            stueck("17:42", links = 300, mitte = 80),
            stueck("BOROTALCO DEO", links = 20, mitte = 200),
            stueck("WASCHMITTEL", links = 20, mitte = 240),
            stueck("SUMME EUR", links = 20, mitte = 320),
            stueck("3,45", links = 400, mitte = 201),
            stueck("12,99", links = 400, mitte = 241),
            stueck("16,44", links = 400, mitte = 321),
        )

        val ergebnis = Kassenbon.auswerten(
            stuecke,
            heute = LocalDate.of(2026, 8, 15),
            produkt = "Borotalco Deo",
        )

        assertEquals(345, ergebnis.preisCents)
        assertEquals(LocalDate.of(2026, 8, 14), ergebnis.datum)
        assertEquals("Rewe", ergebnis.haendler)
    }

    @Test
    fun `ohne Zusammensetzen faende sich der falsche Betrag`() {
        // Zum Vergleich: derselbe Bon als roher Blocktext, wie ihn die
        // Texterkennung ohne Rahmen liefert.
        val roh = """
            REWE Markt GmbH
            14.08.2026
            BOROTALCO DEO
            WASCHMITTEL
            SUMME EUR
            3,45
            12,99
            16,44
        """.trimIndent()
        val ergebnis = Kassenbon.auswerten(
            roh,
            heute = LocalDate.of(2026, 8, 15),
            produkt = "Borotalco Deo",
        )
        // Die Summenzeile greift auf die nächste Zeile durch — und liefert den
        // Preis irgendeines Artikels. Der Produktposten ist nicht zu finden.
        assertEquals(345, ergebnis.preisCents)
        assertEquals(null, Kassenbon.lesePreisFuerProdukt(roh, "Borotalco Deo"))
    }
}
