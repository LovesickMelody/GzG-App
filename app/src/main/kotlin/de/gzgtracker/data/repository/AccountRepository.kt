package de.gzgtracker.data.repository

import de.gzgtracker.core.Account
import de.gzgtracker.data.local.AccountDao
import de.gzgtracker.data.local.AccountEntity
import de.gzgtracker.data.local.toDomain
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

    suspend fun anlegen(name: String, ibanLast4: String?, colorHex: String): Long =
        dao.fuegeEin(
            AccountEntity(
                name = name.trim(),
                ibanLast4 = ibanLast4?.trim()?.takeIf { it.isNotBlank() },
                colorHex = colorHex,
                isActive = true,
                createdAt = Instant.now(),
            ),
        )

    suspend fun aktualisieren(account: Account) {
        val bestehend = dao.lade(account.id) ?: return
        dao.aktualisiere(
            bestehend.copy(
                name = account.name.trim(),
                ibanLast4 = account.ibanLast4?.trim()?.takeIf { it.isNotBlank() },
                colorHex = account.colorHex,
                isActive = account.isActive,
            ),
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
