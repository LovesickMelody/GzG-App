package de.gzgtracker.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import de.gzgtracker.core.Bonauswertung
import de.gzgtracker.core.Kassenbon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Liest Preis und Kaufdatum aus einem fotografierten Kassenbon.
 *
 * Die Texterkennung laeuft vollstaendig auf dem Geraet — das Modell ist in die
 * App einkompiliert. Kein Netz, kein Dienst, kein Bon verlaesst das Telefon.
 * Das ist hier keine Kleinigkeit: Auf einem Kassenbon steht, was jemand wann
 * wo gekauft hat.
 *
 * Die eigentliche Auswertung steckt in [Kassenbon] im Modul `:core` und ist
 * dort ohne Android getestet. Hier steht nur der Weg vom Bild zum Text.
 */
/**
 * Was beim Lesen eines Bons herauskam — und wenn nichts, warum nicht.
 *
 * Der Unterschied zaehlt: "Bild nicht lesbar", "Erkennung abgestuerzt" und
 * "kein Text gefunden" fuehren zu drei verschiedenen naechsten Schritten. Vorher
 * sahen alle drei gleich aus, und daran war nicht zu erkennen, woran es lag.
 */
data class Bonergebnis(
    val auswertung: Bonauswertung = Bonauswertung(),
    val fehler: String? = null,
)

@Singleton
class BonLeser @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val erkenner by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Wertet das Bild unter [pfad] aus.
     *
     * Gibt eine leere [Bonauswertung] zurueck, wenn nichts Brauchbares
     * herauskommt — ein fehlender Vorschlag ist harmlos, ein falscher nicht.
     */
    suspend fun auswerten(pfad: String): Bonergebnis = withContext(Dispatchers.Default) {
        val datei = File(pfad)
        if (!datei.exists()) {
            return@withContext Bonergebnis(fehler = "Das Bild wurde nicht gefunden.")
        }

        // Bewusst selbst dekodieren statt InputImage.fromFilePath: Der Weg ueber
        // die Adresse geht ueber den ContentResolver und scheitert je nach
        // Android-Fassung still. Ein Bitmap in der Hand ist eindeutig.
        val bitmap = runCatching { BitmapFactory.decodeFile(pfad) }.getOrNull()
            ?: return@withContext Bonergebnis(fehler = "Das Bild ließ sich nicht öffnen.")

        val text = try {
            erkenneText(bitmap)
        } catch (fehler: Exception) {
            Log.w(TAG, "Texterkennung auf $pfad fehlgeschlagen", fehler)
            return@withContext Bonergebnis(
                fehler = "Texterkennung nicht möglich: ${fehler.javaClass.simpleName}",
            )
        } finally {
            bitmap.recycle()
        }

        if (text.isBlank()) {
            // Erkennung lief, fand aber nichts. Das ist etwas anderes als ein
            // Fehler — und der Rat an den Nutzer ist ein anderer.
            return@withContext Bonergebnis(
                fehler = "Auf dem Bild war kein Text zu lesen. Näher ran, mehr Licht.",
            )
        }

        Log.i(TAG, "Bon gelesen: ${text.length} Zeichen, ${text.lines().size} Zeilen")
        Bonergebnis(auswertung = Kassenbon.auswerten(text))
    }

    private suspend fun erkenneText(bitmap: Bitmap): String =
        suspendCancellableCoroutine { fortsetzung ->
            // Drehung 0: Das Bild wurde beim Uebernehmen schon aufgerichtet.
            erkenner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { ergebnis -> fortsetzung.resume(ergebnis.text) }
                .addOnFailureListener { fehler -> fortsetzung.resumeWithException(fehler) }
        }

    private companion object {
        const val TAG = "BonLeser"
    }
}
