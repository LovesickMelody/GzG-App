package de.gzgtracker.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Wann an eine Aktion erinnert wird.
 *
 * Der Sinn der Erinnerung ist, dass man **rechtzeitig** einreicht — nicht, dass man
 * erfährt, dass es zu spät war. Deshalb liegt der Regelfall drei Tage vor dem
 * Einsendeschluss: genug Zeit, um das Produkt noch zu kaufen, den Bon zu suchen und
 * das Formular auszufüllen.
 *
 * Ist es dafür schon zu spät, rückt der Zeitpunkt nach, statt zu entfallen. Eine
 * Aktion, die man heute noch einreichen kann, ist die dringendste von allen.
 */
object Erinnerung {

    /** Uhrzeit der Erinnerung: vormittags, wenn der Tag noch offen ist. */
    const val STUNDE = 10

    /**
     * Gibt den Zeitpunkt zurück, zu dem erinnert werden soll — oder `null`, wenn es
     * nichts mehr zu erinnern gibt.
     *
     * @param frist der Einsendeschluss
     * @param jetzt der aktuelle Zeitpunkt
     */
    fun zeitpunkt(frist: LocalDate, jetzt: LocalDateTime): LocalDateTime? {
        // Vorbei ist vorbei. Eine Erinnerung an eine abgelaufene Aktion waere
        // nur noch aergerlich.
        if (frist.isBefore(jetzt.toLocalDate())) return null

        val regelfall = frist.minusDays(VORLAUF_TAGE).atTime(STUNDE, 0)
        if (regelfall.isAfter(jetzt)) return regelfall

        // Zu spaet fuer den Regelfall: morgen frueh, solange die Frist das noch
        // hergibt.
        val morgen = jetzt.toLocalDate().plusDays(1).atTime(STUNDE, 0)
        if (!morgen.toLocalDate().isAfter(frist) && morgen.isAfter(jetzt)) return morgen

        // Frist heute oder morgen frueh — dann eben gleich, aber mit etwas Luft,
        // damit die Meldung nicht im selben Moment aufpoppt, in dem man sie stellt.
        val gleich = jetzt.plusHours(2)
        return gleich.takeIf { !it.toLocalDate().isAfter(frist) }
    }

    /** Wie viele Tage vor der Frist im Regelfall erinnert wird. */
    private const val VORLAUF_TAGE = 3L
}

/**
 * Eine Zuruecksetzung, wie sie der Sammellauf als Text hinterlaesst:
 * "Montags um 08:00 Uhr", "Täglich um 00:00 Uhr", "Montags".
 *
 * Der Text ist fuer Menschen gedacht, der Wecker braucht Zahlen. Die Umwandlung
 * steht hier und nicht im Sammellauf, damit sich die Formulierung aendern laesst,
 * ohne den naechsten Lauf abzuwarten.
 */
data class Zuruecksetzung(
    /** `null` heisst: jeden Tag. */
    val wochentag: DayOfWeek? = null,
    val stunde: Int = 0,
    val minute: Int = 0,
) {
    /** Wie oft es sich wiederholt — die Grundlage fuer den wiederkehrenden Wecker. */
    val abstandTage: Long get() = if (wochentag == null) 1L else 7L
}

/**
 * Erinnerungen an den Moment, in dem ein Kontingent neu freigeschaltet wird.
 *
 * Bei gedeckelten Aktionen entscheidet nicht die Frist, sondern die Minute:
 * "1.000 pro Woche, montags ab 08:00 Uhr" heisst, dass um 08:05 nichts mehr da
 * sein kann. Deshalb weckt diese Erinnerung **vorher**.
 */
object Kontingenterinnerung {

    /** Wie viele Minuten vor der Freischaltung geweckt wird. */
    const val VORLAUF_MINUTEN = 5L

    private val WOCHENTAGE = mapOf(
        "montag" to DayOfWeek.MONDAY,
        "dienstag" to DayOfWeek.TUESDAY,
        "mittwoch" to DayOfWeek.WEDNESDAY,
        "donnerstag" to DayOfWeek.THURSDAY,
        "freitag" to DayOfWeek.FRIDAY,
        "samstag" to DayOfWeek.SATURDAY,
        "sonntag" to DayOfWeek.SUNDAY,
    )

    private val UHRZEIT = Regex("""(\d{1,2}):(\d{2})""")

    /**
     * Liest den Text des Sammellaufs.
     *
     * Gibt `null` zurueck, wenn keine Uhrzeit dabeisteht: Ein Wecker ohne
     * Uhrzeit waere geraten, und geraten wird hier nicht.
     */
    fun lies(text: String?): Zuruecksetzung? {
        if (text.isNullOrBlank()) return null
        val klein = text.lowercase()

        val zeit = UHRZEIT.find(klein) ?: return null
        val stunde = zeit.groupValues[1].toIntOrNull() ?: return null
        val minute = zeit.groupValues[2].toIntOrNull() ?: return null
        if (stunde !in 0..23 || minute !in 0..59) return null

        val tag = WOCHENTAGE.entries.firstOrNull { klein.contains(it.key) }?.value
        val taeglich = klein.contains("täglich") || klein.contains("taeglich")
        if (tag == null && !taeglich) return null

        return Zuruecksetzung(wochentag = tag, stunde = stunde, minute = minute)
    }

    /**
     * Der naechste Weckzeitpunkt: kurz vor der naechsten Freischaltung.
     *
     * Liegt die heutige Freischaltung noch vor uns, gilt sie; sonst die
     * naechste. Der Vorlauf wird abgezogen, *nachdem* die Freischaltung
     * feststeht — sonst faende ein Wecker um 07:55 die Freischaltung um 08:00
     * schon nicht mehr.
     */
    fun naechsterWecker(
        zuruecksetzung: Zuruecksetzung,
        jetzt: LocalDateTime,
        vorlaufMinuten: Long = VORLAUF_MINUTEN,
    ): LocalDateTime {
        var freischaltung = jetzt.toLocalDate().atTime(zuruecksetzung.stunde, zuruecksetzung.minute)

        zuruecksetzung.wochentag?.let { tag ->
            var tage = (tag.value - freischaltung.dayOfWeek.value + 7) % 7
            if (tage == 0 && !freischaltung.minusMinutes(vorlaufMinuten).isAfter(jetzt)) tage = 7
            freischaltung = freischaltung.plusDays(tage.toLong())
        }

        if (zuruecksetzung.wochentag == null &&
            !freischaltung.minusMinutes(vorlaufMinuten).isAfter(jetzt)
        ) {
            freischaltung = freischaltung.plusDays(1)
        }

        return freischaltung.minusMinutes(vorlaufMinuten)
    }
}
