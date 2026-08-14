package de.gzgtracker.ui.aktionen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.gzgtracker.core.PromoAction
import de.gzgtracker.data.repository.ActionRepository
import de.gzgtracker.data.repository.FeedErgebnis
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

data class AktionenUiState(
    val laedt: Boolean = true,
    val aktionen: List<PromoAction> = emptyList(),
    val suche: String = "",
    val nurLaufende: Boolean = true,
    val aktualisiertGerade: Boolean = false,
    val letzterSync: Instant? = null,
    val meldung: String? = null,
) {
    val istLeer: Boolean get() = !laedt && aktionen.isEmpty()
}

@HiltViewModel
class AktionenViewModel @Inject constructor(
    private val actions: ActionRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val eingaben = MutableStateFlow(Eingaben())

    val uiState: StateFlow<AktionenUiState> = combine(
        actions.alle,
        eingaben,
        settings.settings,
    ) { alle, eingabe, einstellungen ->
        val heute = LocalDate.now()
        val begriff = eingabe.suche.trim().lowercase()

        AktionenUiState(
            laedt = false,
            aktionen = alle
                .filter { aktion ->
                    !eingabe.nurLaufende || aktion.laeuftNoch(heute)
                }
                .filter { aktion ->
                    begriff.isEmpty() ||
                        aktion.title.lowercase().contains(begriff) ||
                        aktion.brand?.lowercase()?.contains(begriff) == true ||
                        aktion.retailers.any { it.lowercase().contains(begriff) } ||
                        aktion.eans.any { it.contains(begriff) }
                },
            suche = eingabe.suche,
            nurLaufende = eingabe.nurLaufende,
            aktualisiertGerade = eingabe.aktualisiert,
            letzterSync = einstellungen.lastSyncAt,
            meldung = eingabe.meldung,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AktionenUiState(),
    )

    fun setzeSuche(begriff: String) = eingaben.update { it.copy(suche = begriff) }

    fun setzeNurLaufende(nur: Boolean) = eingaben.update { it.copy(nurLaufende = nur) }

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
        val aktualisiert: Boolean = false,
        val meldung: String? = null,
    )
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
