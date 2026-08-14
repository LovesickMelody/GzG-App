package de.gzgtracker.ui.scan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage

/**
 * Liest EAN-13 und EAN-8 aus dem Kamerabild.
 *
 * Nur diese beiden Formate sind eingeschaltet: Weniger Formate heisst schnellere
 * Erkennung und keine Fehltreffer auf QR-Codes, die auf Verpackungen herumstehen.
 *
 * Derselbe Code wird erst nach [BESTAETIGUNGEN] aufeinanderfolgenden Erkennungen
 * gemeldet. Einzelne Fehllesungen bei unscharfem Bild kommen vor; ein falscher
 * Treffer waere hier teurer als ein Sekundenbruchteil Wartezeit.
 */
class BarcodeAnalyzer(
    private val onTreffer: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
            .build(),
    )

    private var letzterCode: String? = null
    private var wiederholungen = 0
    private var gemeldet = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(bild: ImageProxy) {
        val medienBild = bild.image
        if (medienBild == null || gemeldet) {
            bild.close()
            return
        }

        val eingabe = InputImage.fromMediaImage(medienBild, bild.imageInfo.rotationDegrees)
        scanner.process(eingabe)
            .addOnSuccessListener { codes ->
                val code = codes.firstNotNullOfOrNull { it.rawValue }?.takeIf { istPlausibel(it) }
                pruefe(code)
            }
            .addOnCompleteListener { bild.close() }
    }

    private fun pruefe(code: String?) {
        if (code == null) {
            letzterCode = null
            wiederholungen = 0
            return
        }

        if (code == letzterCode) {
            wiederholungen++
        } else {
            letzterCode = code
            wiederholungen = 1
        }

        if (wiederholungen >= BESTAETIGUNGEN && !gemeldet) {
            gemeldet = true
            onTreffer(code)
        }
    }

    /** Freigeben, wenn der Scan-Screen verschwindet. */
    fun schliessen() {
        scanner.close()
    }

    private fun istPlausibel(code: String): Boolean =
        (code.length == 8 || code.length == 13) && code.all(Char::isDigit)

    private companion object {
        const val BESTAETIGUNGEN = 2
    }
}
