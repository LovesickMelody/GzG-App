package de.gzgtracker.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.gzgtracker.core.Account
import de.gzgtracker.core.PromoAction
import de.gzgtracker.core.Submission
import de.gzgtracker.core.SubmissionStatus
import de.gzgtracker.core.TotalsCalculator
import de.gzgtracker.data.repository.AccountRepository
import de.gzgtracker.data.repository.ActionRepository
import de.gzgtracker.data.repository.SubmissionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Ein Schritt im Statusverlauf, wie er in der Detailansicht steht. */
data class Verlaufsschritt(
    val status: SubmissionStatus,
    val datum: LocalDate?,
    val erreicht: Boolean,
)

data class DetailUiState(
    val laedt: Boolean = true,
    val submission: Submission? = null,
    val action: PromoAction? = null,
    val account: Account? = null,
    val erwarteteErstattungCents: Int = 0,
    val verlauf: List<Verlaufsschritt> = emptyList(),
    val geradeErstattet: Boolean = false,
    val meldung: String? = null,
    val geloescht: Boolean = false,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val submissions: SubmissionRepository,
    private val actions: ActionRepository,
    private val accounts: AccountRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val id: Long = savedStateHandle.get<Long>("id") ?: -1L
    private val fluechtig = MutableStateFlow(Fluechtig())

    val uiState: StateFlow<DetailUiState> = combine(
        submissions.beobachte(id),
        actions.alle,
        accounts.alle,
        fluechtig,
    ) { eintrag, alleAktionen, alleKonten, transient ->
        if (eintrag == null) {
            return@combine DetailUiState(laedt = false, geloescht = transient.geloescht)
        }

        val aktion = alleAktionen.firstOrNull { it.id == eintrag.actionId }
        DetailUiState(
            laedt = false,
            submission = eintrag,
            action = aktion,
            account = alleKonten.firstOrNull { it.id == eintrag.accountId },
            erwarteteErstattungCents =
                TotalsCalculator.erwarteteErstattungCents(eintrag, aktion),
            verlauf = baueVerlauf(eintrag),
            geradeErstattet = transient.geradeErstattet,
            meldung = transient.meldung,
            geloescht = transient.geloescht,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState(),
    )

    /**
     * Der Verlauf zeigt den Weg bis zum aktuellen Stand.
     *
     * Bei einer Ablehnung endet die Kette mit "abgelehnt" statt mit "erstattet" —
     * beide Endzustaende gleichzeitig zu zeigen waere irrefuehrend.
     */
    private fun baueVerlauf(eintrag: Submission): List<Verlaufsschritt> {
        val abgelehnt = eintrag.status == SubmissionStatus.ABGELEHNT
        val stufen = buildList {
            add(SubmissionStatus.GEKAUFT to eintrag.purchaseDate)
            add(SubmissionStatus.EINGEREICHT to eintrag.submittedAt)
            if (abgelehnt) {
                add(SubmissionStatus.ABGELEHNT to eintrag.submittedAt)
            } else {
                add(SubmissionStatus.ERSTATTET to eintrag.refundedAt)
            }
        }

        val reihenfolge = listOf(
            SubmissionStatus.GEKAUFT,
            SubmissionStatus.EINGEREICHT,
            if (abgelehnt) SubmissionStatus.ABGELEHNT else SubmissionStatus.ERSTATTET,
        )
        val aktuellerIndex = reihenfolge.indexOf(eintrag.status).takeIf { it >= 0 } ?: 0

        return stufen.mapIndexed { index, (status, datum) ->
            Verlaufsschritt(
                status = status,
                datum = datum,
                erreicht = index <= aktuellerIndex,
            )
        }
    }

    fun setzeStatus(
        status: SubmissionStatus,
        am: LocalDate = LocalDate.now(),
        erstatteterBetragCents: Int? = null,
    ) {
        viewModelScope.launch {
            submissions.setzeStatus(id, status, am, erstatteterBetragCents)
            fluechtig.update {
                it.copy(geradeErstattet = status == SubmissionStatus.ERSTATTET)
            }
        }
    }

    fun loeschen() {
        viewModelScope.launch {
            submissions.loeschen(id)
            fluechtig.update { it.copy(geloescht = true) }
        }
    }

    fun meldungGelesen() = fluechtig.update { it.copy(meldung = null) }

    private data class Fluechtig(
        val geradeErstattet: Boolean = false,
        val meldung: String? = null,
        val geloescht: Boolean = false,
    )
}
