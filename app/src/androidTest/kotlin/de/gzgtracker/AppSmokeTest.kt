package de.gzgtracker

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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

    /**
     * Die App startet auf "Aktionen" — dort beginnt der Weg: erst suchen, was es
     * gibt, dann kaufen, dann eintragen.
     */
    @Test
    fun startetUndZeigtDieTitelzeile() {
        app.onAllNodesWithText("Aktionen").onFirst().assertIsDisplayed()
    }

    /** Wechselt auf den Belegstapel — die Tests darunter spielen dort. */
    private fun zeigeBelege() {
        app.onNodeWithText("Belege").performClick()
    }

    @Test
    fun zeigtDenLeerenZustand() {
        zeigeBelege()
        app.onNodeWithText("Noch keine Einreichungen").assertIsDisplayed()
    }

    @Test
    fun zeigtDenEinladungstext() {
        zeigeBelege()
        app.onNodeWithText("Such dir eine Aktion, kauf das Produkt und trag den Beleg hier ein.")
            .assertIsDisplayed()
    }

    /**
     * Die Hauptaktion der App — fehlt sie, kommt man nirgendwo hin.
     *
     * Der Knoten war in einem Lauf vorhanden (nur "nicht sichtbar") und im naechsten
     * gar nicht, ohne dass sich App-Code geaendert haette. Das deutete auf ein
     * Zeitproblem beim Aufbau hin, nicht auf Geometrie. Deshalb wird hier aktiv
     * gewartet und im Fehlerfall der gesamte Baum einzeilig ausgegeben — mehrzeilige
     * Meldungen schneidet Gradle in der Konsole ab.
     */
    @Test
    fun hatEinenBedienbarenHauptknopf() {
        zeigeBelege()
        val erschienen = runCatching {
            app.waitUntil(timeoutMillis = 10_000) {
                app.onAllNodesWithContentDescription("Beleg eintragen")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            true
        }.getOrDefault(false)

        if (!erschienen) {
            throw AssertionError(
                "Hauptknopf nach 10 s nicht im Baum. Baum: " +
                    app.onRoot().printToString(maxDepth = 100).replace("\n", " | "),
            )
        }

        app.onNodeWithContentDescription("Beleg eintragen").assertHasClickAction()
    }

    @Test
    fun zeigtDieSummenkarte() {
        zeigeBelege()
        app.onNodeWithText("Offen").assertIsDisplayed()
        // Summen bei leerer Liste: formatiert, nicht roh.
        app.onAllNodesWithText("0,00 €").onFirst().assertIsDisplayed()
    }

    @Test
    fun navigiertDurchAlleReiter() {
        // Reihenfolge so gewaehlt, dass die Beschriftung des Ziels immer nur in
        // der Navigationsleiste steht — auf dem eigenen Reiter steht sie auch in
        // der Titelzeile, und dann waere nicht klar, was angetippt wird.
        app.onNodeWithText("Konten").performClick()
        app.onNodeWithText("Noch keine Konten").assertIsDisplayed()

        app.onNodeWithText("Optionen").performClick()
        app.onNodeWithText("Kontoregel").assertIsDisplayed()

        app.onNodeWithText("Belege").performClick()
        app.onNodeWithText("Noch keine Einreichungen").assertIsDisplayed()

        app.onNodeWithText("Aktionen").performClick()
        app.onNodeWithText("Noch keine Aktionen").assertIsDisplayed()
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
        app.onNodeWithText("Optionen").performClick()

        app.onNodeWithText("Blockieren").performClick()
        app.onNodeWithText("Warnen").assertIsDisplayed()

        // Der Feed laesst sich anstossen — die Adresse dazu steht bewusst
        // nicht mehr hier, sie ist eine Einstellung der App.
        app.onNodeWithText("Jetzt aktualisieren").assertIsDisplayed()
        app.onAllNodesWithText("Feed-URL").assertCountEquals(0)
    }
}
