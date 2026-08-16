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
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import de.gzgtracker.data.repository.Erinnerungsart
import de.gzgtracker.ui.components.Teilnahmeliste
import de.gzgtracker.ui.format.deutsch
import de.gzgtracker.ui.theme.GzgTheme
import de.gzgtracker.ui.theme.MoneyTextStyle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

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
    var erinnerungswahlOffen by remember { mutableStateOf(false) }
    var datumOffen by remember { mutableStateOf(false) }

    // Ab Android 13 muss man Meldungen erlauben. Gefragt wird erst hier — beim
    // ersten Mal, dass jemand tatsaechlich erinnert werden will.
    val meldeErlaubnis = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { erlaubt ->
            if (erlaubt) {
                erinnerungswahlOffen = true
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
            erinnerungswahlOffen = true
        }
    }

    LaunchedEffect(zustand.meldung) {
        val meldung = zustand.meldung ?: return@LaunchedEffect
        snackbar.showSnackbar(meldung)
        viewModel.meldungGelesen()
    }

    if (erinnerungswahlOffen) {
        Erinnerungswahl(
            erinnert = zustand.erinnert,
            freischaltung = aktion?.limitReset.takeIf { zustand.hatFreischaltung },
            onWahl = { art ->
                erinnerungswahlOffen = false
                viewModel.erinnere(art)
            },
            onEigenerZeitpunkt = {
                erinnerungswahlOffen = false
                datumOffen = true
            },
            onEntfernen = {
                erinnerungswahlOffen = false
                viewModel.erinnerungEntfernen()
            },
            onSchliessen = { erinnerungswahlOffen = false },
        )
    }

    if (datumOffen) {
        DatumUndZeitwahl(
            onFertig = { zeitpunkt ->
                datumOffen = false
                viewModel.erinnere(Erinnerungsart.EIGEN, zeitpunkt)
            },
            onAbbrechen = { datumOffen = false },
        )
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
            // Vor dem Beginn zuerst: Wer zu frueh kauft, bekommt nichts erstattet.
            aktion.validFrom?.takeIf { aktion.tageBisStart() != null }?.let {
                Angabe("Startet erst am", it.deutsch())
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

            // Das Kontingent gehoert weit nach oben: Es entscheidet, ob sich der
            // Einkauf ueberhaupt lohnt.
            if (aktion.hatKontingent) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (aktion.limitErschoepft) {
                            GzgTheme.status.dringend
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = listOfNotNull(
                            if (aktion.limitErschoepft) "Kontingent zuletzt erschöpft" else null,
                            aktion.kontingentText,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (aktion.limitErschoepft) {
                            GzgTheme.status.dringend
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }

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

/**
 * Die Auswahl, woran erinnert werden soll.
 *
 * Zwei Anlässe sind grundverschieden: Eine Frist läuft irgendwann ab — da reicht
 * ein Hinweis Tage vorher. Ein Kontingent wird zu einer festen Minute neu
 * freigeschaltet, und fünf Minuten später kann alles weg sein. Dazu der eigene
 * Zeitpunkt, für alles, was die App falsch oder gar nicht ausgelesen hat.
 */
@Composable
private fun Erinnerungswahl(
    erinnert: Boolean,
    freischaltung: String?,
    onWahl: (Erinnerungsart) -> Unit,
    onEigenerZeitpunkt: () -> Unit,
    onEntfernen: () -> Unit,
    onSchliessen: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSchliessen,
        title = { Text("Woran erinnern?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (freischaltung != null) {
                    // Zuerst, weil es bei gedeckelten Aktionen das Wichtigere ist.
                    Wahlzeile("5 Minuten vor der Freischaltung ($freischaltung)") {
                        onWahl(Erinnerungsart.FREISCHALTUNG)
                    }
                }
                Wahlzeile("Drei Tage vor der Frist") { onWahl(Erinnerungsart.FRIST) }
                Wahlzeile("Eigener Zeitpunkt …", onKlick = onEigenerZeitpunkt)
                if (erinnert) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Wahlzeile("Erinnerung entfernen", onKlick = onEntfernen)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onSchliessen) { Text("Abbrechen") } },
    )
}

/**
 * Eine Zeile der Auswahl.
 *
 * Linksbündig, weil man eine Liste von Möglichkeiten an der linken Kante
 * entlangliest — zentrierter Text zwingt das Auge bei jeder Zeile neu zum
 * Suchen. Und mindestens 48 dp hoch, damit jede Zeile sicher zu treffen ist.
 */
@Composable
private fun Wahlzeile(beschriftung: String, onKlick: () -> Unit) {
    TextButton(
        onClick = onKlick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(
            text = beschriftung,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
    }
}

/**
 * Datum, dann Uhrzeit — in zwei Schritten.
 *
 * Beides gleichzeitig geht auf einem Telefon nicht unter, ohne dass eines von
 * beiden zu klein wird.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatumUndZeitwahl(
    onFertig: (LocalDateTime) -> Unit,
    onAbbrechen: () -> Unit,
) {
    var datum by remember { mutableStateOf<LocalDate?>(null) }
    val datumszustand = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis(),
    )
    val zeitzustand = rememberTimePickerState(initialHour = 9, initialMinute = 55, is24Hour = true)

    if (datum == null) {
        DatePickerDialog(
            onDismissRequest = onAbbrechen,
            confirmButton = {
                TextButton(
                    onClick = {
                        datumszustand.selectedDateMillis?.let { millis ->
                            datum = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                        }
                    },
                    enabled = datumszustand.selectedDateMillis != null,
                ) {
                    Text("Weiter")
                }
            },
            dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
        ) {
            DatePicker(state = datumszustand)
        }
    } else {
        AlertDialog(
            onDismissRequest = onAbbrechen,
            title = { Text("Uhrzeit") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = zeitzustand)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onFertig(
                            datum!!.atTime(zeitzustand.hour, zeitzustand.minute),
                        )
                    },
                ) {
                    Text("Erinnern")
                }
            },
            dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
        )
    }
}
