package de.gzgtracker.ui.aktionen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.gzgtracker.core.PromoAction
import de.gzgtracker.data.repository.ActionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class AktionDetailUiState(
    val laedt: Boolean = true,
    val aktion: PromoAction? = null,
    val gemerkt: Boolean = false,
    val erinnert: Boolean = false,
    val meldung: String? = null,
)

@HiltViewModel
class AktionDetailViewModel @Inject constructor(
    private val actions: ActionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val aktionId: String = savedStateHandle.get<String>("actionId").orEmpty()

    private val meldungen = MutableStateFlow<String?>(null)

    private val datumsformat = DateTimeFormatter.ofPattern("dd.MM. 'um' HH:mm")

    val uiState: StateFlow<AktionDetailUiState> = combine(
        actions.beobachte(aktionId),
        actions.gemerkt,
        actions.erinnert,
        meldungen,
    ) { aktion, gemerkt, erinnert, meldung ->
        AktionDetailUiState(
            laedt = false,
            aktion = aktion,
            gemerkt = aktionId in gemerkt,
            erinnert = aktionId in erinnert,
            meldung = meldung,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AktionDetailUiState(),
    )

    fun merkenUmschalten() {
        viewModelScope.launch { actions.merkenUmschalten(aktionId) }
    }

    /**
     * Stellt die Erinnerung oder nimmt sie zurueck.
     *
     * Der gestellte Zeitpunkt wird gemeldet, statt ihn stillschweigend zu setzen:
     * "Erinnerung steht" allein liesse offen, ob sie morgen oder in drei Wochen
     * kommt — und genau das will man wissen.
     */
    fun erinnerungUmschalten() {
        viewModelScope.launch {
            val vorher = uiState.value.erinnert
            val zeitpunkt = actions.erinnerungUmschalten(aktionId)
            meldungen.update {
                when {
                    zeitpunkt != null -> "Erinnerung am ${zeitpunkt.format(datumsformat)} Uhr"
                    vorher -> "Erinnerung entfernt"
                    else -> "Zu dieser Aktion ist keine Frist bekannt."
                }
            }
        }
    }

    fun zeigeMeldung(text: String) = meldungen.update { text }

    fun meldungGelesen() = meldungen.update { null }
}
