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
    const val EXTRA_ANLASS = "anlass"

    /** Abstand einer wiederkehrenden Erinnerung in Millisekunden; 0 = einmalig. */
    const val EXTRA_ABSTAND = "abstandMillis"

    /** Erinnert an eine ablaufende Einsendefrist. */
    const val ANLASS_FRIST = "frist"

    /** Erinnert kurz bevor ein Kontingent neu freigeschaltet wird. */
    const val ANLASS_FREISCHALTUNG = "freischaltung"

    /**
     * Legt den Benachrichtigungskanal an.
     *
     * Ab Android 8 fuehrt kein Weg daran vorbei: Ohne Kanal zeigt das System die
     * Meldung wortlos gar nicht an.
     */
    fun legeKanalAn(context: Context) {
        // Name und Beschreibung darf `createNotificationChannel` auch bei einem
        // bestehenden Kanal noch aendern — die Kennung nicht. Deshalb bleibt sie
        // "fristen", obwohl hier laengst mehr als Fristen ankommt.
        val kanal = NotificationChannel(
            KANAL,
            "Erinnerungen",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description =
                "Erinnert vor dem Einsendeschluss und kurz bevor ein Kontingent neu " +
                    "freigeschaltet wird."
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
     *
     * [abstandMillis] groesser null heisst: wiederkehrend, fuer Kontingente, die
     * jede Woche neu freigeschaltet werden. Bewusst **nicht** mit
     * `setInexactRepeating` — dessen Wecker sind nicht doze-fest, und
     * ausgerechnet bei fuenf Minuten Vorlauf auf eine Freischaltung waere ein
     * bis zum Wartungsfenster liegengebliebener Wecker wertlos. Stattdessen
     * wird immer nur der naechste Termin gestellt, und der
     * [ErinnerungsEmpfaenger] stellt nach dem Ausloesen den uebernaechsten.
     */
    fun stelle(
        context: Context,
        aktionId: String,
        titel: String,
        faelligAm: Instant,
        abstandMillis: Long = 0,
        anlass: String = ANLASS_FRIST,
    ) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            faelligAm.toEpochMilli(),
            absicht(context, aktionId, titel, anlass, abstandMillis),
        )
        Log.i(TAG, "Erinnerung für $aktionId auf $faelligAm gestellt (Abstand $abstandMillis)")
    }

    /** Nimmt den Wecker zurück. */
    fun nimmZurueck(context: Context, aktionId: String) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(absicht(context, aktionId, titel = "", anlass = ANLASS_FRIST))
    }

    /**
     * Der Wecker selbst.
     *
     * `FLAG_UPDATE_CURRENT` sorgt dafuer, dass ein zweites Stellen denselben Wecker
     * ueberschreibt statt einen zweiten anzulegen — sonst meldete sich dieselbe
     * Aktion mehrfach.
     */
    private fun absicht(
        context: Context,
        aktionId: String,
        titel: String,
        anlass: String,
        abstandMillis: Long = 0,
    ): PendingIntent {
        val intent = Intent(context, ErinnerungsEmpfaenger::class.java).apply {
            // Ohne eigene Adresse haelt das System zwei Absichten fuer gleich,
            // wenn sich nur die Extras unterscheiden — dann traegt der letzte
            // Wecker alle Aktionen.
            data = android.net.Uri.parse("gzg://erinnerung/$aktionId")
            putExtra(EXTRA_AKTION, aktionId)
            putExtra(EXTRA_TITEL, titel)
            putExtra(EXTRA_ANLASS, anlass)
            putExtra(EXTRA_ABSTAND, abstandMillis)
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
        val anlass = intent.getStringExtra(Erinnerungen.EXTRA_ANLASS)
        val abstand = intent.getLongExtra(Erinnerungen.EXTRA_ABSTAND, 0)

        // Wiederkehrende Erinnerung: gleich den naechsten Termin stellen. Das
        // System kennt keinen doze-festen Wiederholwecker, also stellt sich
        // dieser hier selbst neu — sonst kaeme die Meldung zur Freischaltung
        // genau einmal.
        //
        // Zuerst, noch vor dem Anzeigen: Faellt das Melden gleich durch eine
        // entzogene Erlaubnis, soll wenigstens die Kette nicht abreissen.
        if (abstand > 0) {
            Erinnerungen.stelle(
                context,
                aktionId,
                titel,
                Instant.now().plusMillis(abstand),
                abstandMillis = abstand,
                anlass = anlass ?: Erinnerungen.ANLASS_FREISCHALTUNG,
            )
        }

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

        val freischaltung = anlass == Erinnerungen.ANLASS_FREISCHALTUNG
        val text = titel.ifBlank {
            if (freischaltung) {
                "Gleich werden neue Plätze frei."
            } else {
                "Eine gemerkte Aktion läuft bald ab."
            }
        }

        val meldung: Notification = NotificationCompat.Builder(context, Erinnerungen.KANAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (freischaltung) "Gleich gibt es neue Plätze" else "Einsendeschluss rückt näher",
            )
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
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
