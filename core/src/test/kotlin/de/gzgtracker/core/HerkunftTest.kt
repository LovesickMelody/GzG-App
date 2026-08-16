package de.gzgtracker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Wann warnt die App, dass die Seite nicht mehr zur Aktion gehoert?
 *
 * Der Knopf darunter schreibt IBAN, Bankverbindung, Geburtsdatum und Anschrift
 * in fremde Formularfelder. Zu selten warnen ist gefaehrlich, zu oft warnen
 * lehrt das Wegklicken — beides ist hier ein Fehler.
 */
class HerkunftTest {

    @Test
    fun `liest den Gastgeber und laesst www weg`() {
        assertEquals("justsnap.eu", hostVon("https://www.justsnap.eu/aktion"))
        assertEquals("airwick.justsnap.eu", hostVon("https://airwick.justsnap.eu/"))
    }

    @Test
    fun `unlesbare Adresse hat keinen Gastgeber`() {
        assertNull(hostVon(null))
        assertNull(hostVon(""))
        assertNull(hostVon("kein komische adresse"))
    }

    @Test
    fun `dieselbe Seite ist nicht fremd`() {
        assertNull(
            fremderGastgeber(
                "https://airwick.justsnap.eu/schritt2",
                "https://airwick.justsnap.eu/",
            ),
        )
    }

    @Test
    fun `Unterdomaene und Hauptdomaene gehoeren zusammen`() {
        assertNull(
            fremderGastgeber("https://justsnap.eu/einreichen", "https://airwick.justsnap.eu/"),
        )
        assertNull(
            fremderGastgeber("https://airwick.justsnap.eu/", "https://justsnap.eu/einreichen"),
        )
    }

    @Test
    fun `www macht keinen Unterschied`() {
        assertNull(fremderGastgeber("https://www.justsnap.eu/a", "https://justsnap.eu/b"))
    }

    @Test
    fun `fremde Domain wird gemeldet`() {
        assertEquals(
            "boeses.example",
            fremderGastgeber("https://boeses.example/formular", "https://airwick.justsnap.eu/"),
        )
    }

    @Test
    fun `aehnlicher Name ist trotzdem fremd`() {
        assertEquals(
            "justsnap.eu.boeses.example",
            fremderGastgeber(
                "https://justsnap.eu.boeses.example/",
                "https://justsnap.eu/",
            ),
        )
    }

    @Test
    fun `gemeinsame Endung allein reicht nicht`() {
        assertEquals(
            "andere.eu",
            fremderGastgeber("https://andere.eu/", "https://justsnap.eu/"),
        )
    }

    @Test
    fun `ohne Grundlage wird nicht gewarnt`() {
        assertNull(fremderGastgeber(null, "https://justsnap.eu/"))
        assertNull(fremderGastgeber("https://justsnap.eu/", null))
    }
}
