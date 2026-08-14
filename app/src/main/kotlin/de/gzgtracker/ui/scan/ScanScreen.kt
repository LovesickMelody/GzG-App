package de.gzgtracker.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.concurrent.Executors

@androidx.compose.runtime.Composable
private fun kameraErlaubt(): Boolean {
    val context = LocalContext.current
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onAbbrechen: () -> Unit,
    onTreffer: (actionId: String?, ean: String) -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val zustand by viewModel.uiState.collectAsStateWithLifecycle()
    var habenErlaubnis by remember { mutableStateOf(false) }
    val bereitsErlaubt = kameraErlaubt()

    LaunchedEffect(bereitsErlaubt) { habenErlaubnis = bereitsErlaubt }

    val erlaubnisFrage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { erlaubt -> habenErlaubnis = erlaubt },
    )

    LaunchedEffect(Unit) {
        if (!bereitsErlaubt) erlaubnisFrage.launch(Manifest.permission.CAMERA)
    }

    // Sobald der Code zugeordnet ist, geht es weiter ins Formular.
    LaunchedEffect(zustand.ergebnis) {
        val ergebnis = zustand.ergebnis ?: return@LaunchedEffect
        if (!ergebnis.mehrdeutig) {
            onTreffer(ergebnis.eindeutigeAktion?.id, ergebnis.ean)
        }
    }

    Scaffold(
        // Das aeussere Scaffold in GzgApp rechnet die System-Insets bereits an.
        // Ohne diese Zeile zieht dieses Scaffold sie ein zweites Mal ab, und die
        // Inhalte rutschen um Status- und Navigationsleiste zu weit nach innen.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Produkt scannen") },
                navigationIcon = {
                    IconButton(onClick = onAbbrechen) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innen),
        ) {
            when {
                !habenErlaubnis -> KeineKamera(
                    onNochmal = { erlaubnisFrage.launch(Manifest.permission.CAMERA) },
                    onOhneScan = { onTreffer(null, "") },
                )

                zustand.ergebnis?.mehrdeutig == true -> AktionWaehlen(
                    zustand = zustand,
                    onWahl = { aktionId ->
                        onTreffer(aktionId, zustand.ergebnis?.ean.orEmpty())
                    },
                )

                else -> Kamerabild(onCode = viewModel::codeErkannt)
            }
        }
    }
}

@Composable
private fun Kamerabild(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { BarcodeAnalyzer(onTreffer = onCode) }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.schliessen()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val vorschau = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val anbieterZukunft = ProcessCameraProvider.getInstance(ctx)

                anbieterZukunft.addListener({
                    val anbieter = anbieterZukunft.get()

                    val vorschauFall = Preview.Builder().build().apply {
                        setSurfaceProvider(vorschau.surfaceProvider)
                    }

                    val analyse = ImageAnalysis.Builder()
                        // Nur das neueste Bild auswerten — sonst laeuft die Erkennung
                        // der Kamera hinterher und meldet veraltete Codes.
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .apply { setAnalyzer(executor, analyzer) }

                    runCatching {
                        anbieter.unbindAll()
                        anbieter.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            vorschauFall,
                            analyse,
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))

                vorschau
            },
        )

        // Zielrahmen: zeigt, wohin der Barcode gehoert.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.82f)
                .height(150.dp)
                .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp)),
        )

        Text(
            text = "Barcode in den Rahmen halten",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AktionWaehlen(zustand: ScanUiState, onWahl: (String) -> Unit) {
    val ergebnis = zustand.ergebnis ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Mehrere Aktionen zu diesem Barcode",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Zu ${ergebnis.ean} passen ${ergebnis.treffer.size} Aktionen. " +
                "Welche meinst du?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ergebnis.treffer.forEach { aktion ->
            Button(
                onClick = { onWahl(aktion.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(aktion.title)
            }
        }
    }
}

@Composable
private fun KeineKamera(onNochmal: () -> Unit, onOhneScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.QrCodeScanner,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = "Kamera nicht freigegeben",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Ohne Kamera kannst du den Barcode nicht scannen. Du kannst das " +
                "Produkt aber von Hand eintragen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onNochmal) { Text("Kamera freigeben") }
        TextButton(onClick = onOhneScan) { Text("Von Hand eintragen") }
    }
}
