package de.gzgtracker.ui.formular

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.gzgtracker.core.Beleg
import de.gzgtracker.core.Einreichdaten
import de.gzgtracker.core.Formularfeld
import de.gzgtracker.core.Formularskript
import de.gzgtracker.core.Money
import de.gzgtracker.data.repository.AccountRepository
import de.gzgtracker.data.repository.ActionRepository
import de.gzgtracker.data.repository.SubmissionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class WebFormularUiState(
    val laedt: Boolean = true,
    val adresse: String? = null,
    val aktionstitel: String = "",
    /** Was in das Formular soll, in fester Reihenfolge. */
    val werte: Map<Formularfeld, String> = emptyMap(),
    /** Die Fotos dieser Einreichung — für das Datei-Feld der Anbieterseite. */
    val belege: List<Beleg> = emptyList(),
    val meldung: String? = null,
) {
    val hatDaten: Boolean get() = werte.isNotEmpty()
}

@HiltViewModel
class WebFormularViewModel @Inject constructor(
    private val submissions: SubmissionRepository,
    private val actions: ActionRepository,
    private val accounts: AccountRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val submissionId: Long = savedStateHandle.get<Long>("submissionId") ?: -1L

    private val _uiState = MutableStateFlow(WebFormularUiState())
    val uiState: StateFlow<WebFormularUiState> = _uiState.asStateFlow()

    private val datumsformat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    init {
        viewModelScope.launch {
            val eintrag = submissions.ladeAlle().firstOrNull { it.id == submissionId }
            if (eintrag == null) {
                _uiState.update {
                    it.copy(laedt = false, meldung = "Die Einreichung wurde nicht gefunden.")
                }
                return@launch
            }

            val aktion = actions.lade(eintrag.actionId)
            val konto = accounts.lade(eintrag.accountId)

            _uiState.value = WebFormularUiState(
                laedt = false,
                adresse = aktion?.besteAdresse,
                aktionstitel = aktion?.title.orEmpty(),
                werte = Einreichdaten.aus(
                    konto = konto,
                    produktname = eintrag.productName,
                    // Deutsche Schreibweise: Genau so steht es in den Formularen.
                    preis = Money.formatPlain(eintrag.pricePaidCents),
                    kaufdatum = eintrag.purchaseDate.format(datumsformat),
                    haendler = eintrag.retailer,
                    // Manche Anbieter verlangen die Nummer unter dem Strichcode.
                    // Sie kommt aus dem Produktfoto, wenn sie darauf zu sehen war.
                    ean = eintrag.ean,
                ),
                belege = eintrag.belege,
                meldung = if (aktion?.besteAdresse == null) {
                    "Zu dieser Aktion ist keine Adresse hinterlegt."
                } else {
                    null
                },
            )
        }
    }

    /** Das Skript, das die Werte in die offene Seite einträgt. */
    fun skript(): String = Formularskript.baue(_uiState.value.werte)

    fun zeigeMeldung(text: String) = _uiState.update { it.copy(meldung = text) }

    fun meldungGelesen() = _uiState.update { it.copy(meldung = null) }
}
