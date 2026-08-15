package de.gzgtracker.ui.aktionen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import de.gzgtracker.core.Money
import de.gzgtracker.core.PromoActionType
import de.gzgtracker.ui.components.Teilnahmeliste
import de.gzgtracker.ui.format.deutsch
import de.gzgtracker.ui.theme.MoneyTextStyle

/**
 * Alles zu einer Aktion auf einer Seite — und unten der Weg weiter.
 *
 * Vorher sprang ein Tipp auf die Aktion sofort ins Erfassungsformular. Das ist
 * die falsche Reihenfolge: Erst will man wissen, worum es geht, was man braucht
 * und bis wann — und *dann* einreichen. Diese Seite ist genau dieser Zwischen-
 * schritt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AktionDetailScreen(
    onZurueck: () -> Unit,
    onEinreichen: (String) -> Unit,
    onBearbeiten: (String) -> Unit,
    viewModel: AktionDetailViewModel = hiltViewModel(),
) {
    val zustand by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val aktion = zustand.aktion
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Ab Android 13 muss man Meldungen erlauben. Gefragt wird erst hier — beim
    // ersten Mal, dass jemand tatsaechlich erinnert werden will.
    val meldeErlaubnis = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { erlaubt ->
            if (erlaubt) {
                viewModel.erinnerungUmschalten()
            } else {
                viewModel.zeigeMeldung("Ohne Meldungen kann die App nicht erinnern.")
            }
        },
    )

    fun erinnerung() {
        val brauchtFrage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        // Beim Abschalten nicht fragen: Wer die Erinnerung loswerden will,
        // braucht dafuer keine Erlaubnis.
        if (brauchtFrage && !zustand.erinnert) {
            meldeErlaubnis.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.erinnerungUmschalten()
        }
    }

    LaunchedEffect(zustand.meldung) {
        val meldung = zustand.meldung ?: return@LaunchedEffect
        snackbar.showSnackbar(meldung)
        viewModel.meldungGelesen()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Aktion") },
                navigationIcon = {
                    IconButton(onClick = onZurueck) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (aktion != null) {
                        IconButton(onClick = { erinnerung() }) {
                            Icon(
                                if (zustand.erinnert) {
                                    Icons.Filled.Notifications
                                } else {
                                    Icons.Outlined.NotificationsNone
                                },
                                contentDescription = if (zustand.erinnert) {
                                    "Erinnerung entfernen"
                                } else {
                                    "An die Frist erinnern"
                                },
                                tint = if (zustand.erinnert) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = viewModel::merkenUmschalten) {
                            Icon(
                                if (zustand.gemerkt) {
                                    Icons.Filled.Bookmark
                                } else {
                                    Icons.Outlined.BookmarkBorder
                                },
                                contentDescription = if (zustand.gemerkt) {
                                    "Von der Merkliste nehmen"
                                } else {
                                    "Auf die Merkliste setzen"
                                },
                                tint = if (zustand.gemerkt) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
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
        if (aktion == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innen)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (zustand.laedt) "Einen Moment …" else "Diese Aktion gibt es nicht mehr.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innen)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Gross statt Briefmarke: Im Laden erkennt man die Packung am Bild.
            aktion.imageUrl?.let { adresse ->
                AsyncImage(
                    model = adresse,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(4.dp),
                        ),
                )
            }

            Text(
                text = aktion.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            aktion.maxRefundCents?.let { max ->
                Text(
                    text = "Erstattung bis ${Money.format(max)}",
                    style = MoneyTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Angabe(
                "Art",
                when (aktion.type) {
                    PromoActionType.GRATIS_TESTEN -> "Gratis testen — voller Kaufpreis zurück"
                    PromoActionType.CASHBACK_TEILBETRAG -> "Cashback — nur ein Teilbetrag"
                    PromoActionType.UNBEKANNT -> "Nicht bekannt"
                },
            )
            aktion.brand?.let { Angabe("Marke", it) }
            aktion.retailers.takeIf { it.isNotEmpty() }?.let {
                Angabe("Händler", it.joinToString(", "))
            }
            aktion.submissionDeadline?.let { Angabe("Einsendeschluss", it.deutsch()) }
            aktion.validTo?.takeIf { it != aktion.submissionDeadline }?.let {
                Angabe("Aktion läuft bis", it.deutsch())
            }
            aktion.eans.takeIf { it.isNotEmpty() }?.let {
                Angabe("EAN", it.joinToString(", "))
            }
            Angabe("Quelle", if (aktion.isManual) "selbst angelegt" else aktion.source)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Teilnahmeliste(aktion.requirements)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Button(
                onClick = { onEinreichen(aktion.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Jetzt einreichen")
            }

            aktion.besteAdresse?.let { adresse ->
                OutlinedButton(
                    onClick = { uriHandler.openUri(adresse) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        if (aktion.fuehrtDirektZumFormular) {
                            "  Aktionsseite öffnen"
                        } else {
                            "  Beim Portal ansehen"
                        },
                    )
                }
            }

            OutlinedButton(
                onClick = { onBearbeiten(aktion.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Angaben bearbeiten")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Angabe(bezeichnung: String, wert: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = bezeichnung,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = wert,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
        )
    }
}
