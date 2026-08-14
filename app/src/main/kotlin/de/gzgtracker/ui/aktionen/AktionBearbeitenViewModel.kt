package de.gzgtracker.ui.aktionen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.gzgtracker.core.Money
import de.gzgtracker.core.PromoAction
import de.gzgtracker.core.PromoActionType
import de.gzgtracker.data.repository.ActionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AktionFormular(
    val istNeu: Boolean = true,
    val id: String? = null,
    val titel: String = "",
    val marke: String = "",
    val art: PromoActionType = PromoActionType.GRATIS_TESTEN,
    val maxErstattung: String = "",
    val gueltigBis: LocalDate? = null,
    val einsendeschluss: LocalDate? = null,
    val haendler: String = "",
    val eans: String = "",
    val url: String = "",
    val gespeichert: Boolean = false,
) {
    val maxErstattungOk: Boolean
        get() = maxErstattung.isBlank() || Money.parseOrNull(maxErstattung)?.let { it >= 0 } == true

    val speicherbar: Boolean
        get() = titel.isNotBlank() && maxErstattungOk
}

@HiltViewModel
class AktionBearbeitenViewModel @Inject constructor(
    private val actions: ActionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val actionId: String? =
        savedStateHandle.get<String>("actionId")?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(AktionFormular(istNeu = actionId == null))
    val uiState: StateFlow<AktionFormular> = _uiState.asStateFlow()

    init {
        actionId?.let { id ->
            viewModelScope.launch {
                val aktion = actions.lade(id) ?: return@launch
                _uiState.value = AktionFormular(
                    istNeu = false,
                    id = aktion.id,
                    titel = aktion.title,
                    marke = aktion.brand.orEmpty(),
                    art = aktion.type,
                    maxErstattung = aktion.maxRefundCents?.let(Money::formatPlain).orEmpty(),
                    gueltigBis = aktion.validTo,
                    einsendeschluss = aktion.submissionDeadline,
                    haendler = aktion.retailers.joinToString(", "),
                    eans = aktion.eans.joinToString(", "),
                    url = aktion.url.orEmpty(),
                )
            }
        }
    }

    fun setzeTitel(wert: String) = _uiState.update { it.copy(titel = wert) }
    fun setzeMarke(wert: String) = _uiState.update { it.copy(marke = wert) }
    fun setzeArt(wert: PromoActionType) = _uiState.update { it.copy(art = wert) }
    fun setzeMaxErstattung(wert: String) = _uiState.update { it.copy(maxErstattung = wert) }
    fun setzeGueltigBis(wert: LocalDate) = _uiState.update { it.copy(gueltigBis = wert) }
    fun setzeEinsendeschluss(wert: LocalDate) = _uiState.update { it.copy(einsendeschluss = wert) }
    fun setzeHaendler(wert: String) = _uiState.update { it.copy(haendler = wert) }
    fun setzeEans(wert: String) = _uiState.update { it.copy(eans = wert) }
    fun setzeUrl(wert: String) = _uiState.update { it.copy(url = wert) }

    fun speichern() {
        val formular = _uiState.value
        if (!formular.speicherbar) return

        viewModelScope.launch {
            val bestehend = formular.id?.let { actions.lade(it) }
            val aktion = PromoAction(
                id = formular.id.orEmpty(),
                title = formular.titel.trim(),
                brand = formular.marke.trim().takeIf { it.isNotBlank() },
                type = formular.art,
                maxRefundCents = formular.maxErstattung
                    .takeIf { it.isNotBlank() }
                    ?.let(Money::parseOrNull),
                validFrom = bestehend?.validFrom,
                validTo = formular.gueltigBis,
                submissionDeadline = formular.einsendeschluss,
                url = formular.url.trim().takeIf { it.isNotBlank() },
                retailers = zerlege(formular.haendler),
                eans = zerlege(formular.eans).filter { eintrag ->
                    (eintrag.length == 8 || eintrag.length == 13) && eintrag.all(Char::isDigit)
                },
                imageUrl = bestehend?.imageUrl,
                // Eine aus dem Feed stammende Aktion bleibt ihrer Quelle zugeordnet,
                // damit der naechste Abgleich sie nicht als verwaist wegraeumt.
                source = bestehend?.source ?: "manuell",
                isManual = bestehend?.isManual ?: true,
            )

            if (formular.istNeu) {
                actions.legeManuellAn(aktion)
            } else {
                actions.aktualisiere(aktion)
            }
            _uiState.update { it.copy(gespeichert = true) }
        }
    }

    private fun zerlege(eingabe: String): List<String> =
        eingabe.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
}
