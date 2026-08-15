package de.gzgtracker.core

import java.time.LocalDate
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
 * Vorangekuendigte Aktionen.
 *
 * Portale nennen den Start oft Tage im Voraus. Die Aktion gehoert dann in die
 * App — aber sie darf nicht wie eine laufende aussehen, sonst kauft jemand zu
 * frueh und die Erstattung faellt aus.
 */
class PromoActionStartTest {

    private val heute = LocalDate.of(2026, 8, 15)

    private fun aktion(beginn: LocalDate?) =
        PromoAction(id = "a", title = "Testaktion", validFrom = beginn)

    @Test
    fun `kuenftiger Start zaehlt die Tage`() {
        assertEquals(2L, aktion(LocalDate.of(2026, 8, 17)).tageBisStart(heute))
        assertEquals(9L, aktion(LocalDate.of(2026, 8, 24)).tageBisStart(heute))
    }

    @Test
    fun `am Starttag laeuft sie schon`() {
        assertNull(aktion(heute).tageBisStart(heute))
    }

    @Test
    fun `laengst gestartete Aktion meldet nichts`() {
        assertNull(aktion(LocalDate.of(2026, 7, 1)).tageBisStart(heute))
    }

    @Test
    fun `ohne Startdatum gibt es nichts zu melden`() {
        assertNull(aktion(null).tageBisStart(heute))
    }
}
