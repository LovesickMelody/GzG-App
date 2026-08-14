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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextDecoration
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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.gzgtracker.core.Money
import de.gzgtracker.core.PromoAction
import de.gzgtracker.ui.components.TeilnahmeKurz
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
                actions = {
                    // Eine Aktion von Hand anzulegen ist der Ausnahmefall — der
                    // Feed bringt sie sonst mit. Ein großer Knopf am Daumen
                    // hätte hier nichts verloren.
                    IconButton(onClick = onAktionAnlegen) {
                        Icon(Icons.Outlined.Add, contentDescription = "Aktion anlegen")
                    }
                },
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
                    FilterChip(
                        selected = zustand.nurMerkliste,
                        onClick = { viewModel.setzeNurMerkliste(!zustand.nurMerkliste) },
                        label = {
                            Text(
                                if (zustand.anzahlGemerkt > 0) {
                                    "Merkliste (${zustand.anzahlGemerkt})"
                                } else {
                                    "Merkliste"
                                },
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    if (!zustand.nurMerkliste) {
                        zustand.letzterSync?.let { sync ->
                            Text(
                                text = "Aktualisiert ${sync.relativeAngabe()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Kopfzeile des Einkaufszettels: was noch fehlt, und der Knopf
                // zum Aufraeumen nach dem Einkauf.
                if (zustand.nurMerkliste && zustand.anzahlGemerkt > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when (zustand.nochZuKaufen) {
                                0 -> "Alles im Wagen"
                                1 -> "Noch 1 Produkt zu kaufen"
                                else -> "Noch ${zustand.nochZuKaufen} Produkte zu kaufen"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (zustand.hatErledigte) {
                            TextButton(onClick = viewModel::entferneErledigte) {
                                Text("Abgehakte entfernen")
                            }
                        }
                    }
                }

                if (zustand.istLeer) {
                    if (zustand.nurMerkliste) {
                        LeereMerkliste(onAlleZeigen = { viewModel.setzeNurMerkliste(false) })
                    } else {
                        LeereAktionen(
                            onAnlegen = onAktionAnlegen,
                            onAktualisieren = viewModel::aktualisiere,
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(zustand.aktionen, key = { it.id }) { aktion ->
                            AktionZeile(
                                aktion = aktion,
                                gemerkt = aktion.id in zustand.gemerkt,
                                imWagen = zustand.gemerkt[aktion.id] == true,
                                einkaufsmodus = zustand.nurMerkliste,
                                onErfassen = { onAktionErfassen(aktion.id) },
                                onBearbeiten = { onAktionBearbeiten(aktion.id) },
                                onMerken = { viewModel.merkenUmschalten(aktion.id) },
                                onImWagen = { viewModel.setzeImWagen(aktion.id, it) },
                                onOeffnen = { aktion.besteAdresse?.let(uriHandler::openUri) },
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
    gemerkt: Boolean,
    imWagen: Boolean,
    einkaufsmodus: Boolean,
    onErfassen: () -> Unit,
    onBearbeiten: () -> Unit,
    onMerken: () -> Unit,
    onImWagen: (Boolean) -> Unit,
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
        // Im Einkaufsmodus steht das Häkchen vorn — dort greift der Daumen im
        // Laden hin, mit dem Wagen in der anderen Hand.
        if (einkaufsmodus) {
            Checkbox(
                checked = imWagen,
                onCheckedChange = onImWagen,
            )
        }

        // Produktbild aus dem Feed. Im Laden erkennt man die Packung schneller
        // wieder als den Produktnamen — und viele Titel sind ohnehin kryptisch.
        aktion.imageUrl?.let { adresse ->
            AsyncImage(
                model = adresse,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(4.dp),
                    ),
            )
        }

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
                    color = if (imWagen && einkaufsmodus) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (imWagen && einkaufsmodus) {
                        TextDecoration.LineThrough
                    } else {
                        null
                    },
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

            TeilnahmeKurz(aktion.requirements)
        }

        if (aktion.besteAdresse != null) {
            IconButton(onClick = onOeffnen) {
                Icon(
                    Icons.Outlined.OpenInNew,
                    // Der Unterschied zählt: Bei der einen Quelle landet man
                    // direkt im Formular, bei der anderen erst auf der
                    // Portalseite. Wer das vorher weiß, klickt richtig.
                    contentDescription = if (aktion.fuehrtDirektZumFormular) {
                        "Zur Einreichung"
                    } else {
                        "Aktionsseite öffnen"
                    },
                )
            }
        }
        IconButton(onClick = onMerken) {
            Icon(
                if (gemerkt) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (gemerkt) {
                    "Von der Merkliste nehmen"
                } else {
                    "Auf die Merkliste setzen"
                },
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (!einkaufsmodus) {
            IconButton(onClick = onBearbeiten) {
                Icon(Icons.Outlined.Edit, contentDescription = "Aktion bearbeiten")
            }
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
private fun LeereMerkliste(onAlleZeigen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Dein Einkaufszettel ist leer",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Setz Aktionen mit dem Lesezeichen auf die Merkliste. " +
                "Im Laden hakst du sie hier der Reihe nach ab.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onAlleZeigen, modifier = Modifier.padding(top = 8.dp)) {
            Text("Alle Aktionen zeigen")
        }
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
