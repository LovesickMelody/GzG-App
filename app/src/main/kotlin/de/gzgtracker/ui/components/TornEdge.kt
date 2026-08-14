package de.gzgtracker.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * Form fuer die Summenkarte: oben eine gerade Kante, unten eine gezackte —
 * als waere der Bon dort abgerissen worden.
 *
 * Die Zahnbreite wird so nachjustiert, dass ganze Zaehne exakt in die Kartenbreite
 * passen. Sonst haengt am rechten Rand ein angeschnittener Zahn, und die Kante
 * wirkt wie ein Rendering-Fehler statt wie Absicht.
 */
class TornEdgeShape(
    private val toothWidth: Dp = 12.dp,
    private val toothHeight: Dp = 7.dp,
    private val cornerRadius: Dp = 4.dp,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        with(density) {
            val toothHeightPx = toothHeight.toPx()
            val radiusPx = cornerRadius.toPx().coerceAtMost(size.height / 2f)

            val teeth = ceil(size.width / toothWidth.toPx()).coerceAtLeast(1f)
            val actualToothWidth = size.width / teeth
            val baseY = size.height - toothHeightPx

            val path = Path().apply {
                // Oben gerade, mit leicht gerundeten oberen Ecken.
                moveTo(0f, radiusPx)
                quadraticBezierTo(0f, 0f, radiusPx, 0f)
                lineTo(size.width - radiusPx, 0f)
                quadraticBezierTo(size.width, 0f, size.width, radiusPx)
                lineTo(size.width, baseY)

                // Unten die Abrisskante, von rechts nach links.
                var index = teeth.toInt()
                while (index > 0) {
                    val right = actualToothWidth * index
                    val left = actualToothWidth * (index - 1)
                    lineTo((left + right) / 2f, size.height)
                    lineTo(left, baseY)
                    index--
                }

                close()
            }
            return Outline.Generic(path)
        }
    }
}
