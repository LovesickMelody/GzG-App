package de.gzgtracker.ui.aktionen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.gzgtracker.core.PromoActionType
import de.gzgtracker.ui.components.DatumFeld
import de.gzgtracker.ui.theme.MoneyTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AktionBearbeitenScreen(
    onFertig: () -> Unit,
    viewModel: AktionBearbeitenViewModel = hiltViewModel(),
) {
    val zustand by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(zustand.gespeichert) {
        if (zustand.gespeichert) onFertig()
    }

    Scaffold(
        // Das aeussere Scaffold in GzgApp rechnet die System-Insets bereits an.
        // Ohne diese Zeile zieht dieses Scaffold sie ein zweites Mal ab, und die
        // Inhalte rutschen um Status- und Navigationsleiste zu weit nach innen.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (zustand.istNeu) "Aktion anlegen" else "Aktion bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onFertig) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Zurück",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innen ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innen)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = zustand.titel,
                onValueChange = viewModel::setzeTitel,
                label = { Text("Titel") },
                placeholder = { Text("z. B. Duschgel gratis testen") },
                isError = zustand.titel.isBlank(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = zustand.marke,
                onValueChange = viewModel::setzeMarke,
                label = { Text("Marke") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Art der Aktion",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArtChip(
                    "Gratis testen",
                    zustand.art == PromoActionType.GRATIS_TESTEN,
                ) { viewModel.setzeArt(PromoActionType.GRATIS_TESTEN) }
                ArtChip(
                    "Teil-Cashback",
                    zustand.art == PromoActionType.CASHBACK_TEILBETRAG,
                ) { viewModel.setzeArt(PromoActionType.CASHBACK_TEILBETRAG) }
            }

            OutlinedTextField(
                value = zustand.maxErstattung,
                onValueChange = viewModel::setzeMaxErstattung,
                label = { Text("Höchste Erstattung") },
                placeholder = { Text("z. B. 3,99") },
                suffix = { Text("€") },
                singleLine = true,
                isError = !zustand.maxErstattungOk,
                supportingText = {
                    if (!zustand.maxErstattungOk) {
                        Text("Bitte einen Betrag wie 3,99 eintragen oder das Feld leer lassen.")
                    } else {
                        Text("Leer lassen, wenn der volle Kaufpreis erstattet wird.")
                    }
                },
                textStyle = MoneyTextStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DatumFeld(
                    label = "Läuft ab",
                    wert = zustand.gueltigBis,
                    onWert = viewModel::setzeGueltigBis,
                    modifier = Modifier.weight(1f),
                    platzhalter = "optional",
                )
                DatumFeld(
                    label = "Einsendeschluss",
                    wert = zustand.einsendeschluss,
                    onWert = viewModel::setzeEinsendeschluss,
                    modifier = Modifier.weight(1f),
                    platzhalter = "optional",
                )
            }

            OutlinedTextField(
                value = zustand.haendler,
                onValueChange = viewModel::setzeHaendler,
                label = { Text("Händler") },
                placeholder = { Text("dm, Rossmann") },
                supportingText = { Text("Mehrere mit Komma trennen.") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = zustand.eans,
                onValueChange = viewModel::setzeEans,
                label = { Text("EANs") },
                placeholder = { Text("4001234567890") },
                supportingText = {
                    Text("Mehrere mit Komma trennen. Damit findet der Scan die Aktion.")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = zustand.url,
                onValueChange = viewModel::setzeUrl,
                label = { Text("Aktionsseite") },
                placeholder = { Text("https://…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::speichern,
                enabled = zustand.speicherbar,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
            ) {
                Text("Aktion speichern")
            }
        }
    }
}

@Composable
private fun ArtChip(text: String, gewaehlt: Boolean, onWahl: () -> Unit) {
    FilterChip(selected = gewaehlt, onClick = onWahl, label = { Text(text) })
}
