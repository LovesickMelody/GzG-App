package de.gzgtracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import de.gzgtracker.ui.theme.MoneyTextStyle

/**
 * "Was brauche ich, um mitzumachen?" — die Checkliste einer Aktion.
 *
 * Die Schlüssel kommen aus dem Feed; der Scraper liest sie aus den
 * Teilnahmebedingungen des Portals. Hier werden sie zu Sätzen, die man beim
 * Einkauf im Kopf haben will.
 *
 * Eine leere Liste heißt **nicht** "nichts nötig", sondern "wir wissen es
 * nicht". Genau das steht dann auch da: Wer vor dem Regal steht, muss den
 * Unterschied erkennen können, sonst fotografiert er nur den Bon und die
 * Erstattung fällt aus.
 */

/** Reihenfolge und Wortlaut der Checkliste. Unbekannte Schlüssel fallen weg. */
private val TEXTE: Map<String, String> = mapOf(
    "produktfoto" to "Foto vom Produkt",
    "bonfoto" to "Foto vom Kassenbon",
    "zusammen_fotografieren" to "Produkt und Bon zusammen auf ein Bild",
    "strichcode" to "Strichcode ausschneiden und aufheben",
    "verpackung_aufbewahren" to "Verpackung aufbewahren",
    "app" to "App des Anbieters nötig",
    "registrierung" to "Konto beim Anbieter anlegen",
    "iban" to "IBAN bereithalten",
)

/** Kurzfassung für die Zeile in einer Liste. */
private val KURZ: Map<String, String> = mapOf(
    "produktfoto" to "Produktfoto",
    "bonfoto" to "Bonfoto",
    "zusammen_fotografieren" to "zusammen aufs Bild",
    "strichcode" to "Strichcode",
    "verpackung_aufbewahren" to "Verpackung",
    "app" to "App",
    "registrierung" to "Konto",
    "iban" to "IBAN",
)

fun List<String>.bekannteAnforderungen(): List<String> = filter(TEXTE::containsKey)

/**
 * Die vollständige, nummerierte Liste — für den Bildschirm, auf dem man handelt.
 */
@Composable
fun Teilnahmeliste(
    anforderungen: List<String>,
    modifier: Modifier = Modifier,
) {
    val bekannte = anforderungen.bekannteAnforderungen()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Was brauche ich?",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (bekannte.isEmpty()) {
            Text(
                text = "Steht nicht im Feed. Die Bedingungen stehen auf der Aktionsseite.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        bekannte.forEachIndexed { index, schluessel ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Ziffern in der Mono-Schrift: gleiche Breite, also stehen die
                // Texte daneben auf einer Kante.
                Text(
                    text = "${index + 1}.",
                    style = MoneyTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(20.dp),
                )
                Text(
                    text = TEXTE.getValue(schluessel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Einzeilige Kurzfassung für dichte Listen.
 *
 * Gibt nichts aus, wenn nichts bekannt ist — in einer Liste wäre "unbekannt"
 * nur Rauschen; auf dem Erfassen-Bildschirm steht der Hinweis ausführlich.
 */
@Composable
fun TeilnahmeKurz(
    anforderungen: List<String>,
    modifier: Modifier = Modifier,
) {
    val bekannte = anforderungen.bekannteAnforderungen()
    if (bekannte.isEmpty()) return

    val zusammen = bekannte.joinToString(" · ") { KURZ.getValue(it) }

    Text(
        text = "Braucht: $zusammen",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(top = 1.dp)
            // Für die Sprachausgabe der ganze Satz statt der Stichworte.
            .clearAndSetSemantics {
                contentDescription = "Zum Mitmachen nötig: " +
                    bekannte.joinToString(", ") { TEXTE.getValue(it) }
            },
    )
}
