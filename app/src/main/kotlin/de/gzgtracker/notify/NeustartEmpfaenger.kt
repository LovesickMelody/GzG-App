package de.gzgtracker.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import de.gzgtracker.data.repository.ActionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Stellt die Erinnerungen nach einem Neustart wieder.
 *
 * Android verwirft beim Ausschalten **alle** Wecker. Ohne diesen Empfaenger
 * verschwindet damit jede gestellte Erinnerung — und zwar still: Wer sich auf
 * die Meldung verlassen hat, merkt es erst, wenn die Frist vorbei ist. Genau
 * dagegen ist die Funktion gebaut.
 *
 * Fristen liegen typischerweise Wochen in der Zukunft. Ein Neustart in der
 * Zwischenzeit ist praktisch sicher — Systemupdate, leerer Akku, einmal
 * ausgeschaltet.
 *
 * ``MY_PACKAGE_REPLACED`` steht aus demselben Grund daneben: Auch ein Update
 * der App raeumt ihre Wecker ab.
 *
 * Der Empfaenger ist nicht exportiert; diese beiden Meldungen schickt nur das
 * System selbst.
 */
@AndroidEntryPoint
class NeustartEmpfaenger : BroadcastReceiver() {

    @Inject
    lateinit var repository: ActionRepository

    override fun onReceive(context: Context, absicht: Intent) {
        val grund = absicht.action
        if (grund != Intent.ACTION_BOOT_COMPLETED &&
            grund != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        // goAsync haelt den Empfaenger am Leben, bis die Datenbank gelesen ist.
        // Ohne das killt Android den Prozess mitten in der Abfrage, und die
        // Erinnerungen waeren genauso weg wie ohne Empfaenger.
        val fertig = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.stelleErinnerungenNeu()
                Log.i(TAG, "Erinnerungen nach $grund neu gestellt")
            } catch (fehler: Exception) {
                // Ein Fehler hier darf das Telefon nicht beim Starten stoeren.
                Log.w(TAG, "Erinnerungen konnten nicht neu gestellt werden", fehler)
            } finally {
                fertig.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GzgNeustart"
    }
}
