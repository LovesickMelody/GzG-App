package de.gzgtracker.ui.konten

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.gzgtracker.core.Account
import de.gzgtracker.core.Totals
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
import javax.inject.Inject

/** Vorschlagsfarben fuer neue Konten — zurueckhaltend, damit nichts mit Status kollidiert. */
val KONTO_FARBEN = listOf(
    "#4C6EF5",
    "#7048E8",
    "#0CA678",
    "#F76707",
    "#495057",
    "#C2255C",
)

data class KontoZeile(
    val account: Account,
    val totals: Totals,
    val anzahlEinreichungen: Int,
)

data class KontenUiState(
    val laedt: Boolean = true,
    val zeilen: List<KontoZeile> = emptyList(),
    val meldung: String? = null,
) {
    val istLeer: Boolean get() = !laedt && zeilen.isEmpty()
}

@HiltViewModel
class KontenViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val submissions: SubmissionRepository,
    private val actions: ActionRepository,
) : ViewModel() {

    private val meldung = MutableStateFlow<String?>(null)

    val uiState: StateFlow<KontenUiState> = combine(
        accounts.alle,
        submissions.alle,
        actions.alle,
        meldung,
    ) { alleKonten, alleEinreichungen, alleAktionen, aktuelleMeldung ->
        val aktionenById = alleAktionen.associateBy { it.id }
        val summenJeKonto = TotalsCalculator.jeKonto(alleEinreichungen, aktionenById)

        KontenUiState(
            laedt = false,
            zeilen = alleKonten.map { konto ->
                KontoZeile(
                    account = konto,
                    totals = summenJeKonto[konto.id] ?: Totals(),
                    anzahlEinreichungen = alleEinreichungen.count { it.accountId == konto.id },
                )
            },
            meldung = aktuelleMeldung,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KontenUiState(),
    )

    fun anlegen(name: String, ibanLast4: String?, colorHex: String) {
        viewModelScope.launch {
            accounts.anlegen(name, ibanLast4, colorHex)
            meldung.value = "Konto „${name.trim()}“ angelegt."
        }
    }

    fun aktualisieren(account: Account) {
        viewModelScope.launch {
            accounts.aktualisieren(account)
            meldung.value = "Konto gespeichert."
        }
    }

    fun setzeAktiv(id: Long, aktiv: Boolean) {
        viewModelScope.launch {
            accounts.setzeAktiv(id, aktiv)
            meldung.value = if (aktiv) {
                "Konto wieder aktiv."
            } else {
                "Konto deaktiviert. Bestehende Einreichungen bleiben erhalten."
            }
        }
    }

    /** Loeschen geht nur, solange nichts daran haengt — sonst bleibt Deaktivieren. */
    fun loeschen(id: Long) {
        viewModelScope.launch {
            meldung.value = if (accounts.loeschen(id)) {
                "Konto gelöscht."
            } else {
                "Konto hat Einreichungen und lässt sich nur deaktivieren."
            }
        }
    }

    fun meldungGelesen() {
        meldung.update { null }
    }
}
