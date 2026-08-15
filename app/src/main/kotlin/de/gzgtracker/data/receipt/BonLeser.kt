package de.gzgtracker.data.receipt

import android.content.Context
import android.net.Uri
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
    suspend fun auswerten(pfad: String): Bonauswertung = withContext(Dispatchers.Default) {
        val datei = File(pfad)
        if (!datei.exists()) return@withContext Bonauswertung()

        val text = runCatching { erkenneText(Uri.fromFile(datei)) }
            .onFailure { Log.w(TAG, "Texterkennung auf $pfad fehlgeschlagen", it) }
            .getOrNull()
            ?: return@withContext Bonauswertung()

        Kassenbon.auswerten(text)
    }

    private suspend fun erkenneText(quelle: Uri): String =
        suspendCancellableCoroutine { fortsetzung ->
            val bild = InputImage.fromFilePath(context, quelle)
            erkenner.process(bild)
                .addOnSuccessListener { ergebnis -> fortsetzung.resume(ergebnis.text) }
                .addOnFailureListener { fehler ->
                    Log.w(TAG, "Texterkennung fehlgeschlagen", fehler)
                    // Kein Grund, den Aufrufer scheitern zu lassen: Ohne Text
                    // gibt es eben keinen Vorschlag.
                    fortsetzung.resume("")
                }
        }

    private companion object {
        const val TAG = "BonLeser"
    }
}
