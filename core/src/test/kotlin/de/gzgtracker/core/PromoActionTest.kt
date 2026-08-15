package de.gzgtracker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Der Unterschied zwischen Portalseite und Einreichungsseite.
 *
 * Er entscheidet, ob man mit einem Fingertipp im Formular landet oder erst noch
 * auf einer Portalseite suchen muss — deshalb sagt die App ihn auch an.
 */
class PromoActionTest {

    private fun aktion(url: String? = null, submitUrl: String? = null) =
        PromoAction(id = "a", title = "Testaktion", url = url, submitUrl = submitUrl)

    @Test
    fun `nimmt die Einreichungsseite wenn es eine gibt`() {
        val a = aktion(url = "https://portal.example/artikel", submitUrl = "https://anbieter.example/formular")
        assertEquals("https://anbieter.example/formular", a.besteAdresse)
        assertTrue(a.fuehrtDirektZumFormular)
    }

    @Test
    fun `faellt auf die Portalseite zurueck`() {
        val a = aktion(url = "https://portal.example/artikel")
        assertEquals("https://portal.example/artikel", a.besteAdresse)
        assertFalse(a.fuehrtDirektZumFormular)
    }

    @Test
    fun `ohne jede Adresse gibt es nichts zu oeffnen`() {
        assertNull(aktion().besteAdresse)
        assertFalse(aktion().fuehrtDirektZumFormular)
    }

    @Test
    fun `eine Aktion ohne bekannte Bedingungen hat eine leere Checkliste`() {
        // Leer heisst "nicht bekannt", nicht "nichts noetig" — die App muss den
        // Unterschied anzeigen koennen.
        assertTrue(aktion().requirements.isEmpty())
    }
}

/**
 * Das Kontingent in einem Satz. Viele Aktionen sind gedeckelt, und wer das
 * nicht weiss, kauft das Produkt und reicht zu spaet ein.
 */
class KontingentTextTest {

    private fun aktion(
        anzahl: Int? = null,
        zeitraum: String? = null,
        reset: String? = null,
        erschoepft: Boolean = false,
    ) = PromoAction(
        id = "a",
        title = "Test",
        limitAnzahl = anzahl,
        limitZeitraum = zeitraum,
        limitReset = reset,
        limitErschoepft = erschoepft,
    )

    @Test
    fun `nennt Anzahl und Zeitraum`() {
        assertEquals("1000 Teilnahmen pro Woche", aktion(1000, "woche").kontingentText)
    }

    @Test
    fun `ohne Zeitraum gilt insgesamt`() {
        assertEquals("500 Teilnahmen insgesamt", aktion(500).kontingentText)
    }

    @Test
    fun `haengt die Zuruecksetzung an`() {
        val text = aktion(1000, "woche", "Montags um 09:00 Uhr").kontingentText
        assertEquals("1000 Teilnahmen pro Woche, neu Montags um 09:00 Uhr", text)
    }

    @Test
    fun `nur die Zuruecksetzung reicht auch`() {
        assertEquals("neu Montags", aktion(reset = "Montags").kontingentText)
    }

    @Test
    fun `ohne Angaben gibt es keinen Text`() {
        assertEquals(null, aktion().kontingentText)
        assertEquals(false, aktion().hatKontingent)
    }

    @Test
    fun `erschoepft zaehlt als Angabe`() {
        assertEquals(true, aktion(erschoepft = true).hatKontingent)
    }
}
