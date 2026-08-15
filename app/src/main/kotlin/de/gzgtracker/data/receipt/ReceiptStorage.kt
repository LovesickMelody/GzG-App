package de.gzgtracker.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bonfotos liegen app-intern unter `files/receipts`. Kein MediaStore, keine
 * Galerie — die Belege gehen niemanden ausser der App etwas an, und so braucht sie
 * auch keine Speicherberechtigung.
 *
 * Bilder werden beim Uebernehmen auf maximal 2000 px lange Kante herunterskaliert
 * und als JPEG mit Qualitaet 85 abgelegt. Ein Kassenbon bleibt so bequem lesbar,
 * aber ein Jahr Sammeln fuellt nicht den Geraetespeicher.
 */
@Singleton
class ReceiptStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val ordner: File
        get() = File(context.filesDir, ORDNER).apply { mkdirs() }

    /**
     * Kopiert das Bild hinter [quelle] in den App-Speicher und gibt den absoluten
     * Pfad zurueck. `null`, wenn sich das Bild nicht lesen liess.
     */
    suspend fun uebernehmen(quelle: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = ladeVerkleinert(quelle) ?: return@runCatching null
            val ziel = File(ordner, "bon-${UUID.randomUUID()}.jpg")
            ziel.outputStream().use { strom ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITAET, strom)
            }
            bitmap.recycle()
            ziel.absolutePath
        }.onFailure { fehler ->
            // Ohne diese Zeile steht im Fehlerfall nur "ließ sich nicht laden"
            // und niemand weiss, woran es lag.
            Log.w(TAG, "Bild $quelle nicht übernommen", fehler)
        }.getOrNull()
    }

    /** Loescht ein Bonfoto, etwa wenn im Formular ein anderes gewaehlt wird. */
    suspend fun loeschen(pfad: String?) = withContext(Dispatchers.IO) {
        if (pfad.isNullOrBlank()) return@withContext
        val datei = File(pfad)
        // Nur innerhalb des eigenen Ordners loeschen.
        if (datei.parentFile?.absolutePath == ordner.absolutePath) {
            datei.delete()
        }
    }

    fun existiert(pfad: String?): Boolean =
        !pfad.isNullOrBlank() && File(pfad).exists()

    /** Zielort fuer ein neues Kamerabild — die Kamera schreibt direkt dorthin. */
    fun neueDatei(): File = File(ordner, "bon-${UUID.randomUUID()}.jpg")

    private fun ladeVerkleinert(quelle: Uri): Bitmap? {
        // ACHTUNG: Mit inJustDecodeBounds gibt decodeStream absichtlich null
        // zurueck — es misst nur. Ein `?: return null` hinter dem use-Block
        // haette also bei *jedem* Bild abgebrochen, bevor ueberhaupt etwas
        // geladen wird. Genau das war der Fehler "Das Bild liess sich nicht
        // laden". Der Strom wird deshalb einzeln geprueft, nicht das Ergebnis.
        val masse = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val messstrom = context.contentResolver.openInputStream(quelle) ?: return null
        messstrom.use { BitmapFactory.decodeStream(it, null, masse) }

        val laengsteKante = maxOf(masse.outWidth, masse.outHeight)
        if (laengsteKante <= 0) return null

        val optionen = BitmapFactory.Options().apply {
            inSampleSize = generateSequence(1) { it * 2 }
                .first { schritt -> laengsteKante / schritt <= MAX_KANTE }
        }

        val ladestrom = context.contentResolver.openInputStream(quelle) ?: return null
        val bitmap = ladestrom.use {
            BitmapFactory.decodeStream(it, null, optionen)
        } ?: return null

        return drehen(quelle, bitmap)
    }

    /**
     * Hochkant fotografierte Bons kommen sonst liegend an: Die Rotation steckt nur
     * im EXIF-Header, den `BitmapFactory` ignoriert.
     */
    private fun drehen(quelle: Uri, bitmap: Bitmap): Bitmap {
        val grad = runCatching {
            context.contentResolver.openInputStream(quelle)?.use { strom ->
                when (
                    ExifInterface(strom).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        if (grad == 0f) return bitmap

        val gedreht = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(grad) },
            true,
        )
        if (gedreht != bitmap) bitmap.recycle()
        return gedreht
    }

    private companion object {
        const val TAG = "ReceiptStorage"
        const val ORDNER = "receipts"

        /**
         * Ein ganzer Kassenbon auf 2000 Punkten liess die Texterkennung nur
         * noch aus naechster Naehe etwas lesen — bei Aktionen, die den
         * vollstaendigen Bon verlangen, geht das nicht. 3200 Punkte kosten
         * etwa ein Megabyte mehr je Beleg und machen die Ziffern wieder
         * lesbar.
         */
        const val MAX_KANTE = 3200
        const val QUALITAET = 88
    }
}
