package de.gzgtracker.ui.erfassen

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.gzgtracker.core.Account
import de.gzgtracker.core.AccountCheck
import de.gzgtracker.core.Belegart
import de.gzgtracker.core.DuplicateAccountRule
import de.gzgtracker.core.Money
import de.gzgtracker.core.PromoAction
import de.gzgtracker.core.Submission
import de.gzgtracker.core.SubmissionStatus
import de.gzgtracker.data.receipt.ReceiptStorage
import de.gzgtracker.data.repository.AccountRepository
import de.gzgtracker.data.repository.ActionRepository
import de.gzgtracker.data.repository.SubmissionRepository
import de.gzgtracker.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ErfassenUiState(
    val laedt: Boolean = true,
    val istNeu: Boolean = true,
    val submissionId: Long? = null,

    val aktionen: List<PromoAction> = emptyList(),
    val konten: List<Account> = emptyList(),

    val aktionId: String? = null,
    val produktname: String = "",
    val ean: String = "",
    val preis: String = "",
    val kaufdatum: LocalDate = LocalDate.now(),
    val haendler: String = "",
    val kontoId: Long? = null,
    val bonPfad: String? = null,
    val produktPfad: String? = null,
    val zusammenPfad: String? = null,
    /** Welcher Belegplatz gerade gefuellt wird — gesetzt, solange der Bildwähler offen ist. */
    val offeneBelegart: Belegart? = null,
    val notiz: String = "",
    val status: SubmissionStatus = SubmissionStatus.GEKAUFT,

    val kontoKonflikt: AccountCheck.BereitsBelegt? = null,
    val regel: DuplicateAccountRule = DuplicateAccountRule.DEFAULT,
    val meldung: String? = null,
    val gespeichert: Boolean = false,
) {
    val gewaehlteAktion: PromoAction?
        get() = aktionen.firstOrNull { it.id == aktionId }

    val preisOk: Boolean
        get() = Money.parseOrNull(preis)?.let { it > 0 } == true

    val hatKonflikt: Boolean get() = kontoKonflikt != null

    /**
     * Blockiert wird nur, wenn die Regel auf "blockieren" steht. Bei "warnen" bleibt
     * der Knopf aktiv — die Entscheidung liegt dann beim Nutzer.
     */
    val kontoBlockiert: Boolean
        get() = hatKonflikt && regel == DuplicateAccountRule.BLOCKIEREN

    val speicherbar: Boolean
        get() = produktname.isNotBlank() &&
            aktionId != null &&
            kontoId != null &&
            preisOk &&
            !kontoBlockiert
}

@HiltViewModel
class ErfassenViewModel @Inject constructor(
    private val submissions: SubmissionRepository,
    private val actions: ActionRepository,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
    private val receipts: ReceiptStorage,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vorgabeAktionId: String? =
        savedStateHandle.get<String>("actionId")?.takeIf { it.isNotBlank() }
    private val vorgabeEan: String =
        savedStateHandle.get<String>("ean").orEmpty()
    private val bearbeiteId: Long? =
        savedStateHandle.get<Long>("submissionId")?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(ErfassenUiState())
    val uiState: StateFlow<ErfassenUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val alleAktionen = actions.ladeAlle()
            val alleKonten = accounts.ladeAlle()
            val einstellungen = settings.settings.first()

            val bestehend = bearbeiteId?.let { id ->
                submissions.ladeAlle().firstOrNull { it.id == id }
            }

            _uiState.value = if (bestehend != null) {
                ErfassenUiState(
                    laedt = false,
                    istNeu = false,
                    submissionId = bestehend.id,
                    aktionen = alleAktionen,
                    konten = alleKonten,
                    aktionId = bestehend.actionId,
                    produktname = bestehend.productName,
                    ean = bestehend.ean.orEmpty(),
                    preis = Money.formatPlain(bestehend.pricePaidCents),
                    kaufdatum = bestehend.purchaseDate,
                    haendler = bestehend.retailer.orEmpty(),
                    kontoId = bestehend.accountId,
                    bonPfad = bestehend.receiptImagePath,
                    produktPfad = bestehend.productImagePath,
                    zusammenPfad = bestehend.comboImagePath,
                    notiz = bestehend.note.orEmpty(),
                    status = bestehend.status,
                    regel = einstellungen.duplicateRule,
                )
            } else {
                // Bei einem gescannten Produkt gleich die passende Aktion und ein
                // freies Konto vorschlagen — das spart zwei Eingaben.
                val aktionId = vorgabeAktionId
                val vorschlag = aktionId?.let { submissions.schlageKontoVor(it) }
                    ?: alleKonten.firstOrNull { it.isActive }

                ErfassenUiState(
                    laedt = false,
                    istNeu = true,
                    aktionen = alleAktionen,
                    konten = alleKonten,
                    aktionId = aktionId,
                    ean = vorgabeEan,
                    produktname = aktionId?.let { id ->
                        alleAktionen.firstOrNull { it.id == id }?.title.orEmpty()
                    }.orEmpty(),
                    kontoId = vorschlag?.id,
                    regel = einstellungen.duplicateRule,
                )
            }

            pruefeKonto()
        }
    }

    fun setzeAktion(id: String) {
        _uiState.update { zustand ->
            val aktion = zustand.aktionen.firstOrNull { it.id == id }
            zustand.copy(
                aktionId = id,
                // Produktnamen nur vorbelegen, solange das Feld unberuehrt ist.
                produktname = zustand.produktname.ifBlank { aktion?.title.orEmpty() },
            )
        }
        viewModelScope.launch {
            // Beim Aktionswechsel passt der alte Kontovorschlag womoeglich nicht mehr.
            val zustand = _uiState.value
            if (zustand.istNeu) {
                val vorschlag = zustand.aktionId?.let { submissions.schlageKontoVor(it) }
                if (vorschlag != null) {
                    _uiState.update { it.copy(kontoId = vorschlag.id) }
                }
            }
            pruefeKonto()
        }
    }

    fun setzeProduktname(wert: String) = _uiState.update { it.copy(produktname = wert) }
    fun setzeEan(wert: String) = _uiState.update { it.copy(ean = wert.filter(Char::isDigit)) }
    fun setzePreis(wert: String) = _uiState.update { it.copy(preis = wert) }
    fun setzeKaufdatum(wert: LocalDate) = _uiState.update { it.copy(kaufdatum = wert) }
    fun setzeHaendler(wert: String) = _uiState.update { it.copy(haendler = wert) }
    fun setzeNotiz(wert: String) = _uiState.update { it.copy(notiz = wert) }
    fun setzeStatus(wert: SubmissionStatus) = _uiState.update { it.copy(status = wert) }

    fun setzeKonto(id: Long) {
        _uiState.update { it.copy(kontoId = id) }
        viewModelScope.launch { pruefeKonto() }
    }

    /** Uebernimmt den Vorschlag aus der Konfliktmeldung. */
    fun nimmVorschlag() {
        val vorschlag = _uiState.value.kontoKonflikt?.vorschlag ?: return
        setzeKonto(vorschlag.id)
    }

    fun zeigeMeldung(text: String) = _uiState.update { it.copy(meldung = text) }

    /** Merkt sich, welcher Belegplatz gemeint ist, bevor der Bildwähler aufgeht. */
    fun waehleBeleg(art: Belegart) {
        _uiState.update { it.copy(offeneBelegart = art) }
    }

    private fun pfadVon(zustand: ErfassenUiState, art: Belegart): String? = when (art) {
        Belegart.PRODUKT -> zustand.produktPfad
        Belegart.BON -> zustand.bonPfad
        Belegart.ZUSAMMEN -> zustand.zusammenPfad
    }

    private fun mitPfad(
        zustand: ErfassenUiState,
        art: Belegart,
        pfad: String?,
    ): ErfassenUiState = when (art) {
        Belegart.PRODUKT -> zustand.copy(produktPfad = pfad)
        Belegart.BON -> zustand.copy(bonPfad = pfad)
        Belegart.ZUSAMMEN -> zustand.copy(zusammenPfad = pfad)
    }

    fun setzeBeleg(quelle: Uri) {
        val art = _uiState.value.offeneBelegart ?: Belegart.BON
        viewModelScope.launch {
            val alt = pfadVon(_uiState.value, art)
            val neu = receipts.uebernehmen(quelle)
            if (neu == null) {
                _uiState.update {
                    it.copy(meldung = "Das Bild ließ sich nicht laden.", offeneBelegart = null)
                }
                return@launch
            }
            // Erst nach erfolgreichem Uebernehmen aufraeumen — sonst waere bei einem
            // Fehlschlag das alte Bild weg und das neue nicht da.
            if (alt != null && alt != neu) receipts.loeschen(alt)
            _uiState.update { mitPfad(it, art, neu).copy(offeneBelegart = null) }
        }
    }

    fun entferneBeleg(art: Belegart) {
        val pfad = pfadVon(_uiState.value, art) ?: return
        viewModelScope.launch {
            receipts.loeschen(pfad)
            _uiState.update { mitPfad(it, art, null) }
        }
    }

    private suspend fun pruefeKonto() {
        val zustand = _uiState.value
        val aktionId = zustand.aktionId
        val kontoId = zustand.kontoId
        if (aktionId == null || kontoId == null) {
            _uiState.update { it.copy(kontoKonflikt = null) }
            return
        }

        val ergebnis = submissions.pruefeKonto(
            actionId = aktionId,
            accountId = kontoId,
            ignoriereSubmissionId = zustand.submissionId,
        )
        _uiState.update {
            it.copy(kontoKonflikt = ergebnis as? AccountCheck.BereitsBelegt)
        }
    }

    fun speichern() {
        val zustand = _uiState.value
        if (!zustand.speicherbar) return
        val preisCents = Money.parseOrNull(zustand.preis) ?: return

        viewModelScope.launch {
            val eintrag = Submission(
                id = zustand.submissionId ?: 0L,
                actionId = requireNotNull(zustand.aktionId),
                accountId = requireNotNull(zustand.kontoId),
                productName = zustand.produktname.trim(),
                ean = zustand.ean.takeIf { it.isNotBlank() },
                pricePaidCents = preisCents,
                purchaseDate = zustand.kaufdatum,
                retailer = zustand.haendler.trim().takeIf { it.isNotBlank() },
                receiptImagePath = zustand.bonPfad,
                productImagePath = zustand.produktPfad,
                comboImagePath = zustand.zusammenPfad,
                status = zustand.status,
                submittedAt = if (zustand.status == SubmissionStatus.GEKAUFT) {
                    null
                } else {
                    LocalDate.now()
                },
                note = zustand.notiz.trim().takeIf { it.isNotBlank() },
            )

            if (zustand.istNeu) {
                submissions.anlegen(eintrag)
                // Was gekauft und eingetragen ist, gehoert nicht mehr auf den
                // Einkaufszettel — sonst haekt man dieselbe Zeile zweimal ab.
                actions.vergiss(eintrag.actionId)
            } else {
                val bestehend = submissions.ladeAlle()
                    .firstOrNull { it.id == zustand.submissionId }
                submissions.aktualisieren(
                    eintrag.copy(
                        createdAt = bestehend?.createdAt ?: java.time.Instant.now(),
                        submittedAt = bestehend?.submittedAt ?: eintrag.submittedAt,
                        refundedAt = bestehend?.refundedAt,
                        refundedAmountCents = bestehend?.refundedAmountCents,
                    ),
                )
            }

            _uiState.update { it.copy(gespeichert = true) }
        }
    }

    fun meldungGelesen() = _uiState.update { it.copy(meldung = null) }
}
