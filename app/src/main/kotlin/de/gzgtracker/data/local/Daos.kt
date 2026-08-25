package de.gzgtracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY isActive DESC, name COLLATE NOCASE ASC")
    fun beobachteAlle(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY name COLLATE NOCASE ASC")
    fun beobachteAktive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun lade(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts")
    suspend fun ladeAlle(): List<AccountEntity>

    @Insert
    suspend fun fuegeEin(account: AccountEntity): Long

    @Update
    suspend fun aktualisiere(account: AccountEntity)

    @Query("UPDATE accounts SET isActive = :aktiv WHERE id = :id")
    suspend fun setzeAktiv(id: Long, aktiv: Boolean)

    @Query("SELECT COUNT(*) FROM submissions WHERE accountId = :id")
    suspend fun anzahlEinreichungen(id: Long): Int

    /** Nur erlaubt, wenn keine Einreichung mehr daran haengt. */
    @Delete
    suspend fun loesche(account: AccountEntity)
}

@Dao
interface PromoActionDao {

    @Query("SELECT * FROM promo_actions ORDER BY submissionDeadline IS NULL, submissionDeadline ASC, title COLLATE NOCASE ASC")
    fun beobachteAlle(): Flow<List<PromoActionEntity>>

    @Query("SELECT * FROM promo_actions WHERE id = :id")
    fun beobachte(id: String): Flow<PromoActionEntity?>

    @Query("SELECT * FROM promo_actions WHERE id = :id")
    suspend fun lade(id: String): PromoActionEntity?

    @Query("SELECT * FROM promo_actions")
    suspend fun ladeAlle(): List<PromoActionEntity>

    /**
     * Sucht Aktionen zu einem gescannten Barcode. Die EANs liegen als getrennte
     * Liste in einem Textfeld, deshalb wird auf das eingerahmte Vorkommen geprueft —
     * sonst wuerde "1234" auch in "51234" treffen.
     */
    @Query(
        """
        SELECT * FROM promo_actions
        WHERE (CHAR(31) || eans || CHAR(31)) LIKE ('%' || CHAR(31) || :ean || CHAR(31) || '%')
        ORDER BY submissionDeadline IS NULL, submissionDeadline ASC
        """,
    )
    suspend fun findeNachEan(ean: String): List<PromoActionEntity>

    @Upsert
    suspend fun upsert(actions: List<PromoActionEntity>)

    @Upsert
    suspend fun upsert(action: PromoActionEntity)

    @Query("DELETE FROM promo_actions WHERE id = :id")
    suspend fun loesche(id: String)

    /**
     * Raeumt Aktionen weg, die aus dem Feed verschwunden sind. Von Hand angelegte
     * Aktionen und solche mit Einreichungen bleiben immer stehen.
     */
    @Query(
        """
        DELETE FROM promo_actions
        WHERE isManual = 0
          AND source = :source
          AND id NOT IN (:aktuelleIds)
          AND id NOT IN (SELECT DISTINCT actionId FROM submissions)
          -- Gemerkte Aktionen bleiben stehen, auch wenn das Portal sie
          -- kurzzeitig nicht mehr ausliefert. Sonst waere die Einkaufsliste
          -- morgens im Supermarkt ploetzlich halb leer.
          AND id NOT IN (SELECT actionId FROM watchlist)
        """,
    )
    suspend fun raeumeAufFuerQuelle(source: String, aktuelleIds: List<String>)

    @Query("SELECT DISTINCT source FROM promo_actions WHERE isManual = 0")
    suspend fun bekannteQuellen(): List<String>

    /**
     * Raeumt eine Quelle vollstaendig weg, die im Feed gar nicht mehr vorkommt.
     *
     * Eigene Abfrage statt [raeumeAufFuerQuelle] mit leerer Liste: `IN ()` ist
     * in SQLite keine leere Menge, sondern ein Syntaxfehler.
     *
     * Dieselben Ausnahmen wie oben — was eine Einreichung hat oder auf dem
     * Einkaufszettel steht, bleibt.
     */
    @Query(
        """
        DELETE FROM promo_actions
        WHERE isManual = 0
          AND source = :source
          AND id NOT IN (SELECT DISTINCT actionId FROM submissions)
          AND id NOT IN (SELECT actionId FROM watchlist)
        """,
    )
    suspend fun entferneQuelle(source: String)

    @Transaction
    suspend fun ersetzeQuelle(
        source: String,
        actions: List<PromoActionEntity>,
        gesehenAm: Instant,
    ) {
        upsert(actions.map { it.copy(lastSeenAt = gesehenAm) })
        raeumeAufFuerQuelle(source, actions.map { it.id })
    }
}

@Dao
interface SubmissionDao {

    @Query("SELECT * FROM submissions ORDER BY createdAt DESC, id DESC")
    fun beobachteAlle(): Flow<List<SubmissionEntity>>

    @Query("SELECT * FROM submissions WHERE id = :id")
    fun beobachte(id: Long): Flow<SubmissionEntity?>

    @Query("SELECT * FROM submissions WHERE id = :id")
    suspend fun lade(id: Long): SubmissionEntity?

    @Query("SELECT * FROM submissions")
    suspend fun ladeAlle(): List<SubmissionEntity>

    @Query("SELECT * FROM submissions WHERE actionId = :actionId")
    suspend fun ladeFuerAktion(actionId: String): List<SubmissionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun fuegeEin(submission: SubmissionEntity): Long

    @Update
    suspend fun aktualisiere(submission: SubmissionEntity)

    @Query("DELETE FROM submissions WHERE id = :id")
    suspend fun loesche(id: Long)
}

@Dao
interface WatchlistDao {

    @Query("SELECT * FROM watchlist ORDER BY addedAt ASC")
    fun beobachteAlle(): Flow<List<WatchlistEntity>>

    @Upsert
    suspend fun upsert(eintrag: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE actionId = :actionId")
    suspend fun entferne(actionId: String)

    @Query("UPDATE watchlist SET imWagen = :imWagen WHERE actionId = :actionId")
    suspend fun setzeImWagen(actionId: String, imWagen: Boolean)

    @Query("DELETE FROM watchlist WHERE imWagen = 1")
    suspend fun entferneErledigte()

    @Query("SELECT COUNT(*) FROM watchlist WHERE actionId = :actionId")
    suspend fun anzahl(actionId: String): Int

    /** Merken und Vergessen in einem Schritt — ein Fingertipp, ein Aufruf. */
    @Transaction
    suspend fun schalteUm(actionId: String, jetzt: Instant) {
        if (anzahl(actionId) > 0) {
            entferne(actionId)
        } else {
            upsert(WatchlistEntity(actionId = actionId, addedAt = jetzt))
        }
    }
}

@Dao
interface ErinnerungDao {

    @Query("SELECT * FROM reminders")
    fun beobachteAlle(): Flow<List<ErinnerungEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun ladeAlle(): List<ErinnerungEntity>

    @Upsert
    suspend fun upsert(eintrag: ErinnerungEntity)

    @Query("DELETE FROM reminders WHERE actionId = :actionId")
    suspend fun entferne(actionId: String)

    /**
     * Raeumt auf, was laengst gefeuert hat — aber nur Einmaliges.
     *
     * Eine wiederkehrende Erinnerung hat ihren Termin zwangslaeufig in der
     * Vergangenheit, sobald sie einmal gefeuert hat. Sie zu loeschen hiesse,
     * dass sie genau einmal nuetzlich war.
     */
    @Query("DELETE FROM reminders WHERE faelligAm < :vor AND abstandMillis = 0")
    suspend fun entferneAbgelaufeneEinmalige(vor: Instant)
}
