package de.gzgtracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Die einzige Stelle im Projekt, an der Farben definiert werden.
 *
 * Leitgedanke: **Farbe ist fuer Status reserviert.** Buttons, Navigation und Akzente
 * sind `ink`. Der Status ist damit das einzig Bunte auf dem Screen und springt sofort
 * ins Auge. Deshalb ist Material You Dynamic Color auch bewusst deaktiviert — es
 * wuerde die Gelb/Gruen-Semantik ueberschreiben.
 */

// --- Papier und Tinte -------------------------------------------------------

/** Hintergrund hell, Ton von Thermopapier. */
val Paper = Color(0xFFFAF8F3)

/** Leicht abgesetzte Flaeche auf hellem Papier (Fuehrungslinien, Trennungen). */
val PaperDim = Color(0xFFEFEBE1)

/** Text und primaere Buttons. */
val Ink = Color(0xFF16181C)

/** Sekundaertext und Punktlinien. Kontrast auf `paper` 4,8:1. */
val InkMuted = Color(0xFF6B6F76)

// --- Dunkles Papier ---------------------------------------------------------
// Dieselbe Logik, nur invertiert: das Papier wird dunkel, die Tinte hell.

/** Hintergrund dunkel — identisch mit `ink`. */
val InkPaper = Color(0xFF16181C)

/** Angehobene Flaeche im Dunkelmodus (Karten, Belegzeilen). */
val InkPaperRaised = Color(0xFF1F2229)

/** Text auf dunklem Papier. */
val PaperOnInk = Color(0xFFF3F1EC)

/** Sekundaertext im Dunkelmodus. Kontrast auf `inkPaper` 6,8:1. */
val InkMutedOnInk = Color(0xFF9AA0A8)

// --- Status -----------------------------------------------------------------
// Die drei Statusfarben sind in beiden Modi identisch. Sie werden als gefuellte
// Flaeche eingesetzt, der Text darauf traegt den Kontrast:
//   gelb + ink      = 8,7:1
//   gruen + paper   = 5,3:1
//   rot + paper     = 6,5:1

/** Eingereicht. */
val StatusSubmitted = Color(0xFFE8B208)

/** Erstattet. */
val StatusRefunded = Color(0xFF1B7A4B)

/** Abgelehnt. */
val StatusRejected = Color(0xFFB3261E)

/**
 * Einzige Abweichung im Dunkelmodus: Rot wird minimal aufgehellt, damit die
 * Badge-Flaeche gegen das dunkle Papier die 3:1-Grenze fuer Nicht-Text haelt
 * (2,8:1 auf 3,9:1). Gelb und Gruen halten sie bereits unveraendert.
 */
val StatusRejectedOnInk = Color(0xFFCF4339)
