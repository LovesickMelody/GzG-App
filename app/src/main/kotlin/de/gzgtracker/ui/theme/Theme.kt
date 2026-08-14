package de.gzgtracker.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import de.gzgtracker.core.SubmissionStatus

/**
 * Statusfarben liegen bewusst neben dem Material-Farbschema. Material haette sie in
 * primary/secondary/tertiary gepresst — genau das soll hier nicht passieren, weil
 * Farbe ausschliesslich Status bedeutet.
 */
@Immutable
data class StatusPalette(
    val neutral: Color,
    val onNeutral: Color,
    val submitted: Color,
    val onSubmitted: Color,
    val refunded: Color,
    val onRefunded: Color,
    val rejected: Color,
    val onRejected: Color,
) {
    /** Flaechenfarbe des Stempels. */
    fun background(status: SubmissionStatus): Color = when (status) {
        SubmissionStatus.GEKAUFT -> neutral
        SubmissionStatus.EINGEREICHT -> submitted
        SubmissionStatus.ERSTATTET -> refunded
        SubmissionStatus.ABGELEHNT -> rejected
    }

    /** Textfarbe auf dem Stempel — jeweils der Partner mit AA-Kontrast. */
    fun content(status: SubmissionStatus): Color = when (status) {
        SubmissionStatus.GEKAUFT -> onNeutral
        SubmissionStatus.EINGEREICHT -> onSubmitted
        SubmissionStatus.ERSTATTET -> onRefunded
        SubmissionStatus.ABGELEHNT -> onRejected
    }
}

private val LightStatusPalette = StatusPalette(
    neutral = PaperDim,
    onNeutral = Ink,
    submitted = StatusSubmitted,
    onSubmitted = Ink,
    refunded = StatusRefunded,
    onRefunded = Paper,
    rejected = StatusRejected,
    onRejected = Paper,
)

private val DarkStatusPalette = StatusPalette(
    neutral = InkPaperRaised,
    onNeutral = PaperOnInk,
    submitted = StatusSubmitted,
    onSubmitted = Ink,
    refunded = StatusRefunded,
    onRefunded = PaperOnInk,
    rejected = StatusRejectedOnInk,
    onRejected = PaperOnInk,
)

val LocalStatusPalette = staticCompositionLocalOf { LightStatusPalette }

/**
 * True, wenn das System Animationen abgeschaltet hat (Entwickleroptionen oder
 * Bedienungshilfen). Das Android-Gegenstueck zu `prefers-reduced-motion`.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

// Buttons und Navigation bleiben `ink` — kein bunter Akzent im Schema.
private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Ink,
    onPrimaryContainer = Paper,
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = PaperDim,
    onSecondaryContainer = Ink,
    tertiary = Ink,
    onTertiary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = Paper,
    surfaceContainerHigh = PaperDim,
    surfaceContainerHighest = PaperDim,
    outline = InkMuted,
    outlineVariant = PaperDim,
    error = StatusRejected,
    onError = Paper,
    errorContainer = StatusRejected,
    onErrorContainer = Paper,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    scrim = Ink,
)

private val DarkScheme = darkColorScheme(
    primary = PaperOnInk,
    onPrimary = InkPaper,
    primaryContainer = PaperOnInk,
    onPrimaryContainer = InkPaper,
    secondary = PaperOnInk,
    onSecondary = InkPaper,
    secondaryContainer = InkPaperRaised,
    onSecondaryContainer = PaperOnInk,
    tertiary = PaperOnInk,
    onTertiary = InkPaper,
    background = InkPaper,
    onBackground = PaperOnInk,
    surface = InkPaper,
    onSurface = PaperOnInk,
    surfaceVariant = InkPaperRaised,
    onSurfaceVariant = InkMutedOnInk,
    surfaceContainerLowest = InkPaper,
    surfaceContainerLow = InkPaper,
    surfaceContainer = InkPaper,
    surfaceContainerHigh = InkPaperRaised,
    surfaceContainerHighest = InkPaperRaised,
    outline = InkMutedOnInk,
    outlineVariant = InkPaperRaised,
    error = StatusRejectedOnInk,
    onError = PaperOnInk,
    errorContainer = StatusRejectedOnInk,
    onErrorContainer = PaperOnInk,
    inverseSurface = PaperOnInk,
    inverseOnSurface = InkPaper,
    scrim = InkPaper,
)

/**
 * Material You Dynamic Color ist **nicht** eingebaut — bewusst. Ein aus dem
 * Hintergrundbild abgeleitetes Schema wuerde Gelb und Gruen umfaerben und damit die
 * Statussemantik zerstoeren.
 */
@Composable
fun GzgTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val statusPalette = if (darkTheme) DarkStatusPalette else LightStatusPalette

    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalStatusPalette provides statusPalette,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = GzgTypography,
            shapes = GzgShapes,
            content = content,
        )
    }
}

/** Zugriff auf die projekteigenen Tokens neben `MaterialTheme`. */
object GzgTheme {
    val status: StatusPalette
        @Composable @ReadOnlyComposable get() = LocalStatusPalette.current

    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}
