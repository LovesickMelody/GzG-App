package de.gzgtracker.ui.konten

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.gzgtracker.core.Account
import de.gzgtracker.core.Money
import de.gzgtracker.ui.components.ReceiptLine
import de.gzgtracker.ui.theme.MoneySmallTextStyle
import de.gzgtracker.ui.theme.MoneyTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KontenScreen(viewModel: KontenViewModel = hiltViewModel()) {
    val zustand by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var bearbeite by remember { mutableStateOf<Account?>(null) }
    var neuOffen by remember { mutableStateOf(false) }

    LaunchedEffect(zustand.meldung) {
        val meldung = zustand.meldung ?: return@LaunchedEffect
        snackbar.showSnackbar(meldung)
        viewModel.meldungGelesen()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Konten") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { neuOffen = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Konto anlegen") },
            )
        },
    ) { innen ->
        if (zustand.istLeer) {
            LeereKonten(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innen),
                onAnlegen = { neuOffen = true },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innen),
            ) {
                items(zustand.zeilen, key = { it.account.id }) { zeile ->
                    KontoZeileAnsicht(
                        zeile = zeile,
                        onBearbeiten = { bearbeite = zeile.account },
                        onAktiv = { aktiv -> viewModel.setzeAktiv(zeile.account.id, aktiv) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (neuOffen) {
        KontoDialog(
            titel = "Konto anlegen",
            konto = null,
            onSpeichern = { name, iban, farbe ->
                viewModel.anlegen(name, iban, farbe)
                neuOffen = false
            },
            onAbbrechen = { neuOffen = false },
        )
    }

    bearbeite?.let { konto ->
        KontoDialog(
            titel = "Konto bearbeiten",
            konto = konto,
            onSpeichern = { name, iban, farbe ->
                viewModel.aktualisieren(
                    konto.copy(name = name, ibanLast4 = iban, colorHex = farbe),
                )
                bearbeite = null
            },
            onAbbrechen = { bearbeite = null },
            onLoeschen = {
                viewModel.loeschen(konto.id)
                bearbeite = null
            },
            loeschbar = zustand.zeilen
                .firstOrNull { it.account.id == konto.id }
                ?.anzahlEinreichungen == 0,
        )
    }
}

@Composable
private fun KontoZeileAnsicht(
    zeile: KontoZeile,
    onBearbeiten: () -> Unit,
    onAktiv: (Boolean) -> Unit,
) {
    val konto = zeile.account
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBearbeiten)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FarbPunkt(konto.colorHex, 14)
            Column(Modifier.weight(1f)) {
                Text(
                    text = konto.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = listOfNotNull(
                        konto.ibanLast4?.let { "IBAN …$it" },
                        "${zeile.anzahlEinreichungen} Einreichungen",
                        if (!konto.isActive) "deaktiviert" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = konto.isActive,
                onCheckedChange = onAktiv,
            )
        }

        ReceiptLine(
            label = {
                Text(
                    text = "Steht noch aus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            amount = {
                Text(
                    text = Money.format(zeile.totals.ausstehendCents),
                    style = MoneyTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
        )
        ReceiptLine(
            label = {
                Text(
                    text = "Bereits erstattet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            amount = {
                Text(
                    text = Money.format(zeile.totals.erstattetCents),
                    style = MoneySmallTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
private fun KontoDialog(
    titel: String,
    konto: Account?,
    onSpeichern: (name: String, ibanLast4: String?, colorHex: String) -> Unit,
    onAbbrechen: () -> Unit,
    onLoeschen: (() -> Unit)? = null,
    loeschbar: Boolean = false,
) {
    var name by remember { mutableStateOf(konto?.name.orEmpty()) }
    var iban by remember { mutableStateOf(konto?.ibanLast4.orEmpty()) }
    var farbe by remember { mutableStateOf(konto?.colorHex ?: KONTO_FARBEN.first()) }

    val nameOk = name.isNotBlank()
    val ibanOk = iban.isEmpty() || (iban.length == 4 && iban.all(Char::isDigit))

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(titel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("z. B. DKB Giro") },
                    singleLine = true,
                    isError = !nameOk,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = iban,
                    onValueChange = { neu -> iban = neu.filter(Char::isDigit).take(4) },
                    label = { Text("Letzte 4 IBAN-Stellen") },
                    placeholder = { Text("optional") },
                    singleLine = true,
                    isError = !ibanOk,
                    supportingText = { Text("Die volle IBAN braucht die App nicht.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Farbe",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KONTO_FARBEN.forEach { hex ->
                        FarbWahl(
                            hex = hex,
                            gewaehlt = hex.equals(farbe, ignoreCase = true),
                            onWahl = { farbe = hex },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSpeichern(name.trim(), iban.ifBlank { null }, farbe) },
                enabled = nameOk && ibanOk,
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            Row {
                if (onLoeschen != null && loeschbar) {
                    TextButton(onClick = onLoeschen) { Text("Löschen") }
                }
                TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
            }
        },
    )
}

@Composable
private fun FarbWahl(hex: String, gewaehlt: Boolean, onWahl: () -> Unit) {
    val farbe = farbeAus(hex)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(farbe)
            .border(
                width = if (gewaehlt) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
            )
            .clickable(onClick = onWahl),
    )
}

@Composable
fun FarbPunkt(hex: String, groesse: Int) {
    Box(
        modifier = Modifier
            .size(groesse.dp)
            .clip(CircleShape)
            .background(farbeAus(hex)),
    )
}

private fun farbeAus(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

@Composable
private fun LeereKonten(modifier: Modifier = Modifier, onAnlegen: () -> Unit) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.CreditCard,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = "Noch keine Konten",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Leg deine Konten an, dann verteilt die App die Erstattungen " +
                "automatisch darauf.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onAnlegen) { Text("Erstes Konto anlegen") }
    }
}
