package de.gzgtracker.core

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ErinnerungTest {

    private val jetzt = LocalDateTime.of(2026, 8, 15, 14, 30)

    @Test
    fun `erinnert drei Tage vor der Frist am Vormittag`() {
        val zeitpunkt = Erinnerung.zeitpunkt(LocalDate.of(2026, 8, 31), jetzt)
        assertEquals(LocalDateTime.of(2026, 8, 28, 10, 0), zeitpunkt)
    }

    @Test
    fun `rueckt nach wenn der Regelfall schon vorbei ist`() {
        // Frist in zwei Tagen: Drei Tage vorher waere gestern gewesen.
        val zeitpunkt = Erinnerung.zeitpunkt(LocalDate.of(2026, 8, 17), jetzt)
        assertEquals(LocalDateTime.of(2026, 8, 16, 10, 0), zeitpunkt)
    }

    @Test
    fun `erinnert am selben Tag mit etwas Luft`() {
        // Frist heute: Morgen frueh ist zu spaet, also gleich — aber nicht sofort.
        val zeitpunkt = Erinnerung.zeitpunkt(LocalDate.of(2026, 8, 15), jetzt)
        assertEquals(LocalDateTime.of(2026, 8, 15, 16, 30), zeitpunkt)
    }

    @Test
    fun `keine Erinnerung wenn die Frist vorbei ist`() {
        assertNull(Erinnerung.zeitpunkt(LocalDate.of(2026, 8, 14), jetzt))
    }

    @Test
    fun `keine Erinnerung wenn heute nichts mehr geht`() {
        // Kurz vor Mitternacht am letzten Tag: Zwei Stunden spaeter ist die Frist
        // schon abgelaufen.
        val spaet = LocalDateTime.of(2026, 8, 15, 23, 30)
        assertNull(Erinnerung.zeitpunkt(LocalDate.of(2026, 8, 15), spaet))
    }

    @Test
    fun `erinnert morgen frueh wenn die Frist morgen ist`() {
        val zeitpunkt = Erinnerung.zeitpunkt(LocalDate.of(2026, 8, 16), jetzt)
        assertEquals(LocalDateTime.of(2026, 8, 16, 10, 0), zeitpunkt)
    }

    @Test
    fun `der Zeitpunkt liegt nie in der Vergangenheit`() {
        // Ueber ein Jahr an Fristen durchgespielt: Was herauskommt, liegt immer
        // vor der Frist und nach jetzt.
        var frist = jetzt.toLocalDate()
        repeat(400) {
            val zeitpunkt = Erinnerung.zeitpunkt(frist, jetzt)
            if (zeitpunkt != null) {
                assertTrue(zeitpunkt.isAfter(jetzt), "Erinnerung für $frist läge in der Vergangenheit")
                assertTrue(
                    !zeitpunkt.toLocalDate().isAfter(frist),
                    "Erinnerung für $frist läge nach der Frist",
                )
            }
            frist = frist.plusDays(1)
        }
    }
}

/**
 * Der Wecker auf die Freischaltung. Bei "1.000 pro Woche, montags ab 08:00 Uhr"
 * entscheidet nicht die Frist, sondern die Minute.
 */
class KontingenterinnerungTest {

    // Ein Samstag.
    private val samstag = LocalDateTime.of(2026, 8, 15, 14, 30)

    @Test
    fun `liest Wochentag und Uhrzeit`() {
        val gelesen = Kontingenterinnerung.lies("Montags um 08:00 Uhr")
        assertEquals(java.time.DayOfWeek.MONDAY, gelesen?.wochentag)
        assertEquals(8, gelesen?.stunde)
        assertEquals(0, gelesen?.minute)
        assertEquals(7L, gelesen?.abstandTage)
    }

    @Test
    fun `liest taeglich`() {
        val gelesen = Kontingenterinnerung.lies("Täglich um 00:00 Uhr")
        assertNull(gelesen?.wochentag)
        assertEquals(1L, gelesen?.abstandTage)
    }

    @Test
    fun `ohne Uhrzeit gibt es keinen Wecker`() {
        // "Montags" allein waere geraten — und geraten wird hier nicht.
        assertNull(Kontingenterinnerung.lies("Montags"))
        assertNull(Kontingenterinnerung.lies(null))
        assertNull(Kontingenterinnerung.lies("Monatlich"))
    }

    @Test
    fun `weckt fuenf Minuten vor der Freischaltung`() {
        val wecker = Kontingenterinnerung.naechsterWecker(
            Kontingenterinnerung.lies("Montags um 08:00 Uhr")!!,
            samstag,
        )
        // Der naechste Montag ist der 17.08.
        assertEquals(LocalDateTime.of(2026, 8, 17, 7, 55), wecker)
    }

    @Test
    fun `nimmt heute wenn die Freischaltung noch bevorsteht`() {
        val montagFrueh = LocalDateTime.of(2026, 8, 17, 6, 0)
        val wecker = Kontingenterinnerung.naechsterWecker(
            Kontingenterinnerung.lies("Montags um 08:00 Uhr")!!,
            montagFrueh,
        )
        assertEquals(LocalDateTime.of(2026, 8, 17, 7, 55), wecker)
    }

    @Test
    fun `springt auf naechste Woche wenn der Vorlauf schon vorbei ist`() {
        // 07:56 am Montag: Fuer 07:55 ist es zu spaet, also naechste Woche.
        val knappVorbei = LocalDateTime.of(2026, 8, 17, 7, 56)
        val wecker = Kontingenterinnerung.naechsterWecker(
            Kontingenterinnerung.lies("Montags um 08:00 Uhr")!!,
            knappVorbei,
        )
        assertEquals(LocalDateTime.of(2026, 8, 24, 7, 55), wecker)
    }

    @Test
    fun `taeglich springt auf morgen`() {
        val wecker = Kontingenterinnerung.naechsterWecker(
            Kontingenterinnerung.lies("Täglich um 09:00 Uhr")!!,
            samstag,
        )
        assertEquals(LocalDateTime.of(2026, 8, 16, 8, 55), wecker)
    }

    @Test
    fun `der Wecker liegt immer in der Zukunft`() {
        val zuruecksetzung = Kontingenterinnerung.lies("Montags um 08:00 Uhr")!!
        var jetzt = samstag
        repeat(200) {
            val wecker = Kontingenterinnerung.naechsterWecker(zuruecksetzung, jetzt)
            assertTrue(wecker.isAfter(jetzt), "Wecker $wecker läge vor $jetzt")
            jetzt = jetzt.plusHours(1)
        }
    }
}
