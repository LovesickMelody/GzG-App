package de.gzgtracker.data.receipt

import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import de.gzgtracker.core.Ean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Liest die EAN aus dem Foto der Produktpackung.
 *
 * Warum aus dem Foto und nicht aus einem Sucher: Das Produktfoto wird für die
 * Einreichung ohnehin gemacht, und der Strichcode ist auf fast jeder Packung
 * mit drauf. Ein eigener Scan-Bildschirm hieße, dasselbe Produkt zweimal vor
 * die Kamera zu halten — der wurde deshalb schon einmal entfernt und kommt
 * nicht zurück.
 *
 * Die Erkennung läuft wie die Texterkennung vollständig auf dem Gerät.
 *
 * Findet sich nichts, ist das kein Fehler: Auf vielen Fotos ist der Strichcode
 * verdeckt oder unscharf. Dann bleibt das Feld eben leer — es ist optional.
 */
@Singleton
class EanLeser @Inject constructor() {

    // Nur die Handelsformate. Ein QR-Code auf der Packung führt zur Website des
    // Herstellers und hat im EAN-Feld nichts zu suchen.
    private val leser by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                )
                .build(),
        )
    }

    /**
     * Gibt die gefundene EAN zurück, oder `null`.
     *
     * Geprüft wird die Prüfziffer, bevor etwas zurückkommt: Die Nummer wandert
     * später womöglich ungesehen ins Formular des Anbieters, und eine falsche
     * Ziffer fällt erst auf, wenn die Erstattung ausbleibt.
     */
    suspend fun lies(pfad: String): String? = withContext(Dispatchers.Default) {
        val bitmap = runCatching { BitmapFactory.decodeFile(pfad) }.getOrNull() ?: return@withContext null

        val gefunden = try {
            erkenne(bitmap)
        } catch (fehler: Exception) {
            Log.w(TAG, "Strichcode auf $pfad nicht lesbar", fehler)
            null
        } finally {
            bitmap.recycle()
        }

        gefunden?.also { Log.i(TAG, "EAN erkannt: $it") }
    }

    private suspend fun erkenne(bitmap: android.graphics.Bitmap): String? =
        suspendCancellableCoroutine { fortsetzung ->
            // Drehung 0: Das Bild wurde beim Uebernehmen schon aufgerichtet.
            leser.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { codes ->
                    // Der erste, dessen Pruefziffer stimmt. Mehrere Strichcodes
                    // auf einem Bild sind selten, aber moeglich — etwa der Code
                    // des Herstellers neben dem Preisschild des Ladens.
                    fortsetzung.resume(codes.firstNotNullOfOrNull { Ean.pruefe(it.rawValue) })
                }
                .addOnFailureListener { fehler -> fortsetzung.resumeWithException(fehler) }
        }

    private companion object {
        const val TAG = "EanLeser"
    }
}
