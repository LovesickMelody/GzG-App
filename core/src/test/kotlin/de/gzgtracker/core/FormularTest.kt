package de.gzgtracker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

class EinreichdatenTest {

    private val konto = Account(
        id = 1,
        name = "DKB Giro",
        iban = "DE02 1203 0000 0000 2020 51",
        vorname = "Anna",
        nachname = "Muster",
        strasse = "Musterweg",
        hausnummer = "7a",
        plz = "12345",
        ort = "Musterstadt",
        telefon = "01701234567",
        email = "anna@example.org",
        anrede = "Frau",
        geburtsdatum = java.time.LocalDate.of(1985, 3, 7),
    )

    private fun daten(konto: Account? = this.konto) = Einreichdaten.aus(
        konto = konto,
        produktname = "Bonduelle Salat",
        preis = "2,49",
        kaufdatum = "14.08.2026",
        haendler = "Rewe",
    )

    @Test
    fun `nimmt die EAN mit, wenn eine da ist`() {
        val werte = Einreichdaten.aus(
            konto = konto,
            produktname = "Bonduelle Salat",
            preis = "2,49",
            kaufdatum = "14.08.2026",
            haendler = "Rewe",
            ean = "4008400202037",
        )
        assertEquals("4008400202037", werte[Formularfeld.EAN])
    }

    @Test
    fun `ohne EAN bleibt das Feld weg`() {
        assertNull(daten()[Formularfeld.EAN])
    }

    @Test
    fun `die EAN steht vor dem Produktnamen`() {
        // Das Fuellskript nimmt je Feld den ersten Treffer, und PRODUKT passt
        // mit "artikel" auch auf ein Feld "Artikelnummer". Stuende PRODUKT
        // vorn, landete der Produktname in der Artikelnummer.
        val werte = Einreichdaten.aus(
            konto = null,
            produktname = "Bonduelle Salat",
            preis = "2,49",
            kaufdatum = "14.08.2026",
            haendler = null,
            ean = "4008400202037",
        )
        val reihenfolge = werte.keys.toList()
        assertTrue(
            reihenfolge.indexOf(Formularfeld.EAN) < reihenfolge.indexOf(Formularfeld.PRODUKT),
            "EAN muss vor PRODUKT stehen, war aber $reihenfolge",
        )
    }

    @Test
    fun `nimmt Anrede und Geburtsdatum mit`() {
        val werte = daten()
        assertEquals("Frau", werte[Formularfeld.ANREDE])
        // Deutsche Schreibweise — so steht es in den Formularen.
        assertEquals("07.03.1985", werte[Formularfeld.GEBURTSDATUM])
    }

    @Test
    fun `ohne Anrede und Geburtsdatum bleiben die Felder weg`() {
        val werte = daten(konto.copy(anrede = null, geburtsdatum = null))
        assertEquals(null, werte[Formularfeld.ANREDE])
        assertEquals(null, werte[Formularfeld.GEBURTSDATUM])
    }

    @Test
    fun `nimmt die Person aus dem Konto und den Einkauf aus der Einreichung`() {
        val werte = daten()
        assertEquals("Anna", werte[Formularfeld.VORNAME])
        assertEquals("Muster", werte[Formularfeld.NACHNAME])
        assertEquals("Bonduelle Salat", werte[Formularfeld.PRODUKT])
        assertEquals("2,49", werte[Formularfeld.BETRAG])
        assertEquals("14.08.2026", werte[Formularfeld.KAUFDATUM])
        assertEquals("Rewe", werte[Formularfeld.HAENDLER])
    }

    @Test
    fun `schreibt die IBAN ohne Leerzeichen`() {
        // Viele Formulare pruefen die Laenge und stolpern ueber Gruppierungen.
        assertEquals("DE02120300000000202051", daten()[Formularfeld.IBAN])
    }

    @Test
    fun `setzt den Kontoinhaber aus Vor- und Nachname zusammen`() {
        assertEquals("Anna Muster", daten()[Formularfeld.KONTOINHABER])
    }

    @Test
    fun `ohne Konto bleibt der Einkauf uebrig`() {
        val werte = daten(konto = null)
        assertEquals(null, werte[Formularfeld.IBAN])
        assertEquals("Bonduelle Salat", werte[Formularfeld.PRODUKT])
    }

    @Test
    fun `leere Angaben fallen weg`() {
        // Sonst zaehlte die App Felder mit, die sie gar nicht fuellen kann.
        val werte = Einreichdaten.aus(
            konto = Account(id = 1, name = "Ohne Profil"),
            produktname = "Salat",
            preis = "2,49",
            kaufdatum = "14.08.2026",
            haendler = null,
        )
        assertEquals(3, werte.size)
    }

    @Test
    fun `endziffern kommen aus der vollen IBAN`() {
        assertEquals("2051", konto.endziffern)
    }

    @Test
    fun `ohne IBAN gelten die getrennt gespeicherten Endziffern`() {
        assertEquals("9876", Account(id = 1, name = "Alt", ibanLast4 = "9876").endziffern)
    }
}
