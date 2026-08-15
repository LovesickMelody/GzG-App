package de.gzgtracker.ui.formular

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Die Aktionsseite im Browser der App — mit einem Knopf, der die Daten einträgt.
 *
 * Was die App **nicht** tut: absenden. Das bleibt eine bewusste Handlung. Und
 * das Bonfoto kann sie nicht anhängen: Browser lassen Skripte kein Datei-Feld
 * füllen, sonst könnte jede Seite heimlich Dateien hochladen.
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
    var webView by remember { mutableStateOf<WebView?>(null) }

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
                    text = "Bonfoto und Absenden bleiben bei dir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

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

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innen),
            factory = { ctx ->
                WebView(ctx).apply {
                    // Ohne JavaScript zeigen diese Seiten nichts an — sie sind
                    // durchweg damit gebaut.
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Links bleiben in dieser Ansicht, statt einen zweiten
                    // Browser aufzumachen und den Zusammenhang zu zerreissen.
                    webViewClient = WebViewClient()
                    loadUrl(adresse)
                    webView = this
                }
            },
        )
    }
}

private fun kopiere(context: Context, bezeichnung: String, wert: String) {
    val dienst = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    dienst.setPrimaryClip(ClipData.newPlainText(bezeichnung, wert))
}
