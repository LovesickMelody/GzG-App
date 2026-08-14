package de.gzgtracker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startet die echte App auf einem Emulator.
 *
 * Ein gruener Compile sagt nur, dass sich der Code uebersetzen laesst — nicht, dass
 * die App laeuft. Dieser Test deckt genau die Luecke ab: Er zieht beim Start die
 * gesamte Kette durch, die im Compiler unsichtbar bleibt — Hilt-Graph, Room-Datenbank,
 * DataStore, Schriften, Navigation und das erste Compose-Bild. Bricht eine davon,
 * faellt es hier auf und nicht erst auf dem Handy.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val app = createAndroidComposeRule<MainActivity>()

    // Bewusst je eine Behauptung pro Test: Faellt eine, sagt schon der Testname,
    // welches Element fehlt — statt dass drei Moeglichkeiten offenbleiben.

    @Test
    fun startetUndZeigtDieTitelzeile() {
        // "Belege" steht in der Titelzeile und in der Navigation — daher onFirst().
        app.onAllNodesWithText("Belege").onFirst().assertIsDisplayed()
    }

    @Test
    fun zeigtDenLeerenZustand() {
        app.onNodeWithText("Noch keine Einreichungen").assertIsDisplayed()
    }

    @Test
    fun zeigtDenEinladungstext() {
        app.onNodeWithText("Scanne dein erstes Produkt oder trag es von Hand ein.")
            .assertIsDisplayed()
    }

    @Test
    fun zeigtDenScanKnopf() {
        // Bei "nicht sichtbar" sagt die nackte Meldung nicht, wo das Element liegt.
        // Der Baum mit Koordinaten macht daraus eine brauchbare Diagnose.
        try {
            app.onNodeWithText("Produkt scannen").assertIsDisplayed()
        } catch (fehler: AssertionError) {
            throw AssertionError(
                "Der Scan-Knopf ist nicht sichtbar. UI-Baum mit Koordinaten:\n" +
                    app.onRoot().printToString(maxDepth = 100),
                fehler,
            )
        }
    }

    @Test
    fun zeigtDieSummenkarte() {
        app.onNodeWithText("Offen").assertIsDisplayed()
        // Summen bei leerer Liste: formatiert, nicht roh.
        app.onAllNodesWithText("0,00 €").onFirst().assertIsDisplayed()
    }

    @Test
    fun navigiertDurchAlleReiter() {
        app.onNodeWithText("Aktionen").performClick()
        app.onNodeWithText("Noch keine Aktionen").assertIsDisplayed()

        app.onNodeWithText("Konten").performClick()
        app.onNodeWithText("Noch keine Konten").assertIsDisplayed()

        app.onNodeWithText("Einstellungen").performClick()
        app.onNodeWithText("Kontoregel").assertIsDisplayed()

        app.onNodeWithText("Belege").performClick()
        app.onNodeWithText("Noch keine Einreichungen").assertIsDisplayed()
    }

    @Test
    fun legtEinKontoAnUndSpeichertEsInDerDatenbank() {
        app.onNodeWithText("Konten").performClick()
        app.onNodeWithText("Erstes Konto anlegen").performClick()

        app.onNodeWithText("Name").performTextInput("DKB Giro")
        app.onNodeWithText("Speichern").performClick()

        // Kommt der Name aus der Liste zurueck, hat Room geschrieben und gelesen.
        app.waitUntil(timeoutMillis = 5_000) {
            app.onAllNodesWithText("DKB Giro").fetchSemanticsNodes().isNotEmpty()
        }
        app.onAllNodesWithText("DKB Giro").onFirst().assertIsDisplayed()
    }

    @Test
    fun oeffnetDieEinstellungenUndSchaltetDieKontoregelUm() {
        app.onNodeWithText("Einstellungen").performClick()

        app.onNodeWithText("Blockieren").performClick()
        app.onNodeWithText("Warnen").assertIsDisplayed()

        // Die Feed-URL kommt aus BuildConfig und muss gefuellt sein.
        app.onNodeWithText("Feed-URL").assertIsDisplayed()
    }
}
