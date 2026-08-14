package de.gzgtracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.gzgtracker.core.Money
import de.gzgtracker.core.Totals
import de.gzgtracker.ui.theme.GzgTheme
import de.gzgtracker.ui.theme.MoneyLargeTextStyle
import de.gzgtracker.ui.theme.MoneySmallTextStyle

/**
 * Die Kopfkarte: was noch aussteht, was schon da ist, wie viele Einreichungen.
 * Unten die gezackte Abrisskante — hier endet der Bon.
 */
@Composable
fun SummaryCard(
    totals: Totals,
    modifier: Modifier = Modifier,
) {
    val status = GzgTheme.status

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(TornEdgeShape())
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            // Unten mehr Luft, damit der Text nicht in die Zacken laeuft.
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Offen",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = Money.format(totals.ausstehendCents),
                style = MoneyLargeTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StatusDot(
                farbe = status.submitted,
                text = "${totals.anzahlAusstehend} offen",
            )
        }

        DottedRule()

        SummaryLine(
            label = "Erstattet",
            betragCents = totals.erstattetCents,
            farbe = status.refunded,
            zusatz = "${totals.anzahlErstattet}×",
        )

        if (totals.abgelehntCents > 0) {
            SummaryLine(
                label = "Abgelehnt",
                betragCents = totals.abgelehntCents,
                farbe = status.rejected,
                zusatz = null,
            )
        }

        DottedRule()

        ReceiptLine(
            label = {
                Text(
                    text = "Einreichungen gesamt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            amount = {
                Text(
                    text = totals.anzahl.toString(),
                    style = MoneySmallTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
private fun SummaryLine(
    label: String,
    betragCents: Int,
    farbe: Color,
    zusatz: String?,
) {
    ReceiptLine(
        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (zusatz != null) {
                    Text(
                        text = zusatz,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        amount = {
            Text(
                text = Money.format(betragCents),
                style = MoneySmallTextStyle,
                color = farbe,
            )
        },
    )
}

/** Punktlinie ueber die volle Breite — die Trennlinie des Bons. */
@Composable
fun DottedRule(modifier: Modifier = Modifier) {
    DottedLeader(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(1.dp),
    )
}

@Composable
private fun StatusDot(farbe: Color, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(8.dp)) {
            drawCircle(farbe)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
