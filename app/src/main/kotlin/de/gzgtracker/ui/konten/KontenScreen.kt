package de.gzgtracker.ui.konten

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.gzgtracker.core.Account
import de.gzgtracker.core.Money
import de.gzgtracker.ui.components.ReceiptLine
import de.gzgtracker.ui.theme.MoneySmallTextStyle
import de.gzgtracker.ui.theme.MoneyTextStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KontenScreen(viewModel: KontenViewModel = hiltViewModel()) {
    val zustand by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var bearbeite by remember { mutableStateOf<Account?>(null) }
    var neuOffen by remember { mutableStateOf(false) }

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
                title = { Text("Konten") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { neuOffen = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Konto anlegen") },
            )
        },
    ) { innen ->
        if (zustand.istLeer) {
            LeereKonten(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innen),
                onAnlegen = { neuOffen = true },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innen),
            ) {
                items(zustand.zeilen, key = { it.account.id }) { zeile ->
                    KontoZeileAnsicht(
                        zeile = zeile,
                        onBearbeiten = { bearbeite = zeile.account },
                        onAktiv = { aktiv -> viewModel.setzeAktiv(zeile.account.id, aktiv) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (neuOffen) {
        KontoDialog(
            titel = "Konto anlegen",
            konto = null,
            onSpeichern = { entwurf ->
                viewModel.anlegen(entwurf)
                neuOffen = false
            },
            onAbbrechen = { neuOffen = false },
        )
    }

    bearbeite?.let { konto ->
        KontoDialog(
            titel = "Konto bearbeiten",
            konto = konto,
            onSpeichern = { geaendert ->
                viewModel.aktualisieren(geaendert.copy(id = konto.id))
                bearbeite = null
            },
            onAbbrechen = { bearbeite = null },
            onLoeschen = {
                viewModel.loeschen(konto.id)
                bearbeite = null
            },
            loeschbar = zustand.zeilen
                .firstOrNull { it.account.id == konto.id }
                ?.anzahlEinreichungen == 0,
        )
    }
}

@Composable
private fun KontoZeileAnsicht(
    zeile: KontoZeile,
    onBearbeiten: () -> Unit,
    onAktiv: (Boolean) -> Unit,
) {
    val konto = zeile.account
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBearbeiten)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FarbPunkt(konto.colorHex, 14)
            Column(Modifier.weight(1f)) {
                Text(
                    text = konto.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = listOfNotNull(
                        konto.endziffern?.let { "IBAN …$it" },
                        konto.vollerName,
                        "${zeile.anzahlEinreichungen} Einreichungen",
                        if (!konto.isActive) "deaktiviert" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = konto.isActive,
                onCheckedChange = onAktiv,
            )
        }

        ReceiptLine(
            label = {
                Text(
                    text = "Steht noch aus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            amount = {
                Text(
                    text = Money.format(zeile.totals.ausstehendCents),
                    style = MoneyTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
        )
        ReceiptLine(
            label = {
                Text(
                    text = "Bereits erstattet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            amount = {
                Text(
                    text = Money.format(zeile.totals.erstattetCents),
                    style = MoneySmallTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
private fun KontoDialog(
    titel: String,
    konto: Account?,
    onSpeichern: (Account) -> Unit,
    onAbbrechen: () -> Unit,
    onLoeschen: (() -> Unit)? = null,
    loeschbar: Boolean = false,
) {
    var name by remember { mutableStateOf(konto?.name.orEmpty()) }
    var iban by remember { mutableStateOf(konto?.iban.orEmpty()) }
    var farbe by remember { mutableStateOf(konto?.colorHex ?: KONTO_FARBEN.first()) }
    var vorname by remember { mutableStateOf(konto?.vorname.orEmpty()) }
    var nachname by remember { mutableStateOf(konto?.nachname.orEmpty()) }
    var strasse by remember { mutableStateOf(konto?.strasse.orEmpty()) }
    var hausnummer by remember { mutableStateOf(konto?.hausnummer.orEmpty()) }
    var plz by remember { mutableStateOf(konto?.plz.orEmpty()) }
    var ort by remember { mutableStateOf(konto?.ort.orEmpty()) }
    var telefon by remember { mutableStateOf(konto?.telefon.orEmpty()) }
    var email by remember { mutableStateOf(konto?.email.orEmpty()) }
    var anrede by remember { mutableStateOf(konto?.anrede.orEmpty()) }
    var geburtstag by remember {
        mutableStateOf(konto?.geburtsdatum?.format(GEBURTSTAGSFORMAT).orEmpty())
    }

    // Leer ist in Ordnung — freiwillig heisst freiwillig. Nur halb Getipptes
    // wird nicht gespeichert, sonst stuende im Formular spaeter Unsinn.
    val geburtstagWert = leseGeburtstag(geburtstag)
    val geburtstagOk = geburtstag.isBlank() || geburtstagWert != null

    val nameOk = name.isNotBlank()
    // Eine deutsche IBAN hat 22 Zeichen, international bis 34. Geprueft wird
    // nur die Laenge — eine echte Pruefsummenrechnung waere hier Ballast, und
    // ein Tippfehler faellt beim Einreichen ohnehin auf.
    val ibanRoh = iban.filter { !it.isWhitespace() }
    val ibanOk = ibanRoh.isEmpty() || ibanRoh.length in 15..34

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(titel) },
        text = {
            // Mit dem Profil passt der Inhalt nicht mehr auf einen Bildschirm.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("z. B. DKB Giro") },
                    singleLine = true,
                    isError = !nameOk,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = iban,
                    onValueChange = { iban = it.uppercase() },
                    label = { Text("IBAN") },
                    placeholder = { Text("DE00 0000 0000 0000 0000 00") },
                    singleLine = true,
                    isError = !ibanOk,
                    supportingText = {
                        Text("Bleibt auf dem Gerät. Wird beim Einreichen ins Formular gesetzt.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Angaben für die Einreichung",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Alles freiwillig. Was hier steht, trägt die App beim " +
                        "Einreichen ins Formular des Anbieters ein.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vorname,
                        onValueChange = { vorname = it },
                        label = { Text("Vorname") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = nachname,
                        onValueChange = { nachname = it },
                        label = { Text("Nachname") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ANREDEN.forEach { wahl ->
                        FilterChip(
                            // Nochmal antippen hebt die Wahl wieder auf: Wer
                            // sich vertippt hat, soll nicht festsitzen.
                            selected = anrede == wahl,
                            onClick = { anrede = if (anrede == wahl) "" else wahl },
                            label = { Text(wahl) },
                        )
                    }
                }
                OutlinedTextField(
                    value = geburtstag,
                    onValueChange = { geburtstag = it },
                    label = { Text("Geburtsdatum") },
                    placeholder = { Text("TT.MM.JJJJ") },
                    singleLine = true,
                    isError = !geburtstagOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = strasse,
                        onValueChange = { strasse = it },
                        label = { Text("Straße") },
                        singleLine = true,
                        modifier = Modifier.weight(0.65f),
                    )
                    OutlinedTextField(
                        value = hausnummer,
                        onValueChange = { hausnummer = it },
                        label = { Text("Nr.") },
                        singleLine = true,
                        modifier = Modifier.weight(0.35f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = plz,
                        onValueChange = { plz = it.filter(Char::isDigit).take(5) },
                        label = { Text("PLZ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.35f),
                    )
                    OutlinedTextField(
                        value = ort,
                        onValueChange = { ort = it },
                        label = { Text("Ort") },
                        singleLine = true,
                        modifier = Modifier.weight(0.65f),
                    )
                }
                OutlinedTextField(
                    value = telefon,
                    onValueChange = { telefon = it },
                    label = { Text("Telefon") },
                    placeholder = { Text("für Aktionen mit SMS-Code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-Mail") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Farbe",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KONTO_FARBEN.forEach { hex ->
                        FarbWahl(
                            hex = hex,
                            gewaehlt = hex.equals(farbe, ignoreCase = true),
                            onWahl = { farbe = hex },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSpeichern(
                        (konto ?: Account(id = 0, name = "")).copy(
                            name = name.trim(),
                            iban = ibanRoh.ifBlank { null },
                            colorHex = farbe,
                            vorname = vorname.trim().ifBlank { null },
                            nachname = nachname.trim().ifBlank { null },
                            strasse = strasse.trim().ifBlank { null },
                            hausnummer = hausnummer.trim().ifBlank { null },
                            plz = plz.trim().ifBlank { null },
                            ort = ort.trim().ifBlank { null },
                            telefon = telefon.trim().ifBlank { null },
                            email = email.trim().ifBlank { null },
                            anrede = anrede.ifBlank { null },
                            geburtsdatum = geburtstagWert,
                        ),
                    )
                },
                enabled = nameOk && ibanOk && geburtstagOk,
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            Row {
                if (onLoeschen != null && loeschbar) {
                    TextButton(onClick = onLoeschen) { Text("Löschen") }
                }
                TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
            }
        },
    )
}

@Composable
private fun FarbWahl(hex: String, gewaehlt: Boolean, onWahl: () -> Unit) {
    val farbe = farbeAus(hex)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(farbe)
            .border(
                width = if (gewaehlt) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
            )
            .clickable(onClick = onWahl),
    )
}

@Composable
fun FarbPunkt(hex: String, groesse: Int) {
    Box(
        modifier = Modifier
            .size(groesse.dp)
            .clip(CircleShape)
            .background(farbeAus(hex)),
    )
}

private fun farbeAus(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

@Composable
private fun LeereKonten(modifier: Modifier = Modifier, onAnlegen: () -> Unit) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.CreditCard,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = "Noch keine Konten",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Leg deine Konten an, dann verteilt die App die Erstattungen " +
                "automatisch darauf.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onAnlegen) { Text("Erstes Konto anlegen") }
    }
}

/** Die Anreden, die deutsche Formulare anbieten. */
private val ANREDEN = listOf("Herr", "Frau", "Divers")

private val GEBURTSTAGSFORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/**
 * Liest ein Geburtsdatum in deutscher Schreibweise.
 *
 * Gibt `null` zurueck, solange die Eingabe unvollstaendig oder unmoeglich ist —
 * der 31.02. kommt beim Tippen zwangslaeufig vor.
 */
private fun leseGeburtstag(eingabe: String): LocalDate? = runCatching {
    LocalDate.parse(eingabe.trim(), GEBURTSTAGSFORMAT)
}.getOrNull()
