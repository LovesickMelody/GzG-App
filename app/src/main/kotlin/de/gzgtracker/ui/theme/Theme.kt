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
 * primary/secondary/tertiary gepresst — dort sitzt aber der Akzent, der "das kannst
 * du antippen" bedeutet. Beides im selben Topf haette genau die Verwechslung erzeugt,
 * die dieses Farbsystem vermeiden soll.
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
    /**
     * Fuer eine Frist, die heute oder morgen ablaeuft.
     *
     * Derselbe Rotton wie "abgelehnt", aber ausschliesslich als **Textfarbe**.
     * Flaechen bleiben dem Status vorbehalten, sonst saehe eine ablaufende Aktion
     * aus wie eine abgelehnte Einreichung.
     */
    val dringend: Color,
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
    dringend = StatusRejected,
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
    dringend = StatusRejectedOnInk,
)

val LocalStatusPalette = staticCompositionLocalOf { LightStatusPalette }

/**
 * True, wenn das System Animationen abgeschaltet hat (Entwickleroptionen oder
 * Bedienungshilfen). Das Android-Gegenstueck zu `prefers-reduced-motion`.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

// `primary` traegt ab jetzt den Akzent: Was man antippen kann, sieht auch danach
// aus. `secondary` bleibt Tinte — das sind die zurueckhaltenden Handlungen.
private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentSoft,
    onPrimaryContainer = OnAccentSoft,
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = AccentSoft,
    onSecondaryContainer = OnAccentSoft,
    tertiary = Ink,
    onTertiary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = PaperCard,
    surfaceContainer = PaperCard,
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
    primary = AccentOnInk,
    onPrimary = InkPaper,
    primaryContainer = AccentSoftOnInk,
    onPrimaryContainer = PaperOnInk,
    secondary = PaperOnInk,
    onSecondary = InkPaper,
    secondaryContainer = AccentSoftOnInk,
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
    surfaceContainerLow = InkPaperCard,
    surfaceContainer = InkPaperCard,
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
 * Statussemantik zerstoeren; es wuerde ausserdem den Akzent ersetzen, der hier
 * ausschliesslich "das kannst du antippen" bedeutet.
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
