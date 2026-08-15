package de.gzgtracker.ui.aktionen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.gzgtracker.core.PromoAction
import de.gzgtracker.data.repository.ActionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AktionDetailUiState(
    val laedt: Boolean = true,
    val aktion: PromoAction? = null,
    val gemerkt: Boolean = false,
)

@HiltViewModel
class AktionDetailViewModel @Inject constructor(
    private val actions: ActionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val aktionId: String = savedStateHandle.get<String>("actionId").orEmpty()

    val uiState: StateFlow<AktionDetailUiState> = combine(
        actions.beobachte(aktionId),
        actions.gemerkt,
    ) { aktion, gemerkt ->
        AktionDetailUiState(
            laedt = false,
            aktion = aktion,
            gemerkt = aktionId in gemerkt,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AktionDetailUiState(),
    )

    fun merkenUmschalten() {
        viewModelScope.launch { actions.merkenUmschalten(aktionId) }
    }
}
