package de.gzgtracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Die Belegzeile: Bezeichnung links, Betrag rechts, dazwischen eine gepunktete
 * Fuehrungslinie, die genau die Luecke fuellt.
 *
 * Warum eigenes Layout statt `Row` mit `weight`: Ein gewichtetes Kind mit
 * `fill = false` gibt uebrige Breite nicht an das naechste Kind weiter — zwischen
 * Bezeichnung und Punkten klaffte sonst eine Luecke. Hier wird der Betrag zuerst
 * gemessen, die Bezeichnung bekommt den Rest bis auf ein Minimum fuer die Punkte,
 * und die Linie fuellt exakt, was uebrig bleibt.
 *
 * Bezeichnung und Betrag sitzen auf einer gemeinsamen Grundlinie, die Punkte
 * knapp darueber — so wie auf einem gedruckten Bon.
 */
@Composable
fun ReceiptLine(
    label: @Composable () -> Unit,
    amount: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leaderColor: Color = Color.Unspecified,
    minLeaderWidth: Dp = 16.dp,
    gap: Dp = 6.dp,
) {
    Layout(
        modifier = modifier,
        content = {
            Box { label() }
            Box { amount() }
            DottedLeader(color = leaderColor)
        },
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val minLeaderPx = minLeaderWidth.roundToPx()
        val available = constraints.maxWidth

        val amountPlaceable = measurables[1].measure(
            constraints.copy(minWidth = 0, maxWidth = available),
        )

        val labelMaxWidth = max(
            0,
            available - amountPlaceable.width - minLeaderPx - gapPx * 2,
        )
        val labelPlaceable = measurables[0].measure(
            constraints.copy(minWidth = 0, maxWidth = labelMaxWidth),
        )

        val leaderWidth = max(
            minLeaderPx,
            available - labelPlaceable.width - amountPlaceable.width - gapPx * 2,
        )

        // Grundlinien angleichen. Fehlt eine (leerer Text), zaehlt die Unterkante.
        val labelBaseline = labelPlaceable[FirstBaseline]
            .takeIf { it != Int.MIN_VALUE } ?: labelPlaceable.height
        val amountBaseline = amountPlaceable[FirstBaseline]
            .takeIf { it != Int.MIN_VALUE } ?: amountPlaceable.height
        val baseline = max(labelBaseline, amountBaseline)

        val leaderPlaceable = measurables[2].measure(
            Constraints.fixed(width = leaderWidth, height = 1.dp.roundToPx()),
        )

        val height = max(
            baseline - labelBaseline + labelPlaceable.height,
            baseline - amountBaseline + amountPlaceable.height,
        )

        layout(available, height) {
            labelPlaceable.place(0, baseline - labelBaseline)
            amountPlaceable.place(
                available - amountPlaceable.width,
                baseline - amountBaseline,
            )
            leaderPlaceable.place(
                x = labelPlaceable.width + gapPx,
                // Knapp ueber der Grundlinie, auf Hoehe der Zifferunterkante.
                y = baseline - (3.dp.toPx()).roundToInt(),
            )
        }
    }
}

/** Punktlinie im Bon-Raster: 1,5 dp Punkte im Abstand von 4 dp. */
@Composable
fun DottedLeader(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val resolved = if (color == Color.Unspecified) {
        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        color
    }
    Canvas(modifier = modifier) {
        drawLine(
            color = resolved,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(0.5.dp.toPx(), 4.dp.toPx()),
                phase = 0f,
            ),
        )
    }
}
