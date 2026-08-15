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
import de.gzgtracker.core.Textstueck
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
     * [produkt] ist der Name des Aktionsprodukts. Steht er da, sucht die
     * Auswertung den Posten dieses Produkts statt der Bonsumme — erstattet wird
     * das Produkt, nicht der ganze Einkauf.
     *
     * Gibt eine leere [Bonauswertung] zurueck, wenn nichts Brauchbares
     * herauskommt — ein fehlender Vorschlag ist harmlos, ein falscher nicht.
     */
    suspend fun auswerten(
        pfad: String,
        produkt: String? = null,
    ): Bonergebnis = withContext(Dispatchers.Default) {
        val datei = File(pfad)
        if (!datei.exists()) {
            return@withContext Bonergebnis(fehler = "Das Bild wurde nicht gefunden.")
        }

        // Bewusst selbst dekodieren statt InputImage.fromFilePath: Der Weg ueber
        // die Adresse geht ueber den ContentResolver und scheitert je nach
        // Android-Fassung still. Ein Bitmap in der Hand ist eindeutig.
        val bitmap = runCatching { BitmapFactory.decodeFile(pfad) }.getOrNull()
            ?: return@withContext Bonergebnis(fehler = "Das Bild ließ sich nicht öffnen.")

        val erkannt = try {
            erkenneText(bitmap)
        } catch (fehler: Exception) {
            Log.w(TAG, "Texterkennung auf $pfad fehlgeschlagen", fehler)
            return@withContext Bonergebnis(
                fehler = "Texterkennung nicht möglich: ${fehler.javaClass.simpleName}",
            )
        } finally {
            bitmap.recycle()
        }

        if (erkannt.stuecke.isEmpty() && erkannt.roh.isBlank()) {
            // Erkennung lief, fand aber nichts. Das ist etwas anderes als ein
            // Fehler — und der Rat an den Nutzer ist ein anderer.
            return@withContext Bonergebnis(
                fehler = "Auf dem Bild war kein Text zu lesen. Näher ran, mehr Licht.",
            )
        }

        Log.i(TAG, "Bon gelesen: ${erkannt.stuecke.size} Zeilen mit Rahmen")

        // Mit Rahmen laesst sich die Zeile wiederherstellen, in der Name und
        // Betrag auf dem Papier nebeneinander standen. Ohne Rahmen bleibt nur
        // der rohe Text — besser als nichts, aber ungenau.
        val auswertung = if (erkannt.stuecke.isNotEmpty()) {
            Kassenbon.auswerten(erkannt.stuecke, produkt = produkt)
        } else {
            Kassenbon.auswerten(erkannt.roh, produkt = produkt)
        }
        Bonergebnis(auswertung = auswertung)
    }

    /** Was die Texterkennung hergab: Zeilen mit Lage, dazu der rohe Text. */
    private data class Erkanntes(val stuecke: List<Textstueck>, val roh: String)

    private suspend fun erkenneText(bitmap: Bitmap): Erkanntes =
        suspendCancellableCoroutine { fortsetzung ->
            // Drehung 0: Das Bild wurde beim Uebernehmen schon aufgerichtet.
            erkenner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { ergebnis ->
                    val stuecke = ergebnis.textBlocks
                        .flatMap { block -> block.lines }
                        .mapNotNull { zeile ->
                            val rahmen = zeile.boundingBox ?: return@mapNotNull null
                            Textstueck(
                                text = zeile.text,
                                links = rahmen.left,
                                oben = rahmen.top,
                                unten = rahmen.bottom,
                            )
                        }
                    fortsetzung.resume(Erkanntes(stuecke, ergebnis.text))
                }
                .addOnFailureListener { fehler -> fortsetzung.resumeWithException(fehler) }
        }

    private companion object {
        const val TAG = "BonLeser"
    }
}
