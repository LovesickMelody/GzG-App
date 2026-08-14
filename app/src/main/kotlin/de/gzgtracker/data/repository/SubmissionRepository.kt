package de.gzgtracker.data.repository

import de.gzgtracker.core.Account
import de.gzgtracker.core.AccountCheck
import de.gzgtracker.core.AccountDistribution
import de.gzgtracker.core.Submission
import de.gzgtracker.core.SubmissionStatus
import de.gzgtracker.data.local.AccountDao
import de.gzgtracker.data.local.AccountEntity
import de.gzgtracker.data.local.SubmissionDao
import de.gzgtracker.data.local.SubmissionEntity
import de.gzgtracker.data.local.toDomain
import de.gzgtracker.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubmissionRepository @Inject constructor(
    private val dao: SubmissionDao,
    private val accountDao: AccountDao,
) {

    val alle: Flow<List<Submission>> = dao.beobachteAlle().map { liste ->
        liste.map(SubmissionEntity::toDomain)
    }

    fun beobachte(id: Long): Flow<Submission?> = dao.beobachte(id).map { it?.toDomain() }

    suspend fun ladeAlle(): List<Submission> = dao.ladeAlle().map(SubmissionEntity::toDomain)

    /**
     * Prueft das gewaehlte Konto gegen die Kernregel: pro Aktion nur einmal dasselbe
     * Konto. Die Entscheidung, ob gewarnt oder blockiert wird, faellt in der UI —
     * hier wird nur der Sachverhalt festgestellt.
     */
    suspend fun pruefeKonto(
        actionId: String,
        accountId: Long,
        ignoriereSubmissionId: Long? = null,
    ): AccountCheck = AccountDistribution.pruefe(
        actionId = actionId,
        accountId = accountId,
        accounts = accountDao.ladeAlle().map(AccountEntity::toDomain),
        submissions = ladeAlle(),
        ignoriereSubmissionId = ignoriereSubmissionId,
    )

    /** Welches Konto soll die App vorschlagen? */
    suspend fun schlageKontoVor(actionId: String): Account? = AccountDistribution.vorschlag(
        actionId = actionId,
        accounts = accountDao.ladeAlle().map(AccountEntity::toDomain),
        submissions = ladeAlle(),
    )

    suspend fun anlegen(submission: Submission): Long =
        dao.fuegeEin(submission.copy(createdAt = Instant.now()).toEntity().copy(id = 0))

    suspend fun aktualisieren(submission: Submission) = dao.aktualisiere(submission.toEntity())

    suspend fun loeschen(id: Long) = dao.loesche(id)

    /**
     * Setzt den Status und pflegt die zugehoerigen Datumsangaben gleich mit.
     *
     * Beim Wechsel auf `ERSTATTET` wird das Erstattungsdatum gesetzt; ein
     * abweichender Betrag kann mitgegeben werden. Geht es zurueck auf einen offenen
     * Status, werden die Erstattungsdaten wieder geleert, damit die Summen nicht
     * Geld ausweisen, das nie kam.
     */
    suspend fun setzeStatus(
        id: Long,
        status: SubmissionStatus,
        am: LocalDate = LocalDate.now(),
        erstatteterBetragCents: Int? = null,
    ) {
        val aktuell = dao.lade(id) ?: return

        val aktualisiert = when (status) {
            SubmissionStatus.GEKAUFT -> aktuell.copy(
                status = status.name,
                submittedAt = null,
                refundedAt = null,
                refundedAmountCents = null,
            )

            SubmissionStatus.EINGEREICHT -> aktuell.copy(
                status = status.name,
                submittedAt = aktuell.submittedAt ?: am,
                refundedAt = null,
                refundedAmountCents = null,
            )

            SubmissionStatus.ERSTATTET -> aktuell.copy(
                status = status.name,
                submittedAt = aktuell.submittedAt ?: am,
                refundedAt = am,
                refundedAmountCents = erstatteterBetragCents ?: aktuell.refundedAmountCents,
            )

            SubmissionStatus.ABGELEHNT -> aktuell.copy(
                status = status.name,
                submittedAt = aktuell.submittedAt ?: am,
                refundedAt = null,
                refundedAmountCents = null,
            )
        }

        dao.aktualisiere(aktualisiert)
    }
}
