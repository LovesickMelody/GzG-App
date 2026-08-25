package de.gzgtracker.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.gzgtracker.core.Erinnerung
import de.gzgtracker.core.Kontingenterinnerung
import de.gzgtracker.core.PromoAction
import de.gzgtracker.data.local.ErinnerungDao
import de.gzgtracker.data.local.ErinnerungEntity
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
import de.gzgtracker.notify.Erinnerungen
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
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
    private val erinnerungen: ErinnerungDao,
    private val api: ActionsApi,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) {

    val alle: Flow<List<PromoAction>> = dao.beobachteAlle().map { liste ->
        liste.map(PromoActionEntity::toDomain)
    }

    fun beobachte(id: String): Flow<PromoAction?> =
        dao.beobachte(id).map { it?.toDomain() }

    // --- Erinnerungen -------------------------------------------------------

    /** Die Aktionen, zu denen eine Erinnerung gestellt ist. */
    val erinnert: Flow<Set<String>> =
        erinnerungen.beobachteAlle().map { liste -> liste.map { it.actionId }.toSet() }

    /**
     * Stellt eine Erinnerung. Eine vorhandene wird ersetzt.
     *
     * @return der gestellte Zeitpunkt, oder `null`, wenn sich keiner ergibt —
     *   etwa weil die Aktion keine Frist nennt oder die gewuenschte Art nicht
     *   in Frage kommt.
     */
    suspend fun erinnerungStellen(
        actionId: String,
        art: Erinnerungsart,
        eigenerZeitpunkt: LocalDateTime? = null,
    ): LocalDateTime? {
        val aktion = dao.lade(actionId)?.toDomain() ?: return null

        val (zeitpunkt, abstandTage) = when (art) {
            Erinnerungsart.FRIST -> {
                val frist = aktion.submissionDeadline ?: aktion.validTo ?: return null
                (Erinnerung.zeitpunkt(frist, LocalDateTime.now()) ?: return null) to 0L
            }

            Erinnerungsart.FREISCHALTUNG -> {
                val zuruecksetzung = Kontingenterinnerung.lies(aktion.limitReset) ?: return null
                Kontingenterinnerung.naechsterWecker(zuruecksetzung, LocalDateTime.now()) to
                    zuruecksetzung.abstandTage
            }

            Erinnerungsart.EIGEN -> {
                val gewuenscht = eigenerZeitpunkt ?: return null
                if (!gewuenscht.isAfter(LocalDateTime.now())) return null
                gewuenscht to 0L
            }
        }

        val anlass = if (art == Erinnerungsart.FREISCHALTUNG) {
            Erinnerungen.ANLASS_FREISCHALTUNG
        } else {
            Erinnerungen.ANLASS_FRIST
        }
        val abstandMillis = abstandTage * 24 * 60 * 60 * 1000L
        val faellig = zeitpunkt.atZone(ZoneId.systemDefault()).toInstant()

        erinnerungen.upsert(
            ErinnerungEntity(
                actionId = actionId,
                faelligAm = faellig,
                titel = aktion.title,
                abstandMillis = abstandMillis,
                anlass = anlass,
            ),
        )
        Erinnerungen.stelle(context, actionId, aktion.title, faellig, abstandMillis, anlass)
        return zeitpunkt
    }

    /** Nimmt eine Erinnerung zurueck. */
    suspend fun erinnerungEntfernen(actionId: String) {
        erinnerungen.entferne(actionId)
        Erinnerungen.nimmZurueck(context, actionId)
    }

    /**
     * Stellt alle gespeicherten Erinnerungen neu.
     *
     * Noetig nach jedem Neustart des Telefons: Wecker des Systems ueberleben ihn
     * nicht. Weil die App nicht mitbekommt, wann neu gestartet wurde, passiert das
     * einfach bei jedem Start — doppelt Stellen schadet nicht, der Wecker wird
     * dabei ersetzt.
     */
    suspend fun stelleErinnerungenNeu() {
        val jetzt = Instant.now()
        // Nur einmalige Erinnerungen verfallen. Eine wiederkehrende bleibt, auch
        // wenn ihr naechster Termin in der Vergangenheit steht — sie wird gleich
        // auf den kommenden gesetzt.
        erinnerungen.entferneAbgelaufeneEinmalige(jetzt)
        erinnerungen.ladeAlle().forEach { eintrag ->
            val faellig = if (eintrag.abstandMillis > 0) {
                naechsterTermin(eintrag.faelligAm, eintrag.abstandMillis, jetzt)
            } else {
                eintrag.faelligAm
            }
            Erinnerungen.stelle(
                context,
                eintrag.actionId,
                eintrag.titel,
                faellig,
                eintrag.abstandMillis,
                eintrag.anlass,
            )
        }
    }

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

        val imFeed = eintraege.groupBy { it.source }
        imFeed.forEach { (quelle, aktionen) ->
            dao.ersetzeQuelle(source = quelle, actions = aktionen, gesehenAm = jetzt)
        }

        // Quellen, die der Feed gar nicht mehr nennt, wurden bisher nie
        // angefasst — ihre Aktionen blieben fuer immer stehen. Genau daran lag
        // es, dass die Liste wochenalte Angebote zeigte, obwohl der Feed sie
        // laengst nicht mehr enthielt.
        //
        // Das ist gefahrlos, weil der Sammellauf den letzten Stand einer
        // *ausgefallenen* Quelle selbst weitertraegt: Was hier fehlt, fehlt
        // absichtlich. Einreichungen und der Einkaufszettel bleiben ohnehin
        // verschont.
        dao.bekannteQuellen()
            .filterNot { it in imFeed }
            .forEach { verschwunden -> dao.entferneQuelle(verschwunden) }

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


/** Woran eine Erinnerung haengt. */
enum class Erinnerungsart {
    /** Vor Ablauf der Einsendefrist. */
    FRIST,

    /** Kurz bevor ein Kontingent neu freigeschaltet wird — wiederkehrend. */
    FREISCHALTUNG,

    /** Ein selbst gewaehlter Zeitpunkt. */
    EIGEN,
}

/**
 * Schiebt einen vergangenen Wiederholungstermin auf den naechsten kuenftigen.
 *
 * Noetig nach einem Neustart des Telefons: Der gespeicherte Termin kann dann
 * Wochen zurueckliegen, und ein Wecker in der Vergangenheit feuert sofort.
 */
private fun naechsterTermin(start: Instant, abstandMillis: Long, jetzt: Instant): Instant {
    if (abstandMillis <= 0 || start.isAfter(jetzt)) return start
    val vergangen = jetzt.toEpochMilli() - start.toEpochMilli()
    val schritte = vergangen / abstandMillis + 1
    return start.plusMillis(schritte * abstandMillis)
}
