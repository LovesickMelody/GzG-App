package de.gzgtracker.ui.aktionen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.gzgtracker.core.Money
import de.gzgtracker.core.PromoAction
import de.gzgtracker.ui.format.deutsch
import de.gzgtracker.ui.format.relativeAngabe
import de.gzgtracker.ui.theme.MoneyTextStyle
import de.gzgtracker.ui.uebersicht.SucheFeld

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AktionenScreen(
    onAktionErfassen: (String) -> Unit,
    onAktionBearbeiten: (String) -> Unit,
    onAktionAnlegen: () -> Unit,
    viewModel: AktionenViewModel = hiltViewModel(),
) {
    val zustand by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

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
                title = { Text("Aktionen") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAktionAnlegen,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Aktion anlegen") },
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
                SucheFeld(
                    wert = zustand.suche,
                    onWert = viewModel::setzeSuche,
                    onSchliessen = { viewModel.setzeSuche("") },
                )

                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = zustand.nurLaufende,
                        onClick = { viewModel.setzeNurLaufende(!zustand.nurLaufende) },
                        label = { Text("Nur laufende") },
                    )
                    zustand.letzterSync?.let { sync ->
                        Text(
                            text = "Aktualisiert ${sync.relativeAngabe()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (zustand.istLeer) {
                    LeereAktionen(
                        onAnlegen = onAktionAnlegen,
                        onAktualisieren = viewModel::aktualisiere,
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(zustand.aktionen, key = { it.id }) { aktion ->
                            AktionZeile(
                                aktion = aktion,
                                onErfassen = { onAktionErfassen(aktion.id) },
                                onBearbeiten = { onAktionBearbeiten(aktion.id) },
                                onOeffnen = { aktion.url?.let(uriHandler::openUri) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        item { Column(Modifier.padding(bottom = 96.dp)) {} }
                    }
                }
            }
        }
    }
}

@Composable
private fun AktionZeile(
    aktion: PromoAction,
    onErfassen: () -> Unit,
    onBearbeiten: () -> Unit,
    onOeffnen: () -> Unit,
) {
    val tage = aktion.tageBisFrist()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onErfassen)
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = aktion.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                aktion.maxRefundCents?.let { max ->
                    Text(
                        text = "bis ${Money.format(max)}",
                        style = MoneyTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Text(
                text = listOfNotNull(
                    aktion.brand,
                    aktion.retailers.takeIf { it.isNotEmpty() }?.joinToString(", "),
                    if (aktion.isManual) "selbst angelegt" else aktion.source,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = fristText(aktion, tage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (aktion.url != null) {
            IconButton(onClick = onOeffnen) {
                Icon(
                    Icons.Outlined.OpenInNew,
                    contentDescription = "Aktionsseite öffnen",
                )
            }
        }
        IconButton(onClick = onBearbeiten) {
            Icon(Icons.Outlined.Edit, contentDescription = "Aktion bearbeiten")
        }
    }
}

private fun fristText(aktion: PromoAction, tage: Long?): String {
    val frist = aktion.submissionDeadline ?: aktion.validTo ?: return "Ohne Frist"
    val bezeichnung = if (aktion.submissionDeadline != null) "Einsendeschluss" else "Läuft bis"
    return when {
        tage == null -> "$bezeichnung ${frist.deutsch()}"
        tage < 0 -> "Abgelaufen seit ${frist.deutsch()}"
        tage == 0L -> "$bezeichnung heute"
        tage == 1L -> "$bezeichnung morgen"
        tage <= 14 -> "$bezeichnung in $tage Tagen (${frist.deutsch()})"
        else -> "$bezeichnung ${frist.deutsch()}"
    }
}

@Composable
private fun LeereAktionen(onAnlegen: () -> Unit, onAktualisieren: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.LocalOffer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = "Noch keine Aktionen",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Zieh die Liste nach unten, um den Feed zu laden — oder trag eine " +
                "Aktion selbst ein.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.TextButton(onClick = onAktualisieren) {
                Text("Feed laden")
            }
            androidx.compose.material3.TextButton(onClick = onAnlegen) {
                Text("Aktion anlegen")
            }
        }
    }
}
