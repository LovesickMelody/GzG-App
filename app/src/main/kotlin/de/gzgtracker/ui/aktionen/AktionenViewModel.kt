package de.gzgtracker.ui.aktionen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.gzgtracker.core.PromoAction
import de.gzgtracker.data.repository.ActionRepository
import de.gzgtracker.data.repository.FeedErgebnis
import de.gzgtracker.data.repository.SubmissionRepository
import de.gzgtracker.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Wonach die Aktionsliste sortiert wird. */
enum class Sortierung(val label: String) {
    /** Frist zuerst, ohne Frist ans Ende — so kommt die Datenbank sie ohnehin. */
    FRIST("Frist zuerst"),
    BETRAG("Höchste Erstattung"),
    NAME("Name A–Z"),
}

data class AktionenUiState(
    val laedt: Boolean = true,
    val aktionen: List<PromoAction> = emptyList(),
    val suche: String = "",
    val nurLaufende: Boolean = true,
    /** Zeigt nur die gemerkten Aktionen — der Einkaufszettel. */
    val nurMerkliste: Boolean = false,
    val sortierung: Sortierung = Sortierung.FRIST,
    /** Aktions-Id -> schon im Wagen. Enthaelt genau die gemerkten Aktionen. */
    val gemerkt: Map<String, Boolean> = emptyMap(),
    /** Aktionen, zu denen eine Erinnerung gestellt ist. */
    val erinnert: Set<String> = emptySet(),
    val aktualisiertGerade: Boolean = false,
    val letzterSync: Instant? = null,
    val meldung: String? = null,
    /** Wie viele Aktionen insgesamt da sind, bevor gefiltert wird. */
    val gesamt: Int = 0,
) {
    val istLeer: Boolean get() = !laedt && aktionen.isEmpty()

    /**
     * True, wenn Suche oder Filter etwas ausblenden.
     *
     * Genau daran ist schon zweimal der Eindruck entstanden, der Feed lade
     * nicht: Ein Suchbegriff ueberlebt das Einreichen, und danach steht in der
     * Liste nur noch ein Eintrag. Wo etwas fehlt, muss dastehen, warum.
     */
    val eingeschraenkt: Boolean
        get() = !laedt && !nurMerkliste && (suche.isNotBlank() || aktionen.size < gesamt)

    val anzahlGemerkt: Int get() = gemerkt.size

    /** Wie viele Zeilen des Einkaufszettels noch offen sind. */
    val nochZuKaufen: Int get() = gemerkt.count { !it.value }

    val hatErledigte: Boolean get() = gemerkt.any { it.value }
}

@HiltViewModel
class AktionenViewModel @Inject constructor(
    private val actions: ActionRepository,
    private val settings: SettingsRepository,
    private val submissions: SubmissionRepository,
) : ViewModel() {

    private val eingaben = MutableStateFlow(Eingaben())

    val uiState: StateFlow<AktionenUiState> = combine(
        actions.alle,
        actions.gemerkt,
        actions.erinnert,
        eingaben,
        settings.settings,
    ) { alle, gemerkt, erinnert, eingabe, einstellungen ->
        val heute = LocalDate.now()
        val begriff = eingabe.suche.trim().lowercase()

        AktionenUiState(
            laedt = false,
            aktionen = alle
                .filter { aktion ->
                    // Auf dem Einkaufszettel zaehlt nur, was daraufsteht — alle
                    // anderen Filter treten dahinter zurueck.
                    !eingabe.nurMerkliste || aktion.id in gemerkt
                }
                .filter { aktion ->
                    !eingabe.nurLaufende || aktion.laeuftNoch(heute)
                }
                .filter { aktion ->
                    begriff.isEmpty() ||
                        aktion.title.lowercase().contains(begriff) ||
                        aktion.brand?.lowercase()?.contains(begriff) == true ||
                        aktion.retailers.any { it.lowercase().contains(begriff) } ||
                        aktion.eans.any { it.contains(begriff) }
                }
                .let { gefiltert -> sortiere(gefiltert, eingabe.sortierung) },
            suche = eingabe.suche,
            nurLaufende = eingabe.nurLaufende,
            nurMerkliste = eingabe.nurMerkliste,
            sortierung = eingabe.sortierung,
            gemerkt = gemerkt,
            erinnert = erinnert,
            aktualisiertGerade = eingabe.aktualisiert,
            letzterSync = einstellungen.lastSyncAt,
            meldung = eingabe.meldung,
            gesamt = alle.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AktionenUiState(),
    )

    init {
        // Wecker des Systems ueberleben keinen Neustart des Telefons. Weil die App
        // nicht mitbekommt, wann neu gestartet wurde, stellt sie sie bei jedem
        // Start neu — doppelt stellen schadet nicht.
        viewModelScope.launch { actions.stelleErinnerungenNeu() }

        vergissSucheNachEinreichung()
    }

    /**
     * Loescht den Suchbegriff, sobald eine Einreichung dazugekommen ist.
     *
     * Das ViewModel haengt am Navigationseintrag der Liste und ueberlebt deshalb
     * den ganzen Weg ueber Aktion, Formular und Erfassen. Wer vorher nach
     * "senso" gesucht hatte, kam zurueck und sah eine Liste mit einem Eintrag —
     * und hielt den Feed fuer kaputt. Nach dem Einreichen ist die Suche erledigt.
     *
     * Nur der Suchbegriff, nicht die Merkliste: Wer den Einkaufszettel abarbeitet,
     * will nach dem Einreichen wieder den Zettel sehen und nicht alles.
     */
    private fun vergissSucheNachEinreichung() {
        viewModelScope.launch {
            var bekannt: Int? = null
            submissions.alle.collect { liste ->
                val vorher = bekannt
                bekannt = liste.size
                if (vorher != null && liste.size > vorher) {
                    eingaben.update { it.copy(suche = "") }
                }
            }
        }
    }

    fun setzeSuche(begriff: String) = eingaben.update { it.copy(suche = begriff) }

    /** Nimmt Suche und Filter zurueck auf den Ausgangszustand. */
    fun setzeFilterZurueck() = eingaben.update {
        it.copy(suche = "", nurLaufende = true, nurMerkliste = false)
    }

    fun setzeNurLaufende(nur: Boolean) = eingaben.update { it.copy(nurLaufende = nur) }

    fun setzeNurMerkliste(nur: Boolean) = eingaben.update { it.copy(nurMerkliste = nur) }

    fun setzeSortierung(wahl: Sortierung) = eingaben.update { it.copy(sortierung = wahl) }

    fun merkenUmschalten(aktionId: String) {
        viewModelScope.launch { actions.merkenUmschalten(aktionId) }
    }

    fun setzeImWagen(aktionId: String, imWagen: Boolean) {
        viewModelScope.launch { actions.setzeImWagen(aktionId, imWagen) }
    }

    fun entferneErledigte() {
        viewModelScope.launch {
            actions.entferneErledigte()
            eingaben.update { it.copy(meldung = "Abgehakte Zeilen entfernt") }
        }
    }

    fun aktualisiere() {
        if (eingaben.value.aktualisiert) return
        viewModelScope.launch {
            eingaben.update { it.copy(aktualisiert = true) }
            val meldung = when (val ergebnis = actions.aktualisiereFeed()) {
                is FeedErgebnis.Erfolg ->
                    if (ergebnis.aktionen == 0) {
                        "Der Feed enthält keine Aktionen."
                    } else {
                        "${ergebnis.aktionen} Aktionen aus ${ergebnis.quellen} Quellen geladen."
                    }

                is FeedErgebnis.Fehler -> ergebnis.meldung
            }
            eingaben.update { it.copy(aktualisiert = false, meldung = meldung) }
        }
    }

    fun loesche(id: String) {
        viewModelScope.launch {
            actions.loesche(id)
            eingaben.update { it.copy(meldung = "Aktion gelöscht.") }
        }
    }

    fun meldungGelesen() = eingaben.update { it.copy(meldung = null) }

    private data class Eingaben(
        val suche: String = "",
        val nurLaufende: Boolean = true,
        val nurMerkliste: Boolean = false,
        val sortierung: Sortierung = Sortierung.FRIST,
        val aktualisiert: Boolean = false,
        val meldung: String? = null,
    )
}

/**
 * Bringt die Liste in die gewuenschte Reihenfolge.
 *
 * Aktionen ohne Angabe landen jeweils hinten: Eine fehlende Frist ist kein
 * Grund, ganz oben zu stehen, und ein unbekannter Hoechstbetrag auch nicht.
 */
private fun sortiere(aktionen: List<PromoAction>, wahl: Sortierung): List<PromoAction> =
    when (wahl) {
        // Kommt so schon aus der Datenbank — nichts zu tun.
        Sortierung.FRIST -> aktionen
        Sortierung.BETRAG -> aktionen.sortedByDescending { it.maxRefundCents ?: -1 }
        Sortierung.NAME -> aktionen.sortedBy { it.title.lowercase() }
    }

/**
 * Laeuft die Aktion heute noch? Massgeblich ist die Einreichefrist, sonst das
 * Gueltigkeitsende. Fehlt beides, gilt sie als laufend — lieber eine Aktion zu viel
 * anzeigen als eine verpasste Frist.
 */
fun PromoAction.laeuftNoch(heute: LocalDate = LocalDate.now()): Boolean {
    val frist = submissionDeadline ?: validTo ?: return true
    return !heute.isAfter(frist)
}

/** Wie viele Tage bleiben noch? Negativ heisst abgelaufen. */
fun PromoAction.tageBisFrist(heute: LocalDate = LocalDate.now()): Long? {
    val frist = submissionDeadline ?: validTo ?: return null
    return java.time.temporal.ChronoUnit.DAYS.between(heute, frist)
}
