package de.gzgtracker.ui.erfassen

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Zielort fuer ein frisch aufgenommenes Foto.
 *
 * Die Kamera-App schreibt nicht in unseren privaten Ordner, sondern in eine
 * Datei, auf die wir ihr per FileProvider Zugriff geben. Danach uebernimmt
 * [de.gzgtracker.data.receipt.ReceiptStorage] das Bild verkleinert in den
 * App-Speicher; die Zwischendatei liegt im Cache und darf jederzeit weg.
 *
 * Bewusst kein eigener Kamera-Bildschirm mit CameraX: Die Kamera-App des
 * Geraets kann Autofokus, Blitz und Belichtung besser, als wir sie nachbauen
 * wuerden — und bei einem Kassenbon in schlechtem Licht zaehlt genau das.
 */
fun kameraZiel(context: Context): Uri {
    val ordner = File(context.cacheDir, "kamera").apply { mkdirs() }
    val datei = File(ordner, "aufnahme-${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        datei,
    )
}
