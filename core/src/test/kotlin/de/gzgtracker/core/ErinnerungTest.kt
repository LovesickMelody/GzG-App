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
