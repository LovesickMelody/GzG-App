package de.gzgtracker.ui.einstellungen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.gzgtracker.core.DuplicateAccountRule
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
import javax.inject.Inject

data class EinstellungenUiState(
    val duplicateRule: DuplicateAccountRule = DuplicateAccountRule.DEFAULT,
    val feedUrl: String = "",
    val autoSync: Boolean = true,
    val letzterSync: Instant? = null,
    val aktualisiertGerade: Boolean = false,
    val meldung: String? = null,
)

@HiltViewModel
class EinstellungenViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val actions: ActionRepository,
) : ViewModel() {

    private val fluechtig = MutableStateFlow(Fluechtig())

    val uiState: StateFlow<EinstellungenUiState> = combine(
        settings.settings,
        fluechtig,
    ) { gespeichert, transient ->
        EinstellungenUiState(
            duplicateRule = gespeichert.duplicateRule,
            feedUrl = gespeichert.feedUrl,
            autoSync = gespeichert.autoSyncBeimStart,
            letzterSync = gespeichert.lastSyncAt,
            aktualisiertGerade = transient.aktualisiert,
            meldung = transient.meldung,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EinstellungenUiState(),
    )

    fun setzeRegel(regel: DuplicateAccountRule) {
        viewModelScope.launch {
            settings.setzeDuplicateRule(regel)
            fluechtig.update {
                it.copy(
                    meldung = when (regel) {
                        DuplicateAccountRule.WARNEN -> "Bei doppeltem Konto wird gewarnt."
                        DuplicateAccountRule.BLOCKIEREN -> "Doppeltes Konto wird blockiert."
                    },
                )
            }
        }
    }

    fun setzeFeedUrl(url: String) {
        viewModelScope.launch {
            settings.setzeFeedUrl(url)
            fluechtig.update {
                it.copy(
                    meldung = if (url.isBlank()) {
                        "Standard-Feed wiederhergestellt."
                    } else {
                        "Feed-URL gespeichert."
                    },
                )
            }
        }
    }

    fun setzeAutoSync(aktiv: Boolean) {
        viewModelScope.launch { settings.setzeAutoSync(aktiv) }
    }

    fun aktualisiere() {
        if (fluechtig.value.aktualisiert) return
        viewModelScope.launch {
            fluechtig.update { it.copy(aktualisiert = true) }
            val meldung = when (val ergebnis = actions.aktualisiereFeed()) {
                is FeedErgebnis.Erfolg -> "${ergebnis.aktionen} Aktionen geladen."
                is FeedErgebnis.Fehler -> ergebnis.meldung
            }
            fluechtig.update { it.copy(aktualisiert = false, meldung = meldung) }
        }
    }

    fun meldungGelesen() = fluechtig.update { it.copy(meldung = null) }

    private data class Fluechtig(
        val aktualisiert: Boolean = false,
        val meldung: String? = null,
    )
}
