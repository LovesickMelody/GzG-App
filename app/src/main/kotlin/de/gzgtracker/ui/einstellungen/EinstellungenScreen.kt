package de.gzgtracker.ui.einstellungen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.gzgtracker.core.DuplicateAccountRule
import de.gzgtracker.ui.format.relativeAngabe

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EinstellungenScreen(viewModel: EinstellungenViewModel = hiltViewModel()) {
    val zustand by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var feedUrlEntwurf by remember(zustand.feedUrl) { mutableStateOf(zustand.feedUrl) }

    LaunchedEffect(zustand.meldung) {
        val meldung = zustand.meldung ?: return@LaunchedEffect
        snackbar.showSnackbar(meldung)
        viewModel.meldungGelesen()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Abschnitt("Kontoregel")
            Text(
                text = "Auf dasselbe Konto darf pro Aktion nur einmal erstattet werden. " +
                    "Was soll passieren, wenn du ein bereits belegtes Konto wählst?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup()) {
                RegelWahl(
                    text = "Warnen",
                    beschreibung = "Deutlich hinweisen, Speichern bleibt möglich.",
                    gewaehlt = zustand.duplicateRule == DuplicateAccountRule.WARNEN,
                    onWahl = { viewModel.setzeRegel(DuplicateAccountRule.WARNEN) },
                )
                RegelWahl(
                    text = "Blockieren",
                    beschreibung = "Speichern erst, wenn ein freies Konto gewählt ist.",
                    gewaehlt = zustand.duplicateRule == DuplicateAccountRule.BLOCKIEREN,
                    onWahl = { viewModel.setzeRegel(DuplicateAccountRule.BLOCKIEREN) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Abschnitt("Aktions-Feed")
            OutlinedTextField(
                value = feedUrlEntwurf,
                onValueChange = { feedUrlEntwurf = it },
                label = { Text("Feed-URL") },
                supportingText = {
                    Text(
                        "Zeigt auf die Datei data/actions.json im Repository. " +
                            "Bei einem privaten Repository kann die App sie nicht laden.",
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { viewModel.setzeFeedUrl(feedUrlEntwurf) },
                    enabled = feedUrlEntwurf.isNotBlank() && feedUrlEntwurf != zustand.feedUrl,
                ) {
                    Text("URL speichern")
                }
                TextButton(onClick = { viewModel.setzeFeedUrl("") }) {
                    Text("Standard verwenden")
                }
            }

            ZeileMitSchalter(
                titel = "Beim Start aktualisieren",
                beschreibung = "Holt den Feed einmal pro App-Start, wenn Netz da ist.",
                an = zustand.autoSync,
                onAn = viewModel::setzeAutoSync,
            )

            Text(
                text = zustand.letzterSync
                    ?.let { "Zuletzt aktualisiert ${it.relativeAngabe()}" }
                    ?: "Noch nie aktualisiert",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = viewModel::aktualisiere,
                enabled = !zustand.aktualisiertGerade,
            ) {
                Text(if (zustand.aktualisiertGerade) "Wird geladen…" else "Jetzt aktualisieren")
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Abschnitt("Über die App")
            Text(
                text = "Alle Daten bleiben auf diesem Gerät: keine Konten, kein Server, " +
                    "keine Analyse. Die App reicht nichts automatisch bei Anbietern ein — " +
                    "das machst du selbst auf deren Seite.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun Abschnitt(titel: String) {
    Text(
        text = titel,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun RegelWahl(
    text: String,
    beschreibung: String,
    gewaehlt: Boolean,
    onWahl: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = gewaehlt, onClick = onWahl, role = Role.RadioButton)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = gewaehlt, onClick = null)
        Column {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = beschreibung,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ZeileMitSchalter(
    titel: String,
    beschreibung: String,
    an: Boolean,
    onAn: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = beschreibung,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = an, onCheckedChange = onAn)
    }
}
