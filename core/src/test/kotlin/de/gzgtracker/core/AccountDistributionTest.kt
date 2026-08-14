package de.gzgtracker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountDistributionTest {

    private val konten = listOf(konto(1), konto(2), konto(3))

    // --- Duplikatspruefung ---------------------------------------------------

    @Test
    fun `freies Konto ist frei`() {
        val ergebnis = AccountDistribution.pruefe(
            actionId = "a1",
            accountId = 1,
            accounts = konten,
            submissions = emptyList(),
        )
        assertEquals(AccountCheck.Frei, ergebnis)
    }

    @Test
    fun `dasselbe Konto fuer dieselbe Aktion ist belegt`() {
        val bestehend = einreichung(id = 10, actionId = "a1", accountId = 1)

        val ergebnis = AccountDistribution.pruefe(
            actionId = "a1",
            accountId = 1,
            accounts = konten,
            submissions = listOf(bestehend),
        )

        val belegt = assertIs<AccountCheck.BereitsBelegt>(ergebnis)
        assertEquals(bestehend, belegt.konflikt)
    }

    @Test
    fun `dasselbe Konto fuer eine andere Aktion ist frei`() {
        val ergebnis = AccountDistribution.pruefe(
            actionId = "a2",
            accountId = 1,
            accounts = konten,
            submissions = listOf(einreichung(id = 10, actionId = "a1", accountId = 1)),
        )
        assertEquals(AccountCheck.Frei, ergebnis)
    }

    @Test
    fun `eine abgelehnte Einreichung gibt das Konto wieder frei`() {
        val ergebnis = AccountDistribution.pruefe(
            actionId = "a1",
            accountId = 1,
            accounts = konten,
            submissions = listOf(
                einreichung(
                    id = 10,
                    actionId = "a1",
                    accountId = 1,
                    status = SubmissionStatus.ABGELEHNT,
                ),
            ),
        )
        assertEquals(AccountCheck.Frei, ergebnis)
    }

    @Test
    fun `ein erstattetes Konto bleibt belegt`() {
        val ergebnis = AccountDistribution.pruefe(
            actionId = "a1",
            accountId = 1,
            accounts = konten,
            submissions = listOf(
                einreichung(
                    id = 10,
                    actionId = "a1",
                    accountId = 1,
                    status = SubmissionStatus.ERSTATTET,
                ),
            ),
        )
        assertIs<AccountCheck.BereitsBelegt>(ergebnis)
    }

    @Test
    fun `beim Bearbeiten kollidiert ein Eintrag nicht mit sich selbst`() {
        val bestehend = einreichung(id = 10, actionId = "a1", accountId = 1)

        val ergebnis = AccountDistribution.pruefe(
            actionId = "a1",
            accountId = 1,
            accounts = konten,
            submissions = listOf(bestehend),
            ignoriereSubmissionId = 10,
        )

        assertEquals(AccountCheck.Frei, ergebnis)
    }

    @Test
    fun `bei Konflikt kommt ein freies Konto als Vorschlag mit`() {
        val ergebnis = AccountDistribution.pruefe(
            actionId = "a1",
            accountId = 1,
            accounts = konten,
            submissions = listOf(einreichung(id = 10, actionId = "a1", accountId = 1)),
        )

        val belegt = assertIs<AccountCheck.BereitsBelegt>(ergebnis)
        // 2 und 3 waren nie dran, 2 hat die kleinere Id.
        assertEquals(2L, assertNotNull(belegt.vorschlag).id)
    }

    // --- Kontovorschlag ------------------------------------------------------

    @Test
    fun `ohne Historie kommt das erste aktive Konto`() {
        val vorschlag = AccountDistribution.vorschlag("a1", konten, emptyList())
        assertEquals(1L, vorschlag?.id)
    }

    @Test
    fun `nie genutzte Konten haben Vorrang vor laengst genutzten`() {
        val submissions = listOf(
            einreichung(id = 1, actionId = "alt", accountId = 1, createdAtEpochSecond = 100),
            einreichung(id = 2, actionId = "alt", accountId = 2, createdAtEpochSecond = 200),
        )
        // Konto 3 war noch nie dran und gewinnt, obwohl 1 laenger zurueckliegt.
        assertEquals(3L, AccountDistribution.vorschlag("a1", konten, submissions)?.id)
    }

    @Test
    fun `unter genutzten Konten gewinnt das laengst zurueckliegende`() {
        val submissions = listOf(
            einreichung(id = 1, actionId = "x", accountId = 1, createdAtEpochSecond = 300),
            einreichung(id = 2, actionId = "x", accountId = 2, createdAtEpochSecond = 100),
            einreichung(id = 3, actionId = "x", accountId = 3, createdAtEpochSecond = 200),
        )
        assertEquals(2L, AccountDistribution.vorschlag("a1", konten, submissions)?.id)
    }

    @Test
    fun `belegte Konten scheiden fuer die Aktion aus`() {
        val submissions = listOf(
            einreichung(id = 1, actionId = "a1", accountId = 2, createdAtEpochSecond = 100),
        )
        // Konto 2 ist fuer a1 belegt; 1 und 3 waren nie dran, 1 hat die kleinere Id.
        assertEquals(1L, AccountDistribution.vorschlag("a1", konten, submissions)?.id)
    }

    @Test
    fun `inaktive Konten werden nicht vorgeschlagen`() {
        val konten = listOf(konto(1, aktiv = false), konto(2))
        assertEquals(2L, AccountDistribution.vorschlag("a1", konten, emptyList())?.id)
    }

    @Test
    fun `sind alle Konten belegt gibt es keinen Vorschlag`() {
        val submissions = konten.map { k ->
            einreichung(id = k.id, actionId = "a1", accountId = k.id)
        }
        assertNull(AccountDistribution.vorschlag("a1", konten, submissions))
    }

    @Test
    fun `Round-Robin verteilt drei Einreichungen auf drei Konten`() {
        val vergeben = mutableListOf<Submission>()
        val benutzt = mutableListOf<Long>()

        repeat(3) { runde ->
            val konto = AccountDistribution.vorschlag("a1", konten, vergeben)
            checkNotNull(konto) { "Runde $runde ohne Vorschlag" }
            benutzt += konto.id
            vergeben += einreichung(
                id = runde.toLong() + 1,
                actionId = "a1",
                accountId = konto.id,
                createdAtEpochSecond = runde.toLong() + 1,
            )
        }

        assertEquals(setOf(1L, 2L, 3L), benutzt.toSet(), "jedes Konto genau einmal")
    }

    @Test
    fun `der Vorschlag ist reproduzierbar`() {
        // Konto 1 und 2 liegen gleich weit zurueck — die kleinere Id entscheidet,
        // unabhaengig von der Reihenfolge der Kontenliste.
        val submissions = listOf(
            einreichung(id = 1, actionId = "x", accountId = 1, createdAtEpochSecond = 100),
            einreichung(id = 2, actionId = "x", accountId = 2, createdAtEpochSecond = 100),
            einreichung(id = 3, actionId = "x", accountId = 3, createdAtEpochSecond = 300),
        )
        val ersterLauf = AccountDistribution.vorschlag("a1", konten, submissions)?.id
        val zweiterLauf = AccountDistribution.vorschlag("a1", konten.reversed(), submissions)?.id
        assertEquals(1L, ersterLauf)
        assertEquals(ersterLauf, zweiterLauf)
    }
}
