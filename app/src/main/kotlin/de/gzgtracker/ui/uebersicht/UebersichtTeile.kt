package de.gzgtracker.ui.uebersicht

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.gzgtracker.core.Account
import de.gzgtracker.core.Money
import de.gzgtracker.core.PromoAction
import de.gzgtracker.core.SubmissionFilter
import de.gzgtracker.core.SubmissionStatus
import de.gzgtracker.core.TotalsCalculator
import de.gzgtracker.ui.components.DatumFeld
import de.gzgtracker.ui.components.label
import de.gzgtracker.ui.theme.MoneyTextStyle
import java.time.LocalDate

@Composable
fun SucheFeld(
    wert: String,
    onWert: (String) -> Unit,
    onSchliessen: () -> Unit,
) {
    OutlinedTextField(
        value = wert,
        onValueChange = onWert,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        singleLine = true,
        placeholder = { Text("Produkt, Marke, Händler oder EAN") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onSchliessen) {
                Icon(Icons.Outlined.Close, contentDescription = "Suche schließen")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    filter: SubmissionFilter,
    konten: List<Account>,
    aktionen: List<PromoAction>,
    onFilter: (SubmissionFilter) -> Unit,
    onZuruecksetzen: () -> Unit,
    onSchliessen: () -> Unit,
) {
    val sheetZustand = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onSchliessen,
        sheetState = sheetZustand,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Filtern", style = MaterialTheme.typography.headlineSmall)
                TextButton(
                    onClick = onZuruecksetzen,
                    enabled = filter.anzahlKriterien > 0,
                ) {
                    Text("Zurücksetzen")
                }
            }

            FilterAbschnitt("Status") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SubmissionStatus.entries.toList()) { status ->
                        val gewaehlt = status in filter.status
                        FilterChip(
                            selected = gewaehlt,
                            onClick = {
                                val neu = filter.status.toMutableSet().apply {
                                    if (gewaehlt) remove(status) else add(status)
                                }
                                onFilter(filter.copy(status = neu))
                            },
                            label = { Text(status.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor =
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }

            if (konten.isNotEmpty()) {
                FilterAbschnitt("Konto") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(konten) { konto ->
                            val gewaehlt = filter.accountId == konto.id
                            FilterChip(
                                selected = gewaehlt,
                                onClick = {
                                    onFilter(
                                        filter.copy(accountId = if (gewaehlt) null else konto.id),
                                    )
                                },
                                label = { Text(konto.name) },
                                leadingIcon = { KontoPunkt(konto.colorHex) },
                            )
                        }
                    }
                }
            }

            if (aktionen.isNotEmpty()) {
                FilterAbschnitt("Aktion") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(aktionen) { aktion ->
                            val gewaehlt = filter.actionId == aktion.id
                            FilterChip(
                                selected = gewaehlt,
                                onClick = {
                                    onFilter(
                                        filter.copy(actionId = if (gewaehlt) null else aktion.id),
                                    )
                                },
                                label = { Text(aktion.title) },
                            )
                        }
                    }
                }
            }

            FilterAbschnitt("Zeitraum") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DatumFeld(
                        label = "Von",
                        wert = filter.von,
                        onWert = { onFilter(filter.copy(von = it)) },
                        modifier = Modifier.weight(1f),
                        platzhalter = "beliebig",
                    )
                    DatumFeld(
                        label = "Bis",
                        wert = filter.bis,
                        onWert = { onFilter(filter.copy(bis = it)) },
                        modifier = Modifier.weight(1f),
                        platzhalter = "beliebig",
                    )
                }
                if (filter.von != null || filter.bis != null) {
                    TextButton(
                        onClick = { onFilter(filter.copy(von = null, bis = null)) },
                    ) {
                        Text("Zeitraum aufheben")
                    }
                }
            }

            TextButton(
                onClick = onSchliessen,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Fertig")
            }
        }
    }
}

@Composable
private fun FilterAbschnitt(titel: String, inhalt: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = titel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        inhalt()
    }
}

/**
 * Die Kontofarbe erscheint nur als kleiner Punkt in Konto-Zusammenhaengen — sie
 * hilft beim Wiedererkennen, ohne der Statusfarbe Konkurrenz zu machen.
 */
@Composable
fun KontoPunkt(colorHex: String, groesse: Int = 10) {
    val farbe = remember(colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
            .getOrDefault(Color.Gray)
    }
    androidx.compose.foundation.Canvas(Modifier.size(groesse.dp)) {
        drawCircle(farbe)
    }
}

/**
 * Beim Umstellen auf "erstattet" fragt die App nach Datum und tatsaechlichem Betrag.
 * Vorbelegt ist die Erwartung — meist stimmt sie, und dann ist es ein Tipp.
 */
@Composable
fun ErstattungDialog(
    zeile: SubmissionZeile,
    onBestaetigen: (betragCents: Int?, datum: LocalDate) -> Unit,
    onAbbrechen: () -> Unit,
) {
    val erwartet = remember(zeile) {
        TotalsCalculator.erwarteteErstattungCents(zeile.submission, zeile.action)
    }
    var betragText by remember { mutableStateOf(Money.formatPlain(erwartet)) }
    var datum by remember { mutableStateOf(LocalDate.now()) }

    val betragCents = Money.parseOrNull(betragText)
    val betragOk = betragCents != null && betragCents >= 0

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Als erstattet markieren") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = zeile.submission.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = betragText,
                    onValueChange = { betragText = it },
                    label = { Text("Erstatteter Betrag") },
                    suffix = { Text("€") },
                    singleLine = true,
                    isError = !betragOk,
                    supportingText = {
                        if (!betragOk) {
                            Text("Bitte einen Betrag wie 3,99 eintragen.")
                        } else if (betragCents != erwartet) {
                            Text("Erwartet waren ${Money.format(erwartet)}.")
                        }
                    },
                    textStyle = MoneyTextStyle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                DatumFeld(
                    label = "Erstattet am",
                    wert = datum,
                    onWert = { datum = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onBestaetigen(betragCents, datum) },
                enabled = betragOk,
            ) {
                Text("Als erstattet markieren")
            }
        },
        dismissButton = {
            TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
        },
    )
}
