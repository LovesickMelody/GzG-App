package de.gzgtracker

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import de.gzgtracker.data.repository.ActionRepository
import de.gzgtracker.notify.Erinnerungen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GzgApplication : Application() {

    @Inject
    lateinit var repository: ActionRepository

    private val bereich = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Muss vor der ersten Meldung stehen: Ohne Kanal zeigt Android sie
        // wortlos gar nicht an.
        Erinnerungen.legeKanalAn(this)

        // Wecker ueberleben keinen Neustart. Den Regelfall faengt der
        // NeustartEmpfaenger ab; hier steht der Guertel zum Hosentraeger — etwa
        // wenn der Empfaenger von einer Akkusparfunktion uebergangen wurde oder
        // die Daten aus einer Sicherung kamen. Doppelt Stellen schadet nicht,
        // der Wecker wird dabei ersetzt.
        bereich.launch {
            try {
                repository.stelleErinnerungenNeu()
            } catch (fehler: Exception) {
                // Beim App-Start darf daran nichts haengenbleiben.
                Log.w("GzgApplication", "Erinnerungen nicht neu gestellt", fehler)
            }
        }
    }
}
