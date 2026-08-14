package de.gzgtracker.core

/** Wie streng die App auf ein doppelt belegtes Konto reagiert. */
enum class DuplicateAccountRule {
    /** Deutlich warnen, Speichern bleibt moeglich. */
    WARNEN,

    /** Speichern verhindern, bis ein anderes Konto gewaehlt ist. */
    BLOCKIEREN,
    ;

    companion object {
        val DEFAULT = WARNEN
    }
}

/** Ergebnis der Kontopruefung fuer eine geplante Einreichung. */
sealed interface AccountCheck {

    /** Konto ist fuer diese Aktion noch frei. */
    data object Frei : AccountCheck

    /**
     * Auf dieses Konto wurde fuer dieselbe Aktion bereits eingereicht.
     * [vorschlag] ist ein noch freies Konto oder `null`, wenn alle belegt sind.
     */
    data class BereitsBelegt(
        val konflikt: Submission,
        val vorschlag: Account?,
    ) : AccountCheck
}

/**
 * Die Kernregel: Pro Aktion darf jedes Konto nur einmal als Erstattungsziel dienen.
 * Anbieter erkennen sonst die Mehrfachteilnahme.
 *
 * Eine abgelehnte Einreichung belegt das Konto **nicht** weiter — es ist ja kein Geld
 * geflossen, ein zweiter Anlauf ueber dasselbe Konto ist legitim.
 */
object AccountDistribution {

    /** Konten, die fuer [actionId] bereits belegt sind. */
    fun belegteKonten(actionId: String, submissions: List<Submission>): Set<Long> =
        submissions
            .filter { it.actionId == actionId && it.status != SubmissionStatus.ABGELEHNT }
            .map { it.accountId }
            .toSet()

    /**
     * Schlaegt das Konto vor, das fuer [actionId] noch frei ist und insgesamt am
     * laengsten nicht an der Reihe war (Round-Robin). Konten ohne jede Einreichung
     * kommen zuerst. Bei Gleichstand entscheidet die niedrigere Id, damit der
     * Vorschlag reproduzierbar bleibt.
     *
     * Gibt `null` zurueck, wenn kein aktives Konto mehr frei ist.
     */
    fun vorschlag(
        actionId: String,
        accounts: List<Account>,
        submissions: List<Submission>,
        ausgeschlossen: Set<Long> = emptySet(),
    ): Account? {
        val belegt = belegteKonten(actionId, submissions)
        val frei = accounts.filter { account ->
            account.isActive && account.id !in belegt && account.id !in ausgeschlossen
        }
        if (frei.isEmpty()) return null

        val zuletztGenutzt: Map<Long, java.time.Instant> = submissions
            .groupBy { it.accountId }
            .mapValues { (_, eintraege) -> eintraege.maxOf { it.createdAt } }

        return frei.minWithOrNull(
            compareBy<Account> { account ->
                // Nie genutzte Konten zuerst.
                if (zuletztGenutzt.containsKey(account.id)) 1 else 0
            }
                .thenBy { account -> zuletztGenutzt[account.id] ?: java.time.Instant.EPOCH }
                .thenBy { account -> account.id },
        )
    }

    /**
     * Prueft, ob [accountId] fuer [actionId] noch frei ist, und liefert bei einem
     * Konflikt gleich einen Alternativvorschlag mit.
     *
     * [ignoriereSubmissionId] blendet die gerade bearbeitete Einreichung aus, damit
     * das Bearbeiten eines bestehenden Eintrags nicht mit sich selbst kollidiert.
     */
    fun pruefe(
        actionId: String,
        accountId: Long,
        accounts: List<Account>,
        submissions: List<Submission>,
        ignoriereSubmissionId: Long? = null,
    ): AccountCheck {
        val relevant = submissions.filter { it.id != ignoriereSubmissionId }
        val konflikt = relevant.firstOrNull { submission ->
            submission.actionId == actionId &&
                submission.accountId == accountId &&
                submission.status != SubmissionStatus.ABGELEHNT
        } ?: return AccountCheck.Frei

        return AccountCheck.BereitsBelegt(
            konflikt = konflikt,
            vorschlag = vorschlag(
                actionId = actionId,
                accounts = accounts,
                submissions = relevant,
                ausgeschlossen = setOf(accountId),
            ),
        )
    }
}
