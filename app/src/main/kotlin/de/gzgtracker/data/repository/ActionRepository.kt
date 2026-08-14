package de.gzgtracker.data.repository

import de.gzgtracker.core.PromoAction
import de.gzgtracker.data.local.PromoActionDao
import de.gzgtracker.data.local.PromoActionEntity
import de.gzgtracker.data.local.toDomain
import de.gzgtracker.data.local.WatchlistDao
import de.gzgtracker.data.local.WatchlistEntity
import de.gzgtracker.data.local.toEntity
import de.gzgtracker.data.remote.ActionsApi
import de.gzgtracker.data.remote.toEntity
import de.gzgtracker.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis einer Feed-Aktualisierung, fuer die Rueckmeldung an den Nutzer. */
sealed interface FeedErgebnis {
    data class Erfolg(val aktionen: Int, val quellen: Int) : FeedErgebnis
    data class Fehler(val meldung: String) : FeedErgebnis
}

@Singleton
class ActionRepository @Inject constructor(
    private val dao: PromoActionDao,
    private val watchlist: WatchlistDao,
    private val api: ActionsApi,
    private val settings: SettingsRepository,
) {

    val alle: Flow<List<PromoAction>> = dao.beobachteAlle().map { liste ->
        liste.map(PromoActionEntity::toDomain)
    }

    fun beobachte(id: String): Flow<PromoAction?> =
        dao.beobachte(id).map { it?.toDomain() }

    /** Die Merkliste: Aktions-Id -> ist es schon im Wagen? */
    val gemerkt: Flow<Map<String, Boolean>> = watchlist.beobachteAlle().map { liste ->
        liste.associate { it.actionId to it.imWagen }
    }

    suspend fun merkenUmschalten(actionId: String) =
        watchlist.schalteUm(actionId, Instant.now())

    suspend fun setzeImWagen(actionId: String, imWagen: Boolean) =
        watchlist.setzeImWagen(actionId, imWagen)

    /**
     * Nimmt eine Aktion von der Merkliste.
     *
     * Wird beim Erfassen aufgerufen: Was gekauft und eingetragen ist, gehoert
     * nicht mehr auf die Einkaufsliste. Sonst muesste man dieselbe Zeile zweimal
     * abhaken — einmal im Laden, einmal in der App.
     */
    suspend fun vergiss(actionId: String) = watchlist.entferne(actionId)

    /** Raeumt die abgehakten Zeilen weg — der Knopf nach dem Einkauf. */
    suspend fun entferneErledigte() = watchlist.entferneErledigte()

    suspend fun lade(id: String): PromoAction? = dao.lade(id)?.toDomain()

    suspend fun ladeAlle(): List<PromoAction> = dao.ladeAlle().map(PromoActionEntity::toDomain)

    suspend fun findeNachEan(ean: String): List<PromoAction> =
        dao.findeNachEan(ean).map(PromoActionEntity::toDomain)

    /** Von Hand angelegte Aktion. Bekommt eine eigene Id, die nie mit dem Feed kollidiert. */
    suspend fun legeManuellAn(action: PromoAction): String {
        val id = action.id.takeIf { it.isNotBlank() }
            ?: "manuell-${Instant.now().toEpochMilli()}"
        dao.upsert(action.copy(id = id, isManual = true, source = "manuell").toEntity())
        return id
    }

    suspend fun aktualisiere(action: PromoAction) {
        dao.upsert(action.toEntity(lastSeenAt = dao.lade(action.id)?.lastSeenAt))
    }

    suspend fun loesche(id: String) = dao.loesche(id)

    /**
     * Holt `actions.json` und schreibt sie in die lokale Datenbank.
     *
     * Aufgeraeumt wird pro Quelle: Faellt ein Portal aus, verschwinden nur dessen
     * Aktionen aus dem Feed — die anderen Quellen behalten ihren Stand. Aktionen mit
     * Einreichungen und von Hand angelegte bleiben immer erhalten.
     *
     * Ohne Netz bleibt einfach der letzte Stand stehen; die App ist offline nutzbar.
     */
    suspend fun aktualisiereFeed(): FeedErgebnis {
        val url = settings.settings.first().feedUrl
        val antwort = runCatching { api.ladeFeed(url) }.getOrElse { fehler ->
            return FeedErgebnis.Fehler(fehler.lesbareMeldung())
        }

        val jetzt = Instant.now()
        val eintraege = antwort.actions.map { it.toEntity(jetzt) }

        if (eintraege.isEmpty()) {
            // Ein leerer Feed loescht nichts — sonst wuerde ein kaputter Scraper-Lauf
            // die gesamte Aktionsliste auf dem Geraet ausradieren.
            settings.merkeSync(jetzt)
            return FeedErgebnis.Erfolg(aktionen = 0, quellen = 0)
        }

        eintraege.groupBy { it.source }.forEach { (quelle, aktionen) ->
            dao.ersetzeQuelle(source = quelle, actions = aktionen, gesehenAm = jetzt)
        }

        settings.merkeSync(jetzt)
        return FeedErgebnis.Erfolg(
            aktionen = eintraege.size,
            quellen = eintraege.map { it.source }.distinct().size,
        )
    }
}

private fun Throwable.lesbareMeldung(): String = when (this) {
    is java.net.UnknownHostException -> "Keine Verbindung. Der letzte Stand bleibt erhalten."
    is java.net.SocketTimeoutException -> "Zeitüberschreitung. Versuch es später noch einmal."
    is retrofit2.HttpException -> when (code()) {
        404 -> "Feed nicht gefunden. Prüfe die Feed-URL in den Einstellungen."
        403, 401 -> "Kein Zugriff auf den Feed. Ist das Repository privat?"
        else -> "Server antwortet mit Fehler ${code()}."
    }
    else -> message ?: "Aktualisieren fehlgeschlagen."
}
