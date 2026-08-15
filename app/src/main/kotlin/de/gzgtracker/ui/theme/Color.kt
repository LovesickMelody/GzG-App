package de.gzgtracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Die einzige Stelle im Projekt, an der Farben definiert werden.
 *
 * Farbe hat hier genau **drei Rollen**, und keine Farbe hat zwei davon:
 *
 * 1. **Status** — gelb, gruen, rot. Eingereicht, erstattet, abgelehnt. Nur dafuer,
 *    und nur als Flaeche mit Icon und Text darauf.
 * 2. **Interaktion** — der Akzent. Was man antippen kann und was gerade ausgewaehlt
 *    ist: primaere Knoepfe, aktiver Reiter, gesetztes Lesezeichen, Fokus.
 * 3. **Struktur** — Tinte auf Papier. Text, Flaechen, Linien.
 *
 * Vorher war Regel 2 nicht vorgesehen, alles Bedienbare war `ink`. Das Ergebnis war
 * eine Oberflaeche, auf der ein Knopf aussah wie eine Ueberschrift, und in der die
 * Statusfarben als einzige Farbe zwar auffielen, aber ausserhalb des Stempels nichts
 * trugen. Der Akzent liegt bewusst weit weg von Gelb, Gruen und Rot — sonst haette er
 * die Statusbedeutung verwaessert.
 *
 * Material You Dynamic Color bleibt deshalb aus: Ein aus dem Hintergrundbild
 * abgeleitetes Schema wuerde genau diese Zuordnung ueberschreiben.
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

// --- Akzent: alles Bedienbare ----------------------------------------------
// Ein tiefes Tintenblau. Blau ist die einzige kraeftige Farbe, die sich mit Gelb,
// Gruen und Rot nicht ins Gehege kommt — auch nicht fuer jemanden mit Rot-Gruen-
// Schwaeche, fuer den Gelb und Gruen ohnehin naeher beieinander liegen.

/** Primaere Knoepfe, aktiver Reiter, Auswahl. Kontrast auf `paper` 8,9:1. */
val Accent = Color(0xFF1F3A93)

/** Text und Symbole auf [Accent]. */
val OnAccent = Color(0xFFF7F6F2)

/** Ruhige Flaeche fuer Ausgewaehltes, das keine Hauptaktion ist (Chips, Container). */
val AccentSoft = Color(0xFFE3E7F6)

/** Text auf [AccentSoft]. Kontrast 8,1:1. */
val OnAccentSoft = Color(0xFF172C6E)

/** Im Dunkelmodus traegt der Akzent den Text, nicht die Flaeche. Kontrast 8,4:1. */
val AccentOnInk = Color(0xFF9DB4F5)

/** Gefuellte Akzentflaeche im Dunkelmodus — dunkel genug fuer hellen Text darauf. */
val AccentSoftOnInk = Color(0xFF23305C)

// --- Struktur ---------------------------------------------------------------

/**
 * Dritte Papierstufe: Karten, die Zusammengehoeriges gruppieren.
 *
 * Vorher lag alles auf einer Ebene, getrennt nur durch Haarlinien — es gab nichts,
 * woran das Auge einen Abschnitt als Einheit erkannt haette.
 */
val PaperCard = Color(0xFFF4F1EA)

/** Dasselbe im Dunkelmodus. */
val InkPaperCard = Color(0xFF1B1E25)

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
