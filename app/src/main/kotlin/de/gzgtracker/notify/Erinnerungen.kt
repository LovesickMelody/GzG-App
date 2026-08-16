package de.gzgtracker.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.gzgtracker.MainActivity
import de.gzgtracker.R
import java.time.Instant

/**
 * Erinnerungen an ablaufende Aktionen.
 *
 * Bewusst mit dem [AlarmManager] und ohne zusaetzliche Bibliothek: Es geht um eine
 * Meldung zu einem Zeitpunkt, mehr nicht. Der Alarm ist **ungenau** gestellt, aber
 * doze-fest — die Begruendung steht bei [stelle].
 *
 * Was das System nicht kann: Alarme ueberleben keinen Neustart des Telefons. Deshalb
 * liegt jede gestellte Erinnerung auch in der Datenbank, und der `NeustartEmpfaenger`
 * stellt sie nach dem Hochfahren wieder.
 */
object Erinnerungen {

    const val KANAL = "fristen"

    private const val TAG = "Erinnerungen"

    const val EXTRA_AKTION = "aktionId"
    const val EXTRA_TITEL = "titel"

    /**
     * Legt den Benachrichtigungskanal an.
     *
     * Ab Android 8 fuehrt kein Weg daran vorbei: Ohne Kanal zeigt das System die
     * Meldung wortlos gar nicht an.
     */
    fun legeKanalAn(context: Context) {
        val kanal = NotificationChannel(
            KANAL,
            "Fristen",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Erinnert, bevor der Einsendeschluss einer Aktion abläuft."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(kanal)
    }

    /** True, wenn die App Meldungen anzeigen darf. */
    fun darfMelden(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Stellt den Wecker für eine Aktion. Ein vorhandener wird ersetzt.
     *
     * `setAndAllowWhileIdle` statt `set`: Ein gewoehnlicher Wecker wird im
     * Doze-Modus bis zum naechsten Wartungsfenster zurueckgehalten, und das kann
     * ueber Nacht Stunden bedeuten. Eine Erinnerung, die am Tag des
     * Einsendeschlusses erst am Nachmittag ankommt, kommt zu spaet.
     *
     * `AndWhileIdle` weckt auch aus Doze heraus und braucht trotzdem **keine**
     * Sonderberechtigung — anders als ein exakter Wecker. Genau ist der Wecker
     * damit weiterhin nicht, und das ist richtig so: Das System darf ihn
     * verschieben und buendeln, es laesst ihn nur nicht mehr liegen.
     */
    fun stelle(context: Context, aktionId: String, titel: String, faelligAm: Instant) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            faelligAm.toEpochMilli(),
            absicht(context, aktionId, titel),
        )
        Log.i(TAG, "Erinnerung für $aktionId auf $faelligAm gestellt")
    }

    /** Nimmt den Wecker zurück. */
    fun nimmZurueck(context: Context, aktionId: String) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(absicht(context, aktionId, titel = ""))
    }

    /**
     * Der Wecker selbst.
     *
     * `FLAG_UPDATE_CURRENT` sorgt dafuer, dass ein zweites Stellen denselben Wecker
     * ueberschreibt statt einen zweiten anzulegen — sonst meldete sich dieselbe
     * Aktion mehrfach.
     */
    private fun absicht(context: Context, aktionId: String, titel: String): PendingIntent {
        val intent = Intent(context, ErinnerungsEmpfaenger::class.java).apply {
            // Ohne eigene Adresse haelt das System zwei Absichten fuer gleich,
            // wenn sich nur die Extras unterscheiden — dann traegt der letzte
            // Wecker alle Aktionen.
            data = android.net.Uri.parse("gzg://erinnerung/$aktionId")
            putExtra(EXTRA_AKTION, aktionId)
            putExtra(EXTRA_TITEL, titel)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Nimmt den Wecker entgegen und zeigt die Meldung an. */
class ErinnerungsEmpfaenger : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val aktionId = intent.getStringExtra(Erinnerungen.EXTRA_AKTION) ?: return
        val titel = intent.getStringExtra(Erinnerungen.EXTRA_TITEL).orEmpty()

        // Tippen fuehrt in die App. Mehr als das braucht es nicht: Wo man
        // hinmuss, weiss man dann selbst.
        val oeffnen = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val meldung: Notification = NotificationCompat.Builder(context, Erinnerungen.KANAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Einsendeschluss rückt näher")
            .setContentText(titel.ifBlank { "Eine gemerkte Aktion läuft bald ab." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(titel))
            .setContentIntent(oeffnen)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Erinnerungen.darfMelden(context)
        ) {
            // Erlaubnis wurde zwischenzeitlich entzogen — dann eben nicht.
            return
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(aktionId.hashCode(), meldung)
        }
    }
}
