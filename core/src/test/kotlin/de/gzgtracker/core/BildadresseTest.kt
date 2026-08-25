package de.gzgtracker.core

import kotlin.test.Test
import kotlin.test.assertEquals

class BildadresseTest {

    private val vorschau =
        "https://static.mydealz.de/threads/raw/1b1iF/2822183_1/re/150x150/qt/55/2822183_1.jpg"

    @Test
    fun `vergroessert die Vorschau aus dem Feed`() {
        assertEquals(
            "https://static.mydealz.de/threads/raw/1b1iF/2822183_1/re/600x600/qt/80/2822183_1.jpg",
            Bildadresse.groesser(vorschau),
        )
    }

    @Test
    fun `behaelt das Seitenverhaeltnis`() {
        val quer = "https://example.invalid/a/re/200x100/qt/55/b.jpg"
        assertEquals("https://example.invalid/a/re/600x300/qt/80/b.jpg", Bildadresse.groesser(quer))
    }

    @Test
    fun `laesst schon grosse Bilder in Ruhe`() {
        val gross = "https://example.invalid/a/re/900x900/qt/90/b.jpg"
        assertEquals(gross, Bildadresse.groesser(gross))
    }

    @Test
    fun `senkt die Qualitaet nie`() {
        val fein = "https://example.invalid/a/re/150x150/qt/95/b.jpg"
        assertEquals("https://example.invalid/a/re/600x600/qt/95/b.jpg", Bildadresse.groesser(fein))
    }

    @Test
    fun `laesst Adressen ohne Massangabe unveraendert`() {
        // Nicht jede Quelle ist mydealz — rabattigel liefert eine feste Datei.
        val andere = "https://rabattigel.de/wp-content/uploads/2026/07/header-768x651.webp"
        assertEquals(andere, Bildadresse.groesser(andere))
        assertEquals("", Bildadresse.groesser(""))
    }

    @Test
    fun `die Vergroesserung ist stabil`() {
        // Zweimal angewandt kommt dasselbe heraus — sonst waechst die Adresse
        // bei jedem Neuzeichnen weiter.
        val einmal = Bildadresse.groesser(vorschau)
        assertEquals(einmal, Bildadresse.groesser(einmal))
    }
}
