package de.gzgtracker.ui.aktionen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
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
import de.gzgtracker.ui.format.relativeKurz
import de.gzgtracker.ui.theme.GzgTheme
import de.gzgtracker.ui.theme.MoneyTextStyle
import de.gzgtracker.ui.uebersicht.SucheFeld

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AktionenScreen(
    onAktionOeffnen: (String) -> Unit,
    onAktionAnlegen: () -> Unit,
    viewModel: AktionenViewModel = hiltViewModel(),
) {
    val zustand by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Die Suchleiste nahm dauerhaft ein Sechstel des Bildschirms ein, obwohl
    // man selten sucht. Jetzt eine Lupe — und Platz fuer Aktionen.
    var sucheOffen by rememberSaveable { mutableStateOf(false) }

    // Entscheidend ist nicht der Knopf, sondern der Suchbegriff: Wer gesucht,
    // eine Aktion geoeffnet und zurueckgegangen ist, kam mit zugeklappter Leiste
    // zurueck — der Begriff filterte aber weiter. Am Geraet sah das aus, als
    // wuerden die uebrigen Aktionen nicht mehr geladen. Solange etwas im Feld
    // steht, bleibt es sichtbar.
    val sucheSichtbar = sucheOffen || zustand.suche.isNotBlank()
    var sortiermenue by remember { mutableStateOf(false) }

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
                    IconButton(
                        onClick = {
                            if (sucheSichtbar) {
                                viewModel.setzeSuche("")
                                sucheOffen = false
                            } else {
                                sucheOffen = true
                            }
                        },
                    ) {
                        Icon(
                            if (sucheSichtbar) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                            contentDescription = if (sucheSichtbar) {
                                "Suche schließen"
                            } else {
                                "Suchen"
                            },
                            // Ein aktiver Suchbegriff traegt den Akzent: Dann ist
                            // die Liste gefiltert, und das muss man sehen.
                            tint = if (zustand.suche.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }

                    Box {
                        IconButton(onClick = { sortiermenue = true }) {
                            Icon(
                                Icons.Outlined.FilterList,
                                contentDescription = "Sortieren und filtern",
                            )
                        }
                        DropdownMenu(
                            expanded = sortiermenue,
                            onDismissRequest = { sortiermenue = false },
                        ) {
                            Sortierung.entries.forEach { wahl ->
                                DropdownMenuItem(
                                    text = { Text(wahl.label) },
                                    onClick = {
                                        viewModel.setzeSortierung(wahl)
                                        sortiermenue = false
                                    },
                                    leadingIcon = {
                                        if (wahl == zustand.sortierung) {
                                            Icon(Icons.Outlined.Check, contentDescription = null)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    // Eine Aktion selbst anzulegen ist der Ausnahmefall — der
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
                if (sucheSichtbar) {
                    SucheFeld(
                        wert = zustand.suche,
                        onWert = viewModel::setzeSuche,
                        onSchliessen = {
                            viewModel.setzeSuche("")
                            sucheOffen = false
                        },
                    )
                }

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
                }

                // Eigene Zeile statt neben den Chips: Dort blieb vom Text nur
                // "Stand ge..." uebrig, egal wie kurz er gefasst war.
                if (!zustand.nurMerkliste) {
                    zustand.letzterSync?.let { sync ->
                        Text(
                            text = "Stand ${sync.relativeKurz()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                            textAlign = TextAlign.End,
                        )
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
                                erinnert = aktion.id in zustand.erinnert,
                                imWagen = zustand.gemerkt[aktion.id] == true,
                                einkaufsmodus = zustand.nurMerkliste,
                                onOeffnen = { onAktionOeffnen(aktion.id) },
                                onMerken = { viewModel.merkenUmschalten(aktion.id) },
                                onImWagen = { viewModel.setzeImWagen(aktion.id, it) },
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
    erinnert: Boolean,
    imWagen: Boolean,
    einkaufsmodus: Boolean,
    onOeffnen: () -> Unit,
    onMerken: () -> Unit,
    onImWagen: (Boolean) -> Unit,
) {
    val tage = aktion.tageBisFrist()
    val bisStart = aktion.tageBisStart()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOeffnen)
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
            // Ganz zeigen statt zuschneiden: Bei "Crop" fehlte regelmaessig die
            // halbe Packung, und im Laden erkennt man sie dann nicht wieder.
            AsyncImage(
                model = adresse,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(width = 104.dp, height = 88.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(4.dp),
                    )
                    .padding(2.dp),
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

            // Die Frist ist die kritischste Angabe der ganzen Liste. Vorher sah
            // "Einsendeschluss morgen" genauso aus wie "in acht Tagen".
            // Nur was laeuft, kann dringend sein: Eine Aktion, die erst in
            // zwei Tagen startet, hat keine ablaufende Frist.
            val dringend = bisStart == null && tage != null && tage <= 2
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (erinnert) {
                    // Kleiner Hinweis statt eines zweiten Knopfes: Gestellt wird
                    // die Erinnerung auf der Aktionsseite, wo Platz dafuer ist.
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = "Erinnerung gestellt",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = fristText(aktion, tage, bisStart),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (dringend) FontWeight.SemiBold else null,
                    color = if (dringend) {
                        GzgTheme.status.dringend
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            // Ein gedeckeltes Kontingent entscheidet darueber, ob sich der Kauf
            // ueberhaupt lohnt — das gehoert in die Liste, nicht nur auf die
            // Aktionsseite.
            if (aktion.hatKontingent) {
                Text(
                    text = listOfNotNull(
                        if (aktion.limitErschoepft) "Zuletzt erschöpft" else null,
                        aktion.kontingentKurz,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (aktion.limitErschoepft) {
                        GzgTheme.status.dringend
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TeilnahmeKurz(aktion.requirements)
        }

        // Nur noch das Lesezeichen. Oeffnen, Bearbeiten und Einreichen stehen
        // auf der Aktionsseite, wo Platz fuer Beschriftungen ist — in der Liste
        // haben sie mehr Raum gefressen als Bild und Text zusammen.
        IconButton(onClick = onMerken) {
            Icon(
                if (gemerkt) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (gemerkt) {
                    "Von der Merkliste nehmen"
                } else {
                    "Auf die Merkliste setzen"
                },
                // Gesetzt heisst ausgewaehlt, und Ausgewaehltes traegt den Akzent.
                tint = if (gemerkt) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun fristText(aktion: PromoAction, tage: Long?, bisStart: Long? = null): String {
    // Eine noch nicht gestartete Aktion zeigt ihren Beginn, nicht ihre Frist.
    // Wer jetzt kauft, hat einen Bon von heute — der liegt vor dem Zeitraum,
    // und die Erstattung faellt aus. Die Frist steht in der Detailansicht.
    val beginn = aktion.validFrom
    if (bisStart != null && beginn != null) {
        return when {
            bisStart == 1L -> "Ab morgen"
            bisStart <= 14 -> "Ab ${beginn.deutsch()} (in $bisStart Tagen)"
            else -> "Ab ${beginn.deutsch()}"
        }
    }

    val frist = aktion.submissionDeadline ?: aktion.validTo ?: return "Ohne Frist"
    // Kurz halten: Neben Bild, Glocke und Lesezeichen bleiben keine 200 dp fuer
    // Text, und "Einsendeschluss 31.10.2026" brach mitten im Wort um. Das lange
    // Wort steht auf der Aktionsseite, wo Platz dafuer ist.
    val bezeichnung = if (aktion.submissionDeadline != null) "Frist" else "Läuft bis"
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
