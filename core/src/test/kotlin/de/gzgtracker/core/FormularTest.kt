package de.gzgtracker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Das Skript läuft auf einer fremden Seite. Was hier hineingebaut wird, muss
 * also sauber entschärft sein — sonst reisst ein Anführungszeichen im
 * Produktnamen nicht nur das Skript auf, sondern führt fremden Code aus.
 */
class FormularTest {

    private val werte = mapOf(
        Formularfeld.IBAN to "DE02120300000000202051",
        Formularfeld.BETRAG to "3,99",
        Formularfeld.KAUFDATUM to "14.08.2026",
    )

    @Test
    fun `traegt alle Werte ins Skript ein`() {
        val skript = Formularskript.baue(werte)
        assertTrue(skript.contains("DE02120300000000202051"))
        assertTrue(skript.contains("3,99"))
        assertTrue(skript.contains("14.08.2026"))
    }

    @Test
    fun `nimmt die Suchwoerter des Feldes mit`() {
        val skript = Formularskript.baue(mapOf(Formularfeld.BETRAG to "3,99"))
        assertTrue(skript.contains("kaufbetrag"))
        assertTrue(skript.contains("betrag"))
    }

    @Test
    fun `laesst leere Werte weg`() {
        // Ein leeres Feld zu "fuellen" wuerde nur die Trefferzahl verwaessern.
        val skript = Formularskript.baue(
            mapOf(Formularfeld.IBAN to "", Formularfeld.BETRAG to "3,99"),
        )
        assertFalse(skript.contains("iban"))
        assertTrue(skript.contains("3,99"))
    }

    @Test
    fun `gibt die Zahl der gefuellten Felder zurueck`() {
        // Wer nur zwei von sechs getroffen sieht, prueft nach, bevor er absendet.
        assertTrue(Formularskript.baue(werte).contains("gefuellt + \"/\" + vorgaben.length"))
    }

    @Test
    fun `fuellt nur leere Felder`() {
        // Hat die Seite selbst schon etwas eingetragen, ist deren Wert besser.
        assertTrue(Formularskript.baue(werte).contains("if (feld.value && feld.value.trim()"))
    }

    @Test
    fun `loest Aenderungsereignisse aus`() {
        // Ohne sie merken Seiten mit React oder Vue nichts und senden leer ab.
        val skript = Formularskript.baue(werte)
        assertTrue(skript.contains("""new Event("input", { bubbles: true })"""))
        assertTrue(skript.contains("""new Event("change", { bubbles: true })"""))
    }

    @Test
    fun `entschaerft Anfuehrungszeichen`() {
        assertEquals("""Ben\"s Original""", Formularskript.escape("""Ben"s Original"""))
    }

    @Test
    fun `entschaerft Rueckwaertsschraegstriche`() {
        assertEquals("""a\\b""", Formularskript.escape("""a\b"""))
    }

    @Test
    fun `entschaerft Zeilenumbrueche`() {
        assertEquals("""a\nb""", Formularskript.escape("a\nb"))
    }

    @Test
    fun `ein Skriptende im Wert bleibt wirkungslos`() {
        val boesartig = """</script><script>alert(1)</script>"""
        val entschaerft = Formularskript.escape(boesartig)
        assertFalse(entschaerft.contains("<"))
        assertFalse(entschaerft.contains(">"))
    }

    @Test
    fun `ein praeparierter Produktname bricht nicht aus`() {
        val skript = Formularskript.baue(
            mapOf(Formularfeld.PRODUKT to """x"; alert(1); var y="""),
        )
        // Der Ausbruchsversuch steht nur noch als harmloser Text im Skript.
        assertFalse(skript.contains("""x"; alert(1)"""))
        assertTrue(skript.contains("""x\"; alert(1)"""))
    }

    @Test
    fun `unsichtbare Zeilentrenner werden entschaerft`() {
        // U+2028 und U+2029 gelten in JavaScript als Zeilenumbruch und wuerden
        // eine Zeichenkette mittendrin beenden. Sie stehen hier als Escape, weil
        // sie als Literal auch diese Quelldatei zerteilen wuerden.
        assertEquals("a\\u2028b", Formularskript.escape("a\u2028b"))
        assertEquals("a\\u2029b", Formularskript.escape("a\u2029b"))
    }

    @Test
    fun `findet ein Feld an seinem Schluessel wieder`() {
        assertEquals(Formularfeld.IBAN, Formularfeld.vonSchluessel("iban"))
        assertEquals(null, Formularfeld.vonSchluessel("gibtsnicht"))
    }
}
