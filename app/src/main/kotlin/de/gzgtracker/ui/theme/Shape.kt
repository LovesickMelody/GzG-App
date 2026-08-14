package de.gzgtracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Papier hat keine weichen Ecken. Die Radien bleiben klein, damit Karten und Zeilen
 * wie geschnittene Belege wirken und nicht wie Material-Kacheln.
 */
val GzgShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)
