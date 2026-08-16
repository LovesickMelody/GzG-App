package de.gzgtracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Überschrift eines Abschnitts, mit kurzem Akzentstrich darunter.
 *
 * Vorher war eine Abschnittsüberschrift nur größerer Text in derselben Farbe wie
 * alles andere — beim Überfliegen sah ein Formular deshalb wie eine einzige lange
 * Liste aus. Der Strich ist schmal genug, um nicht zu lärmen, und reicht, damit das
 * Auge den Anfang eines Abschnitts findet.
 */
@Composable
fun Abschnittstitel(
    titel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = titel,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box(
            Modifier
                .width(28.dp)
                .height(3.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(2.dp),
                ),
        )
    }
}
