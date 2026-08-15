package de.gzgtracker.data.repository

import de.gzgtracker.core.Account
import de.gzgtracker.data.local.AccountDao
import de.gzgtracker.data.local.AccountEntity
import de.gzgtracker.data.local.toDomain
import de.gzgtracker.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val dao: AccountDao,
) {

    val alle: Flow<List<Account>> = dao.beobachteAlle().map { liste ->
        liste.map(AccountEntity::toDomain)
    }

    val aktive: Flow<List<Account>> = dao.beobachteAktive().map { liste ->
        liste.map(AccountEntity::toDomain)
    }

    suspend fun lade(id: Long): Account? = dao.lade(id)?.toDomain()

    suspend fun ladeAlle(): List<Account> = dao.ladeAlle().map(AccountEntity::toDomain)

    /**
     * Legt ein Konto samt Profil an.
     *
     * Nimmt bewusst das ganze [Account] entgegen statt einzelner Felder: Seit
     * das Konto auch Name, Adresse und IBAN traegt, waere eine Parameterliste
     * zehn Eintraege lang — und jede Erweiterung ein Umbau an vier Stellen.
     */
    suspend fun anlegen(entwurf: Account): Long =
        dao.fuegeEin(entwurf.copy(id = 0).toEntity(createdAt = Instant.now()))

    suspend fun aktualisieren(account: Account) {
        val bestehend = dao.lade(account.id) ?: return
        // Das ganze Konto uebernehmen und nur den Anlagezeitpunkt behalten.
        // Vorher standen hier vier Felder einzeln — jedes neue waere still
        // verworfen worden, und niemand haette gemerkt, warum die Adresse nach
        // dem Speichern wieder leer ist.
        dao.aktualisiere(
            account.copy(name = account.name.trim()).toEntity(createdAt = bestehend.createdAt),
        )
    }

    suspend fun setzeAktiv(id: Long, aktiv: Boolean) = dao.setzeAktiv(id, aktiv)

    /** Wie viele Einreichungen haengen an dem Konto? Entscheidet ueber loeschbar. */
    suspend fun anzahlEinreichungen(id: Long): Int = dao.anzahlEinreichungen(id)

    /**
     * Loescht ein Konto endgueltig — nur moeglich, solange keine Einreichung daran
     * haengt. Sonst bleibt nur das Deaktivieren, damit die Historie stimmig bleibt.
     */
    suspend fun loeschen(id: Long): Boolean {
        if (dao.anzahlEinreichungen(id) > 0) return false
        val account = dao.lade(id) ?: return false
        dao.loesche(account)
        return true
    }
}
