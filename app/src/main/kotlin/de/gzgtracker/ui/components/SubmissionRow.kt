package de.gzgtracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.gzgtracker.core.Account
import de.gzgtracker.core.Money
import de.gzgtracker.core.PromoAction
import de.gzgtracker.core.Submission
import de.gzgtracker.ui.format.deutsch
import de.gzgtracker.ui.theme.GzgTheme
import de.gzgtracker.ui.theme.MoneyTextStyle

/**
 * Eine Zeile im Belegstapel: Produkt links, Betrag rechts in Mono, dazwischen die
 * gepunktete Fuehrungslinie. Der Status sitzt als Stempel darunter — und als
 * schmaler Streifen am linken Rand.
 *
 * Der Streifen ist der Grund, warum die Statusfarben ueberhaupt etwas nuetzen: Beim
 * Ueberfliegen einer langen Liste liest niemand jeden Stempel. Eine durchgehende
 * Farbkante dagegen sieht man, ohne hinzusehen.
 *
 * Das Konto steht als Text da, nicht als Farbe — sonst konkurrierten zwei
 * Farbsysteme in derselben Zeile um Aufmerksamkeit.
 */
@Composable
fun SubmissionRow(
    submission: Submission,
    action: PromoAction?,
    account: Account?,
    betragCents: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stampOnChange: Boolean = false,
) {
    val streifen = GzgTheme.status.background(submission.status)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // Mindestens 48 dp Trefferflaeche.
            .heightIn(min = 64.dp)
            // Der Streifen wird gezeichnet statt gelegt: In einer langen Liste
            // steht die Zeilenhoehe erst beim Zeichnen fest, und ein Element mit
            // "volle Hoehe" haette darin keine bekommen.
            .drawBehind {
                val breite = 4.dp.toPx()
                val rand = 6.dp.toPx()
                drawRoundRect(
                    color = streifen,
                    topLeft = Offset(12.dp.toPx(), rand),
                    size = Size(breite, size.height - 2 * rand),
                    cornerRadius = CornerRadius(breite / 2),
                )
            }
            .padding(start = 28.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ReceiptLine(
            label = {
                Text(
                    text = submission.productName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            amount = {
                Text(
                    text = Money.format(betragCents),
                    style = MoneyTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = untertitel(submission, action, account),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusStamp(
                status = submission.status,
                stampOnChange = stampOnChange,
            )
        }
    }
}

private fun untertitel(
    submission: Submission,
    action: PromoAction?,
    account: Account?,
): String = listOfNotNull(
    submission.purchaseDate.deutsch(),
    action?.brand ?: submission.retailer,
    account?.name,
).joinToString(" · ")
