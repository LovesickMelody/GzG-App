package de.gzgtracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.gzgtracker.notify.Erinnerungen

@HiltAndroidApp
class GzgApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Muss vor der ersten Meldung stehen: Ohne Kanal zeigt Android sie
        // wortlos gar nicht an.
        Erinnerungen.legeKanalAn(this)
    }
}
