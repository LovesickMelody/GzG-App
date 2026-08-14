package de.gzgtracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.gzgtracker.R

/**
 * Drei Familien mit klarer Aufgabenteilung:
 *
 * - **Archivo** — Ueberschriften, kraeftig und eng gesetzt.
 * - **Inter** — Fliesstext und Labels.
 * - **JetBrains Mono** — alles Zaehlbare: Geldbetraege, EANs, Daten. Monospace heisst
 *   von Haus aus Tabellenziffern, damit stehen rechtsbuendige Betraege sauber
 *   untereinander.
 *
 * Die Schriften liegen als Latin-Subset im APK (rund 390 KB) statt als Downloadable
 * Font. So rendert die App ohne Play Services, offline und beim ersten Frame korrekt —
 * bei Downloadable Fonts blitzt sonst die Systemschrift auf. Lizenzen: `licenses/`.
 */

val ArchivoFamily = FontFamily(
    Font(R.font.archivo_600, FontWeight.SemiBold),
    Font(R.font.archivo_700, FontWeight.Bold),
)

val InterFamily = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
)

val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_400, FontWeight.Normal),
    Font(R.font.jetbrains_mono_500, FontWeight.Medium),
)

/** Betraege, EANs, Daten. Rechtsbuendig, damit die Kommata eine Spalte bilden. */
val MoneyTextStyle = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
    textAlign = TextAlign.End,
)

/** Kleinere Variante fuer Nebenbetraege in der Summenkarte. */
val MoneySmallTextStyle = MoneyTextStyle.copy(
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.Normal,
)

/** Der grosse Betrag in der Summenkarte. */
val MoneyLargeTextStyle = MoneyTextStyle.copy(
    fontSize = 26.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.01).em,
)

/** EANs und Datumsangaben im Fliesstext. */
val CodeTextStyle = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)

val GzgTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.025).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.02).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.02).em,
    ),
    titleLarge = TextStyle(
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.015).em,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.01.em,
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.02.em,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.04.em,
    ),
)
