package de.gzgtracker.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.gzgtracker.core.PromoAction
import de.gzgtracker.data.repository.ActionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Was der Scan ergeben hat.
 *
 * - kein Treffer: [treffer] leer, das Formular oeffnet mit der EAN und ohne Aktion
 * - genau einer: direkt weiter
 * - mehrere: der Nutzer waehlt, raten waere hier falsch
 */
data class ScanErgebnis(
    val ean: String,
    val treffer: List<PromoAction>,
) {
    val mehrdeutig: Boolean get() = treffer.size > 1
    val eindeutigeAktion: PromoAction? get() = treffer.singleOrNull()
}

data class ScanUiState(
    val ergebnis: ScanErgebnis? = null,
    val sucht: Boolean = false,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val actions: ActionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun codeErkannt(ean: String) {
        // Der Analyzer meldet nur einmal, trotzdem doppelt absichern: ein zweiter
        // Durchlauf wuerde die Navigation ein zweites Mal ausloesen.
        if (_uiState.value.ergebnis != null || _uiState.value.sucht) return

        viewModelScope.launch {
            _uiState.value = ScanUiState(sucht = true)
            val treffer = actions.findeNachEan(ean)
            _uiState.value = ScanUiState(
                ergebnis = ScanErgebnis(ean = ean, treffer = treffer),
                sucht = false,
            )
        }
    }
}
