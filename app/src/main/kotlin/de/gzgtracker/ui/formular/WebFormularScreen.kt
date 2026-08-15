package de.gzgtracker.ui.formular

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

/**
 * Die Aktionsseite im Browser der App — mit einem Knopf, der die Daten einträgt.
 *
 * Was die App **nicht** tut: absenden. Das bleibt eine bewusste Handlung.
 *
 * Das Bonfoto lässt sich nicht per Skript ins Datei-Feld schreiben — sonst könnte
 * jede Seite heimlich Dateien hochladen. Tippt man das Feld aber selbst an, bietet
 * die App die Fotos dieser Einreichung zur Auswahl an; ausgesucht wird von Hand,
 * hochgeladen erst dann.
 *
 * Daneben stehen die Werte einzeln zum Kopieren. Das ist der Weg für alles, was
 * der Einfüge-Knopf nicht trifft — und für Formulare hinter einem Login, bei
 * denen man ohnehin im eigenen Browser landet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebFormularScreen(
    onZurueck: () -> Unit,
    viewModel: WebFormularViewModel = hiltViewModel(),
) {
    val zustand by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    // Ladezustand der Seite selbst — getrennt vom Laden der Einreichung.
    var seiteLaedt by remember { mutableStateOf(true) }
    var seitenfehler by remember { mutableStateOf<String?>(null) }

    // Das Datei-Feld der Anbieterseite. Ohne eigene Behandlung passiert beim
    // Antippen von "Datei auswählen" gar nichts — und genau dieses Foto ist der
    // Kern der Einreichung. Angeboten werden zuerst die Bilder, die schon in der
    // App liegen; sie sind der Grund, warum man sie überhaupt aufgenommen hat.
    var dateiwahl by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var belegwahlOffen by remember { mutableStateOf(false) }

    fun beantworteDateiwahl(auswahl: Array<Uri>?) {
        // Immer antworten, auch bei Abbruch: Bleibt die Rückmeldung aus, nimmt
        // die Seite nie wieder eine Datei an.
        dateiwahl?.onReceiveValue(auswahl)
        dateiwahl = null
        belegwahlOffen = false
    }

    val galerie = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> beantworteDateiwahl(uri?.let { arrayOf(it) }) },
    )

    // Zurück heisst hier: eine Seite zurück im Formular, und erst am Anfang raus.
    // Vorher landete man mit einem Tipp wieder in der Liste, mitten im Vorgang.
    BackHandler {
        val ziel = webView
        if (ziel != null && ziel.canGoBack()) ziel.goBack() else onZurueck()
    }

    LaunchedEffect(zustand.meldung) {
        val meldung = zustand.meldung ?: return@LaunchedEffect
        snackbar.showSnackbar(meldung)
        viewModel.meldungGelesen()
    }

    if (belegwahlOffen) {
        AlertDialog(
            onDismissRequest = { beantworteDateiwahl(null) },
            title = { Text("Bild hochladen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (zustand.belege.isEmpty()) {
                        Text("Zu dieser Einreichung ist kein Foto gespeichert.")
                    }
                    zustand.belege.forEach { beleg ->
                        TextButton(
                            onClick = {
                                val adresse = teilbareAdresse(context, beleg.pfad)
                                if (adresse == null) {
                                    viewModel.zeigeMeldung("Das Foto ließ sich nicht öffnen.")
                                    beantworteDateiwahl(null)
                                } else {
                                    beantworteDateiwahl(arrayOf(adresse))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(beleg.art.label)
                        }
                    }
                    TextButton(
                        onClick = {
                            belegwahlOffen = false
                            galerie.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Aus der Galerie")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { beantworteDateiwahl(null) }) { Text("Abbrechen") }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = zustand.aktionstitel.ifBlank { "Einreichen" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onZurueck) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Button(
                    onClick = {
                        val ziel = webView
                        if (ziel == null) {
                            viewModel.zeigeMeldung("Die Seite ist noch nicht geladen.")
                        } else {
                            ziel.evaluateJavascript(viewModel.skript()) { ergebnis ->
                                // Rueckgabe ist "3/6" — als JSON-Text mit
                                // Anfuehrungszeichen drum herum.
                                val zahl = ergebnis?.trim('"') ?: "0"
                                viewModel.zeigeMeldung(
                                    "$zahl Feldern gefüllt. Bitte prüfen, dann absenden.",
                                )
                            }
                        }
                    },
                    enabled = zustand.hatDaten,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Daten einfügen")
                }

                Text(
                    text = "Beim Datei-Feld bietet die App deine Fotos an. Absenden bleibt bei dir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Ein sichtbarer Ausgang. Der Pfeil oben links wird auf einer
                    // fremden Seite leicht übersehen, und dann sitzt man fest.
                    TextButton(onClick = onZurueck) { Text("Fertig") }

                    // Wenn die Seite hier nicht laufen will — manche verlangen
                    // einen Login oder sperren eingebettete Browser aus —, ist der
                    // eigene Browser der Ausweg. Die Werte lassen sich vorher
                    // kopieren.
                    zustand.adresse?.let { ziel ->
                        TextButton(onClick = { uriHandler.openUri(ziel) }) {
                            Icon(
                                Icons.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(" Im Browser öffnen")
                        }
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(zustand.werte.entries.toList()) { (feld, wert) ->
                        SuggestionChip(
                            onClick = {
                                kopiere(context, feld.label, wert)
                                viewModel.zeigeMeldung("${feld.label} kopiert")
                            },
                            icon = {
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = { Text(feld.label) },
                        )
                    }
                }
            }
        },
    ) { innen ->
        val adresse = zustand.adresse
        if (adresse == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innen)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (zustand.laedt) {
                        "Einen Moment …"
                    } else {
                        "Zu dieser Aktion ist keine Adresse hinterlegt. " +
                            "Kopiere die Werte unten und öffne die Seite selbst."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innen),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        // Ohne JavaScript zeigen diese Seiten nichts an — sie sind
                        // durchweg damit gebaut.
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        // Der eingebaute Browser meldet sich mit "wv" im Namen.
                        // Manche Anbieter liefern daraufhin eine leere Seite aus
                        // — das war hier der Fall. Ohne dieses Kuerzel sieht die
                        // Seite ein gewoehnliches Handy.
                        settings.userAgentString =
                            settings.userAgentString.replace("; wv", "")

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, fortschritt: Int) {
                                seiteLaedt = fortschritt < 100
                            }

                            override fun onShowFileChooser(
                                view: WebView?,
                                rueckmeldung: ValueCallback<Array<Uri>>?,
                                angaben: WebChromeClient.FileChooserParams?,
                            ): Boolean {
                                // Eine noch offene Anfrage sauber beenden, sonst
                                // wartet die Seite ewig auf eine Antwort.
                                dateiwahl?.onReceiveValue(null)
                                dateiwahl = rueckmeldung
                                belegwahlOffen = true
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: Bitmap?,
                            ) {
                                seiteLaedt = true
                                seitenfehler = null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                seiteLaedt = false
                            }

                            /**
                             * Adressen, die kein Browser oeffnen kann — `intent://`,
                             * `market://`, `mailto:` — gibt der eingebaute Browser
                             * kommentarlos auf und zeigt eine weisse Flaeche. Die
                             * gehen ans System, alles andere bleibt hier.
                             */
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                anfrage: WebResourceRequest?,
                            ): Boolean {
                                val ziel = anfrage?.url ?: return false
                                if (ziel.scheme == "http" || ziel.scheme == "https") return false
                                return try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, ziel))
                                    true
                                } catch (fehler: ActivityNotFoundException) {
                                    viewModel.zeigeMeldung(
                                        "Für diesen Link ist keine App installiert.",
                                    )
                                    true
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                anfrage: WebResourceRequest?,
                                fehler: WebResourceError?,
                            ) {
                                // Nur die Seite selbst. Ein fehlendes Bild oder ein
                                // geblocktes Zaehlpixel ist kein Grund zur Meldung.
                                if (anfrage?.isForMainFrame != true) return
                                seiteLaedt = false
                                seitenfehler = "Die Seite ließ sich nicht laden."
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                anfrage: WebResourceRequest?,
                                antwort: WebResourceResponse?,
                            ) {
                                if (anfrage?.isForMainFrame != true) return
                                seiteLaedt = false
                                seitenfehler =
                                    "Die Seite antwortet mit Fehler ${antwort?.statusCode ?: 0}."
                            }
                        }

                        loadUrl(adresse)
                        webView = this
                    }
                },
            )

            if (seiteLaedt) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            seitenfehler?.let { text ->
                // Sichtbar stehen lassen statt als Meldung, die weghuscht: Sonst
                // steht man vor einer weissen Flaeche und weiss nicht, warum.
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = {
                        seitenfehler = null
                        webView?.loadUrl(adresse)
                    }) {
                        Text("Erneut versuchen")
                    }
                    TextButton(onClick = { uriHandler.openUri(adresse) }) {
                        Text("Im Browser öffnen")
                    }
                }
            }
        }
    }
}

private fun kopiere(context: Context, bezeichnung: String, wert: String) {
    val dienst = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    dienst.setPrimaryClip(ClipData.newPlainText(bezeichnung, wert))
}

/**
 * Macht ein app-internes Foto für den eingebauten Browser lesbar.
 *
 * Der Weg über den FileProvider ist der einzige: Ein roher Dateipfad wird vom
 * Datei-Feld einer Webseite nicht angenommen, und das ist auch richtig so.
 */
private fun teilbareAdresse(context: Context, pfad: String): Uri? = runCatching {
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(pfad))
}.getOrNull()
