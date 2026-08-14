package de.gzgtracker.ui.uebersicht

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.gzgtracker.core.SubmissionStatus
import de.gzgtracker.ui.components.StatusStamp
import de.gzgtracker.ui.components.SubmissionRow
import de.gzgtracker.ui.components.SummaryCard
import de.gzgtracker.ui.components.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UebersichtScreen(
    onEintragOeffnen: (Long) -> Unit,
    onScannen: () -> Unit,
    onErfassen: () -> Unit,
    viewModel: UebersichtViewModel = hiltViewModel(),
) {
    val zustand by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var filterOffen by remember { mutableStateOf(false) }
    var sucheOffen by remember { mutableStateOf(false) }
    var erstattungFuer by remember { mutableStateOf<SubmissionZeile?>(null) }

    LaunchedEffect(zustand.meldung) {
        val meldung = zustand.meldung ?: return@LaunchedEffect
        snackbar.showSnackbar(meldung)
        viewModel.meldungGelesen()
    }

    Scaffold(
        // Das aeussere Scaffold in GzgApp rechnet die System-Insets bereits an.
        // Ohne diese Zeile zieht dieses Scaffold sie ein zweites Mal ab, und die
        // Inhalte rutschen um Status- und Navigationsleiste zu weit nach innen.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Belege") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                actions = {
                    IconButton(onClick = { sucheOffen = !sucheOffen }) {
                        Icon(
                            if (sucheOffen) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                            contentDescription = if (sucheOffen) {
                                "Suche schließen"
                            } else {
                                "Belege durchsuchen"
                            },
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.exportiere { uri ->
                                if (uri != null) {
                                    val teilen = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(teilen, "Liste teilen"),
                                    )
                                }
                            }
                        },
                        enabled = zustand.zeilen.isNotEmpty(),
                    ) {
                        Icon(Icons.Outlined.IosShare, contentDescription = "Als CSV teilen")
                    }
                    BadgedBox(
                        badge = {
                            if (zustand.filter.anzahlKriterien > 0) {
                                Badge { Text(zustand.filter.anzahlKriterien.toString()) }
                            }
                        },
                    ) {
                        IconButton(onClick = { filterOffen = true }) {
                            Icon(Icons.Outlined.FilterList, contentDescription = "Filtern")
                        }
                    }
                    IconButton(onClick = onScannen) {
                        Icon(
                            Icons.Outlined.QrCodeScanner,
                            contentDescription = "Produkt scannen",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // Die Hauptaktion ist das Eintragen eines gekauften Produkts — das
            // ist der Weg, den man taeglich geht. Der Barcode-Scan hilft nur im
            // Sonderfall "steht im Laden vor einem Produkt und will wissen, ob
            // dazu eine Aktion laeuft"; er sitzt deshalb in der Titelzeile.
            ExtendedFloatingActionButton(
                onClick = onErfassen,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Beleg eintragen") },
                // Eigene Beschriftung, weil die Beschriftung aus dem `text`-Slot
                // nicht im Baum der Bedienhilfen ankommt — der Emulator-Test fand
                // an dieser Stelle einen Knopf ganz ohne Namen. Ein Screenreader
                // haette die Hauptaktion der App also gar nicht angesagt.
                modifier = Modifier.semantics { contentDescription = "Beleg eintragen" },
            )
        },
    ) { innen ->
        PullToRefreshBox(
            isRefreshing = zustand.aktualisiertGerade,
            onRefresh = viewModel::aktualisiere,
            modifier = Modifier
                .fillMaxSize()
                .padding(innen),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (sucheOffen) {
                    SucheFeld(
                        wert = zustand.filter.suche,
                        onWert = viewModel::setzeSuche,
                        onSchliessen = {
                            viewModel.setzeSuche("")
                            sucheOffen = false
                        },
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    item(key = "summen") {
                        SummaryCard(
                            totals = zustand.totals,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 16.dp,
                            ),
                        )
                    }

                    if (zustand.istLeer) {
                        item(key = "leer") {
                            LeererZustand(
                                gefiltert = zustand.filter.istAktiv,
                                onZuruecksetzen = viewModel::filterZuruecksetzen,
                                onErfassen = onErfassen,
                            )
                        }
                    }

                    items(zustand.zeilen, key = { it.submission.id }) { zeile ->
                        SwipeZeile(
                            zeile = zeile,
                            stempeln = zustand.geradeErstattet == zeile.submission.id,
                            onOeffnen = { onEintragOeffnen(zeile.submission.id) },
                            onWeiterstufen = { naechster ->
                                if (naechster == SubmissionStatus.ERSTATTET) {
                                    erstattungFuer = zeile
                                } else {
                                    viewModel.setzeStatus(zeile.submission.id, naechster)
                                }
                            },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 1.dp,
                        )
                    }
                }
            }
        }
    }

    if (filterOffen) {
        FilterSheet(
            filter = zustand.filter,
            konten = zustand.konten,
            aktionen = zustand.aktionen,
            onFilter = viewModel::setzeFilter,
            onZuruecksetzen = viewModel::filterZuruecksetzen,
            onSchliessen = { filterOffen = false },
        )
    }

    erstattungFuer?.let { zeile ->
        ErstattungDialog(
            zeile = zeile,
            onBestaetigen = { betragCents, datum ->
                viewModel.setzeStatus(
                    id = zeile.submission.id,
                    status = SubmissionStatus.ERSTATTET,
                    am = datum,
                    erstatteterBetragCents = betragCents,
                )
                erstattungFuer = null
            },
            onAbbrechen = { erstattungFuer = null },
        )
    }
}

/**
 * Wischen als Abkuerzung: nach rechts eine Stufe weiter, nach links abgelehnt.
 * Der Eintrag verschwindet dabei nie — deshalb gibt `confirmValueChange` immer
 * `false` zurueck und die Zeile federt zurueck.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeZeile(
    zeile: SubmissionZeile,
    stempeln: Boolean,
    onOeffnen: () -> Unit,
    onWeiterstufen: (SubmissionStatus) -> Unit,
) {
    val naechster = zeile.submission.status.naechsteStufe()

    val zustand = rememberSwipeToDismissBoxState(
        confirmValueChange = { richtung ->
            when (richtung) {
                SwipeToDismissBoxValue.StartToEnd -> naechster?.let(onWeiterstufen)
                SwipeToDismissBoxValue.EndToStart ->
                    onWeiterstufen(SubmissionStatus.ABGELEHNT)

                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )

    SwipeToDismissBox(
        state = zustand,
        enableDismissFromStartToEnd = naechster != null,
        enableDismissFromEndToStart = zeile.submission.status != SubmissionStatus.ABGELEHNT,
        backgroundContent = {
            SwipeHintergrund(
                richtung = zustand.dismissDirection,
                naechster = naechster,
            )
        },
    ) {
        SubmissionRow(
            submission = zeile.submission,
            action = zeile.action,
            account = zeile.account,
            betragCents = zeile.betragCents,
            onClick = onOeffnen,
            stampOnChange = stempeln,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeHintergrund(
    richtung: SwipeToDismissBoxValue,
    naechster: SubmissionStatus?,
) {
    val ziel = when (richtung) {
        SwipeToDismissBoxValue.StartToEnd -> naechster
        SwipeToDismissBoxValue.EndToStart -> SubmissionStatus.ABGELEHNT
        SwipeToDismissBoxValue.Settled -> null
    } ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 20.dp),
        contentAlignment = if (richtung == SwipeToDismissBoxValue.StartToEnd) {
            Alignment.CenterStart
        } else {
            Alignment.CenterEnd
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Als ${ziel.label.lowercase()} markieren",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusStamp(status = ziel)
        }
    }
}

/** Die naechste sinnvolle Stufe im Ablauf. Endzustaende haben keine. */
fun SubmissionStatus.naechsteStufe(): SubmissionStatus? = when (this) {
    SubmissionStatus.GEKAUFT -> SubmissionStatus.EINGEREICHT
    SubmissionStatus.EINGEREICHT -> SubmissionStatus.ERSTATTET
    SubmissionStatus.ERSTATTET -> null
    SubmissionStatus.ABGELEHNT -> null
}

@Composable
private fun LeererZustand(
    gefiltert: Boolean,
    onZuruecksetzen: () -> Unit,
    onErfassen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.ReceiptLong,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = if (gefiltert) "Nichts gefunden" else "Noch keine Einreichungen",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (gefiltert) {
                "Kein Beleg passt zu diesen Filtern. Setz sie zurück, um alles zu sehen."
            } else {
                "Such dir eine Aktion, kauf das Produkt und trag den Beleg hier ein."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        androidx.compose.material3.TextButton(
            onClick = if (gefiltert) onZuruecksetzen else onErfassen,
        ) {
            Text(if (gefiltert) "Filter zurücksetzen" else "Von Hand eintragen")
        }
    }
}
