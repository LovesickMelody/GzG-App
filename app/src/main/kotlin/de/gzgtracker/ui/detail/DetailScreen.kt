package de.gzgtracker.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.gzgtracker.core.Money
import de.gzgtracker.core.Submission
import de.gzgtracker.core.SubmissionStatus
import de.gzgtracker.ui.components.DottedRule
import de.gzgtracker.ui.components.ReceiptLine
import de.gzgtracker.ui.components.StatusStamp
import de.gzgtracker.ui.components.label
import de.gzgtracker.ui.format.deutsch
import de.gzgtracker.ui.format.eanLesbar
import de.gzgtracker.ui.theme.GzgTheme
import de.gzgtracker.ui.theme.MoneyLargeTextStyle
import de.gzgtracker.ui.theme.MoneyTextStyle
import de.gzgtracker.ui.uebersicht.ErstattungDialog
import de.gzgtracker.ui.uebersicht.SubmissionZeile
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onZurueck: () -> Unit,
    onBearbeiten: (Long) -> Unit,
    onEinreichen: (Long) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val zustand by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    var loeschenOffen by remember { mutableStateOf(false) }
    var erstattungOffen by remember { mutableStateOf(false) }
    // Pfad des Bildes, das gerade im Vollbild liegt — null heisst: keins.
    var grossesBild by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(zustand.geloescht) {
        if (zustand.geloescht) onZurueck()
    }

    val eintrag = zustand.submission

    Scaffold(
        // Das aeussere Scaffold in GzgApp rechnet die System-Insets bereits an.
        // Ohne diese Zeile zieht dieses Scaffold sie ein zweites Mal ab, und die
        // Inhalte rutschen um Status- und Navigationsleiste zu weit nach innen.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Beleg") },
                navigationIcon = {
                    IconButton(onClick = onZurueck) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Zurück",
                        )
                    }
                },
                actions = {
                    if (eintrag != null) {
                        IconButton(onClick = { onBearbeiten(eintrag.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Bearbeiten")
                        }
                        IconButton(onClick = { loeschenOffen = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Löschen")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innen ->
        if (eintrag == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innen),
                contentAlignment = Alignment.Center,
            ) {
                if (!zustand.laedt) Text("Dieser Beleg ist nicht mehr da.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innen)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = eintrag.productName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    zustand.action?.let { aktion ->
                        Text(
                            text = listOfNotNull(aktion.title, aktion.brand).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                StatusStamp(
                    status = eintrag.status,
                    stampOnChange = zustand.geradeErstattet,
                )
            }

            DottedRule()

            ReceiptLine(
                label = { Beschriftung("Gezahlt") },
                amount = {
                    Text(
                        text = Money.format(eintrag.pricePaidCents),
                        style = MoneyTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
            )
            ReceiptLine(
                label = { Beschriftung("Erwartete Erstattung") },
                amount = {
                    Text(
                        text = Money.format(zustand.erwarteteErstattungCents),
                        style = MoneyTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
            )
            eintrag.refundedAmountCents?.let { betrag ->
                ReceiptLine(
                    label = { Beschriftung("Tatsächlich erstattet") },
                    amount = {
                        Text(
                            text = Money.format(betrag),
                            style = MoneyLargeTextStyle,
                            color = GzgTheme.status.refunded,
                        )
                    },
                )
            }

            DottedRule()

            Angabe("Kaufdatum", eintrag.purchaseDate.deutsch())
            eintrag.retailer?.let { Angabe("Händler", it) }
            zustand.account?.let { Angabe("Zielkonto", it.name) }
            eintrag.ean?.let { Angabe("EAN", it.eanLesbar()) }
            eintrag.note?.let { Angabe("Notiz", it) }

            // Der Weg zurueck ins Formular. Wer den Vorgang abgebrochen hat —
            // Seite lud nicht, Foto fehlte, Telefon klingelte —, kommt sonst nicht
            // mehr hin, obwohl der Eintrag laengst gespeichert ist.
            if (zustand.action?.besteAdresse != null) {
                Button(
                    onClick = { onEinreichen(eintrag.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (eintrag.status == SubmissionStatus.GEKAUFT) {
                            "Jetzt einreichen"
                        } else {
                            "Nochmal einreichen"
                        },
                    )
                }
            }

            zustand.action?.url?.let { url ->
                OutlinedButton(
                    onClick = { uriHandler.openUri(url) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                    Text("  Aktionsseite öffnen")
                }
            }

            Ueberschrift("Status ändern")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SubmissionStatus.entries.toList()) { status ->
                    FilterChip(
                        selected = eintrag.status == status,
                        onClick = {
                            if (status == SubmissionStatus.ERSTATTET) {
                                erstattungOffen = true
                            } else {
                                viewModel.setzeStatus(status)
                            }
                        },
                        label = { Text(status.label) },
                    )
                }
            }
            if (eintrag.status != SubmissionStatus.ERSTATTET) {
                Button(
                    onClick = { erstattungOffen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Als erstattet markieren")
                }
            }

            Ueberschrift("Verlauf")
            zustand.verlauf.forEach { schritt ->
                VerlaufsZeile(schritt)
            }

            if (eintrag.hatBeleg) {
                Ueberschrift("Belege")
                eintrag.belege.forEach { beleg ->
                    Beschriftung(beleg.art.label)
                    AsyncImage(
                        model = File(beleg.pfad),
                        contentDescription = "${beleg.art.label}, zum Vergrößern antippen",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(4.dp),
                            )
                            .clickable { grossesBild = beleg.pfad },
                    )
                }
            }

            Box(Modifier.height(32.dp))
        }
    }

    if (loeschenOffen) {
        AlertDialog(
            onDismissRequest = { loeschenOffen = false },
            title = { Text("Beleg löschen?") },
            text = {
                Text("Der Eintrag und das gespeicherte Bonfoto werden entfernt.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.loeschen()
                        loeschenOffen = false
                    },
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { loeschenOffen = false }) { Text("Behalten") }
            },
        )
    }

    if (erstattungOffen && eintrag != null) {
        ErstattungDialog(
            zeile = SubmissionZeile(
                submission = eintrag,
                action = zustand.action,
                account = zustand.account,
                betragCents = zustand.erwarteteErstattungCents,
            ),
            onBestaetigen = { betrag, datum ->
                viewModel.setzeStatus(SubmissionStatus.ERSTATTET, datum, betrag)
                erstattungOffen = false
            },
            onAbbrechen = { erstattungOffen = false },
        )
    }

    grossesBild?.let { pfad ->
        BonVollbild(
            pfad = pfad,
            onSchliessen = { grossesBild = null },
        )
    }
}

@Composable
private fun Beschriftung(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Ueberschrift(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun Angabe(label: String, wert: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(
            text = wert,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun VerlaufsZeile(schritt: Verlaufsschritt) {
    val palette = GzgTheme.status
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (schritt.erreicht) {
                        palette.background(schritt.status)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                )
                .border(
                    1.dp,
                    if (schritt.erreicht) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    CircleShape,
                ),
        )
        Text(
            text = schritt.status.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (schritt.erreicht) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = schritt.datum?.deutsch() ?: "—",
            style = MoneyTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BonVollbild(pfad: String, onSchliessen: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onSchliessen) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onSchliessen),
        ) {
            AsyncImage(
                model = File(pfad),
                contentDescription = "Kassenbon",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
