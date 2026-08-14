package de.gzgtracker.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import de.gzgtracker.core.Account
import de.gzgtracker.core.CsvExport
import de.gzgtracker.core.PromoAction
import de.gzgtracker.core.Submission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schreibt die CSV in den Cache und gibt eine Content-Uri fuer das Share-Sheet zurueck.
 *
 * Der Cache ist absichtlich gewaehlt: Der Export ist eine Momentaufnahme zum
 * Weitergeben, kein Dokument, das die App verwalten muesste. Android raeumt ihn
 * bei Platzmangel selbst auf.
 */
@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun schreibe(
        submissions: List<Submission>,
        actionsById: Map<String, PromoAction>,
        accountsById: Map<Long, Account>,
        heute: LocalDate = LocalDate.now(),
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val ordner = File(context.cacheDir, "export").apply { mkdirs() }

            // Alten Export wegraeumen, damit sich im Cache nichts stapelt.
            ordner.listFiles()?.forEach { it.delete() }

            val datei = File(ordner, "gzg-tracker-$heute.csv")
            datei.writeText(
                CsvExport.erzeuge(submissions, actionsById, accountsById),
                Charsets.UTF_8,
            )

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                datei,
            )
        }.getOrNull()
    }
}
