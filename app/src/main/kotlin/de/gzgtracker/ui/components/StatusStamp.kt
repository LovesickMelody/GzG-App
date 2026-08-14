package de.gzgtracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gzgtracker.core.SubmissionStatus
import de.gzgtracker.ui.theme.GzgTheme

/** Deutscher Anzeigename je Status. */
val SubmissionStatus.label: String
    get() = when (this) {
        SubmissionStatus.GEKAUFT -> "Gekauft"
        SubmissionStatus.EINGEREICHT -> "Eingereicht"
        SubmissionStatus.ERSTATTET -> "Erstattet"
        SubmissionStatus.ABGELEHNT -> "Abgelehnt"
    }

/** Uhr, Haekchen, Kreuz — der Status haengt nie allein an der Farbe. */
val SubmissionStatus.icon: ImageVector
    get() = when (this) {
        SubmissionStatus.GEKAUFT -> Icons.Outlined.ShoppingBag
        SubmissionStatus.EINGEREICHT -> Icons.Outlined.Schedule
        SubmissionStatus.ERSTATTET -> Icons.Outlined.Check
        SubmissionStatus.ABGELEHNT -> Icons.Outlined.Close
    }

/**
 * Der Status als aufgestempeltes Siegel: leicht schraeg, mit Rahmen, Icon und Label.
 *
 * Farbe allein traegt die Bedeutung nie — Icon und Text stehen immer daneben, damit
 * der Status auch bei Farbfehlsichtigkeit und in Graustufen lesbar bleibt.
 *
 * Wird [stampOnChange] auf `true` gesetzt und wechselt der Status auf `ERSTATTET`,
 * faellt der Stempel einmal kurz auf: Scale von 1,15 auf 1,0 mit leichtem Overshoot.
 * Das ist der einzige orchestrierte Moment der App. Hat das System Animationen
 * abgeschaltet, wird nur eingeblendet.
 */
@Composable
fun StatusStamp(
    status: SubmissionStatus,
    modifier: Modifier = Modifier,
    stampOnChange: Boolean = false,
    rotation: Float = -2.5f,
) {
    val palette = GzgTheme.status
    val reducedMotion = GzgTheme.reducedMotion
    val scale = remember { Animatable(1f) }

    LaunchedEffect(status, stampOnChange) {
        if (!stampOnChange || status != SubmissionStatus.ERSTATTET) return@LaunchedEffect
        if (reducedMotion) {
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        scale.snapTo(1.15f)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessHigh,
            ),
        )
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                rotationZ = rotation
                scaleX = scale.value
                scaleY = scale.value
            }
            .background(palette.background(status), RoundedCornerShape(3.dp))
            .border(1.5.dp, palette.content(status).copy(alpha = 0.45f), RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .clearAndSetSemantics { contentDescription = "Status: ${status.label}" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = status.icon,
            contentDescription = null,
            tint = palette.content(status),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = status.label,
            color = palette.content(status),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
        )
    }
}
