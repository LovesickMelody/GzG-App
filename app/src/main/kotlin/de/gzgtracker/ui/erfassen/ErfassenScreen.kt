package de.gzgtracker.ui.erfassen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.gzgtracker.core.Belegart
import de.gzgtracker.core.Money
import de.gzgtracker.core.SubmissionStatus
import de.gzgtracker.ui.components.DatumFeld
import de.gzgtracker.ui.components.label
import de.gzgtracker.ui.konten.FarbPunkt
import de.gzgtracker.ui.theme.MoneyTextStyle
import de.gzgtracker.ui.components.Teilnahmeliste
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErfassenScreen(
    onFertig: () -> Unit,
    onAbbrechen: () -> Unit,
    onScannen: () -> Unit,
    viewModel: ErfassenViewModel = hiltViewModel(),
) {
    val zustand by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    val context = LocalContext.current

    val bildWaehler = rememberLauncherForActivityResult(
        // Photo Picker: kein Speicherzugriff noetig, der Nutzer gibt genau ein Bild frei.
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(viewModel::setzeBeleg) },
    )

    // Ziel der naechsten Aufnahme. Die Kamera-App meldet nur "hat geklappt",
    // nicht wohin sie geschrieben hat — die Adresse muessen wir uns merken.
    var aufnahmeZiel by remember { mutableStateOf<Uri?>(null) }

    val kamera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { erfolg ->
            aufnahmeZiel?.takeIf { erfolg }?.let(viewModel::setzeBeleg)
            aufnahmeZiel = null
        },
    )

    val kameraErlaubnis = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { erlaubt ->
            if (erlaubt) {
                aufnahmeZiel?.let(kamera::launch)
            } else {
                aufnahmeZiel = null
                viewModel.zeigeMeldung("Ohne Kamerazugriff geht nur die Galerie.")
            }
        },
    )

    fun fotografiere() {
        val ziel = kameraZiel(context)
        aufnahmeZiel = ziel
        // Weil die App die Kamera-Berechtigung im Manifest fuehrt (fuer den
        // Barcode-Scan), verlangt Android sie auch fuer den Aufruf der
        // Kamera-App — sonst bricht die Aufnahme wortlos ab.
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            kamera.launch(ziel)
        } else {
            kameraErlaubnis.launch(Manifest.permission.CAMERA)
        }
    }

    fun waehleAusGalerie() {
        bildWaehler.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    LaunchedEffect(zustand.gespeichert) {
        if (zustand.gespeichert) onFertig()
    }

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
                title = { Text(if (zustand.istNeu) "Produkt erfassen" else "Eintrag bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onAbbrechen) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Zurück",
                        )
                    }
                },
                actions = {
                    if (zustand.istNeu) {
                        IconButton(onClick = onScannen) {
                            Icon(
                                Icons.Outlined.QrCodeScanner,
                                contentDescription = "Barcode scannen",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innen)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Abschnitt("Aktion")
            if (zustand.aktionen.isEmpty()) {
                Text(
                    text = "Noch keine Aktionen da. Lade den Feed oder leg eine Aktion an.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(zustand.aktionen, key = { it.id }) { aktion ->
                        FilterChip(
                            selected = zustand.aktionId == aktion.id,
                            onClick = { viewModel.setzeAktion(aktion.id) },
                            label = { Text(aktion.title) },
                        )
                    }
                }
            }

            zustand.gewaehlteAktion?.let { aktion ->
                aktion.maxRefundCents?.let { max ->
                    Text(
                        text = "Erstattung bis ${Money.format(max)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Hier steht man kurz vor dem Einkauf oder direkt danach — der
                // richtige Moment fuer "was muss ich fotografieren?".
                Teilnahmeliste(aktion.requirements)

                aktion.besteAdresse?.let { adresse ->
                    TextButton(onClick = { uriHandler.openUri(adresse) }) {
                        Icon(
                            Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            if (aktion.fuehrtDirektZumFormular) {
                                " Zur Einreichung"
                            } else {
                                " Aktionsseite öffnen"
                            },
                        )
                    }
                }
            }

            Abschnitt("Produkt")
            OutlinedTextField(
                value = zustand.produktname,
                onValueChange = viewModel::setzeProduktname,
                label = { Text("Produktname") },
                isError = zustand.produktname.isBlank(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = zustand.ean,
                onValueChange = viewModel::setzeEan,
                label = { Text("EAN") },
                placeholder = { Text("optional") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = zustand.preis,
                    onValueChange = viewModel::setzePreis,
                    label = { Text("Gezahlter Preis") },
                    suffix = { Text("€") },
                    singleLine = true,
                    isError = zustand.preis.isNotEmpty() && !zustand.preisOk,
                    textStyle = MoneyTextStyle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                DatumFeld(
                    label = "Kaufdatum",
                    wert = zustand.kaufdatum,
                    onWert = viewModel::setzeKaufdatum,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = zustand.haendler,
                onValueChange = viewModel::setzeHaendler,
                label = { Text("Händler") },
                placeholder = { Text("z. B. dm") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Abschnitt("Zielkonto")
            if (zustand.konten.isEmpty()) {
                Text(
                    text = "Noch kein Konto angelegt. Unter „Konten“ legst du eines an.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(zustand.konten.filter { it.isActive }, key = { it.id }) { konto ->
                        FilterChip(
                            selected = zustand.kontoId == konto.id,
                            onClick = { viewModel.setzeKonto(konto.id) },
                            label = { Text(konto.name) },
                            leadingIcon = { FarbPunkt(konto.colorHex, 10) },
                        )
                    }
                }
            }

            zustand.kontoKonflikt?.let { konflikt ->
                KontoWarnung(
                    blockiert = zustand.kontoBlockiert,
                    vorschlagName = konflikt.vorschlag?.name,
                    onVorschlag = viewModel::nimmVorschlag,
                )
            }

            Abschnitt("Belege")
            Text(
                text = "Was du brauchst, steht oben in der Checkliste. Im Zweifel " +
                    "lieber ein Bild zu viel als eins zu wenig.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Belegart.entries.forEach { art ->
                BelegFeld(
                    art = art,
                    pfad = when (art) {
                        Belegart.PRODUKT -> zustand.produktPfad
                        Belegart.BON -> zustand.bonPfad
                        Belegart.ZUSAMMEN -> zustand.zusammenPfad
                    },
                    // Fordert die Aktion diesen Beleg ausdruecklich, wird der Platz
                    // hervorgehoben — die anderen bleiben trotzdem benutzbar, denn
                    // die Checkliste ist nicht immer vollstaendig.
                    verlangt = art.wirdVerlangt(zustand.gewaehlteAktion?.requirements),
                    onFotografieren = {
                        viewModel.waehleBeleg(art)
                        fotografiere()
                    },
                    onAusGalerie = {
                        viewModel.waehleBeleg(art)
                        waehleAusGalerie()
                    },
                    onEntfernen = { viewModel.entferneBeleg(art) },
                )
            }

            Abschnitt("Status")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SubmissionStatus.entries.toList()) { status ->
                    FilterChip(
                        selected = zustand.status == status,
                        onClick = { viewModel.setzeStatus(status) },
                        label = { Text(status.label) },
                    )
                }
            }

            OutlinedTextField(
                value = zustand.notiz,
                onValueChange = viewModel::setzeNotiz,
                label = { Text("Notiz") },
                placeholder = { Text("optional") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::speichern,
                enabled = zustand.speicherbar,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 32.dp),
            ) {
                Text(if (zustand.istNeu) "Einreichung speichern" else "Änderungen speichern")
            }
        }
    }
}

@Composable
private fun Abschnitt(titel: String) {
    Text(
        text = titel,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * Die Warnung zur Kontoregel. Sie nutzt Rot bewusst nicht als Flaeche — Rot ist im
 * Belegstapel der abgelehnte Status. Hier traegt das Warnsymbol die Bedeutung.
 */
@Composable
private fun KontoWarnung(
    blockiert: Boolean,
    vorschlagName: String?,
    onVorschlag: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Dieses Konto ist für diese Aktion schon vergeben",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (blockiert) {
                    "Wähl ein anderes Konto, sonst lässt sich der Eintrag nicht speichern."
                } else {
                    "Anbieter erkennen Mehrfachteilnahmen über dasselbe Konto."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vorschlagName != null) {
                TextButton(onClick = onVorschlag) {
                    Text("Stattdessen $vorschlagName verwenden")
                }
            } else {
                Text(
                    text = "Alle Konten sind für diese Aktion belegt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BelegFeld(
    art: Belegart,
    pfad: String?,
    verlangt: Boolean,
    onFotografieren: () -> Unit,
    onAusGalerie: () -> Unit,
    onEntfernen: () -> Unit,
) {
    Text(
        text = if (verlangt) "${art.label} — von dieser Aktion verlangt" else art.label,
        style = MaterialTheme.typography.labelLarge,
        color = if (verlangt) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    if (pfad != null) {
        Box(Modifier.fillMaxWidth()) {
            AsyncImage(
                model = File(pfad),
                contentDescription = art.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(4.dp),
                    ),
            )
            IconButton(
                onClick = onEntfernen,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "${art.label} entfernen",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onFotografieren) { Text("Neu aufnehmen") }
            TextButton(onClick = onAusGalerie) { Text("Aus Galerie") }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Fotografieren steht links und traegt das Kamerasymbol: Das ist der
            // Regelfall — man kommt vom Einkauf und hat den Bon in der Hand.
            OutlinedButton(
                onClick = onFotografieren,
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                Text("  Fotografieren")
            }
            OutlinedButton(
                onClick = onAusGalerie,
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp),
            ) {
                Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                Text("  Galerie")
            }
        }
    }
}
