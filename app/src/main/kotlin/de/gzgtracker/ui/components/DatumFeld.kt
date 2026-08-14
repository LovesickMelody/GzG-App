package de.gzgtracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.focus.onFocusChanged
import de.gzgtracker.ui.format.deutsch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Datumsfeld mit Kalenderauswahl.
 *
 * Das Textfeld ist bewusst nur lesbar: Ein frei tippbares Datum bringt in der Praxis
 * mehr Tippfehler als Tempo, und die Auswahl ueber den Kalender ist bei Kaufdaten
 * ohnehin schneller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatumFeld(
    label: String,
    wert: LocalDate?,
    onWert: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    platzhalter: String = "Datum wählen",
) {
    var dialogOffen by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = wert?.deutsch() ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text(platzhalter) },
        trailingIcon = {
            Icon(Icons.Outlined.CalendarToday, contentDescription = null)
        },
        modifier = modifier
            .fillMaxWidth()
            // readOnly-Felder bekommen keinen Klick vom Textfeld selbst.
            .clickable { dialogOffen = true }
            .onFocusChanged { if (it.isFocused) dialogOffen = true },
        enabled = false,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            disabledTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
            disabledLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor =
                androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            disabledPlaceholderColor =
                androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )

    if (dialogOffen) {
        val zustand = rememberDatePickerState(
            initialSelectedDateMillis = (wert ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dialogOffen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        zustand.selectedDateMillis?.let { millis ->
                            onWert(
                                Instant.ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate(),
                            )
                        }
                        dialogOffen = false
                    },
                ) {
                    Text("Übernehmen")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogOffen = false }) { Text("Abbrechen") }
            },
        ) {
            DatePicker(state = zustand)
        }
    }
}
