package de.gzgtracker.ui.uebersicht

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.gzgtracker.core.Account
import de.gzgtracker.core.PromoAction
import de.gzgtracker.core.Submission
import de.gzgtracker.core.SubmissionFilter
import de.gzgtracker.core.SubmissionFiltering
import de.gzgtracker.core.SubmissionStatus
import de.gzgtracker.core.Totals
import de.gzgtracker.core.TotalsCalculator
import de.gzgtracker.data.export.CsvExporter
import de.gzgtracker.data.repository.ActionRepository
import de.gzgtracker.data.repository.AccountRepository
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

/** Eine Zeile der Uebersicht mit allem, was zum Anzeigen noetig ist. */
data class SubmissionZeile(
    val submission: Submission,
    val action: PromoAction?,
    val account: Account?,
    val betragCents: Int,
)

data class UebersichtUiState(
    val laedt: Boolean = true,
    val zeilen: List<SubmissionZeile> = emptyList(),
    val totals: Totals = Totals(),
    val konten: List<Account> = emptyList(),
    val aktionen: List<PromoAction> = emptyList(),
    val filter: SubmissionFilter = SubmissionFilter(),
    val aktualisiertGerade: Boolean = false,
    val letzterSync: Instant? = null,
    val meldung: String? = null,
    /** Id der Einreichung, die gerade auf ERSTATTET gesetzt wurde — loest den Stempel aus. */
    val geradeErstattet: Long? = null,
) {
    val istLeer: Boolean get() = !laedt && zeilen.isEmpty()
}

@HiltViewModel
class UebersichtViewModel @Inject constructor(
    private val submissions: SubmissionRepository,
    private val actions: ActionRepository,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
    private val csvExporter: CsvExporter,
) : ViewModel() {

    private val filter = MutableStateFlow(SubmissionFilter())
    private val transient = MutableStateFlow(TransienterZustand())

    val uiState: StateFlow<UebersichtUiState> = combine(
        submissions.alle,
        actions.alle,
        accounts.alle,
        filter,
        transient,
    ) { alleEinreichungen, alleAktionen, alleKonten, aktuellerFilter, fluechtig ->
        val aktionenById = alleAktionen.associateBy { it.id }
        val kontenById = alleKonten.associateBy { it.id }
        val gefiltert = SubmissionFiltering.anwenden(
            alleEinreichungen,
            aktionenById,
            aktuellerFilter,
        )

        UebersichtUiState(
            laedt = false,
            zeilen = gefiltert.map { einreichung ->
                val aktion = aktionenById[einreichung.actionId]
                SubmissionZeile(
                    submission = einreichung,
                    action = aktion,
                    account = kontenById[einreichung.accountId],
                    betragCents = TotalsCalculator.effektiveErstattungCents(einreichung, aktion),
                )
            },
            // Die Summen beziehen sich auf die gefilterte Auswahl — sonst passt die
            // Kopfzeile nicht zu dem, was darunter steht.
            totals = TotalsCalculator.berechne(gefiltert, aktionenById),
            konten = alleKonten,
            aktionen = alleAktionen,
            filter = aktuellerFilter,
            aktualisiertGerade = fluechtig.aktualisiert,
            letzterSync = fluechtig.letzterSync,
            meldung = fluechtig.meldung,
            geradeErstattet = fluechtig.geradeErstattet,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UebersichtUiState(),
    )

    init {
        viewModelScope.launch {
            settings.settings.collect { aktuell ->
                transient.update { it.copy(letzterSync = aktuell.lastSyncAt) }
            }
        }
    }

    fun setzeFilter(neu: SubmissionFilter) {
        filter.value = neu
    }

    fun setzeSuche(begriff: String) {
        filter.update { it.copy(suche = begriff) }
    }

    fun filterZuruecksetzen() {
        filter.value = SubmissionFilter(suche = filter.value.suche)
    }

    fun aktualisiere() {
        if (transient.value.aktualisiert) return
        viewModelScope.launch {
            transient.update { it.copy(aktualisiert = true) }
            val meldung = when (val ergebnis = actions.aktualisiereFeed()) {
                is FeedErgebnis.Erfolg ->
                    if (ergebnis.aktionen == 0) {
                        "Keine Aktionen im Feed."
                    } else {
                        "${ergebnis.aktionen} Aktionen aus ${ergebnis.quellen} Quellen geladen."
                    }

                is FeedErgebnis.Fehler -> ergebnis.meldung
            }
            transient.update { it.copy(aktualisiert = false, meldung = meldung) }
        }
    }

    /**
     * Setzt den Status. Bei `ERSTATTET` merkt sich der Zustand die Id, damit die
     * Liste den Stempel genau einmal animiert und nicht bei jedem Neuzeichnen.
     */
    fun setzeStatus(
        id: Long,
        status: SubmissionStatus,
        am: LocalDate = LocalDate.now(),
        erstatteterBetragCents: Int? = null,
    ) {
        viewModelScope.launch {
            submissions.setzeStatus(id, status, am, erstatteterBetragCents)
            transient.update {
                val stempeln = status == SubmissionStatus.ERSTATTET
                it.copy(geradeErstattet = if (stempeln) id else null)
            }
        }
    }

    fun loesche(id: Long) {
        viewModelScope.launch {
            submissions.loeschen(id)
            transient.update { it.copy(meldung = "Eintrag gelöscht.") }
        }
    }

    /** Schreibt die aktuell gefilterte Liste als CSV und gibt die Datei zum Teilen zurueck. */
    fun exportiere(onFertig: (android.net.Uri?) -> Unit) {
        viewModelScope.launch {
            val zustand = uiState.value
            val uri = csvExporter.schreibe(
                submissions = zustand.zeilen.map { it.submission },
                actionsById = zustand.aktionen.associateBy { it.id },
                accountsById = zustand.konten.associateBy { it.id },
            )
            if (uri == null) {
                transient.update { it.copy(meldung = "Export fehlgeschlagen.") }
            }
            onFertig(uri)
        }
    }

    fun meldungGelesen() {
        transient.update { it.copy(meldung = null) }
    }

    fun stempelGezeigt() {
        transient.update { it.copy(geradeErstattet = null) }
    }

    private data class TransienterZustand(
        val aktualisiert: Boolean = false,
        val letzterSync: Instant? = null,
        val meldung: String? = null,
        val geradeErstattet: Long? = null,
    )
}
