package de.gzgtracker.core

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
