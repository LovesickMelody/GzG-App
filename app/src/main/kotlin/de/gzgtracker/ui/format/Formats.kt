package de.gzgtracker.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Deutsches Datumsformat, projektweit einheitlich. */
private val DATUM: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

fun LocalDate.deutsch(): String = format(DATUM)

fun Instant.deutschesDatum(): String =
    atZone(ZoneId.systemDefault()).toLocalDate().format(DATUM)

/** "vor 3 Tagen", "heute" — fuer den Zeitstempel der letzten Aktualisierung. */
fun Instant.relativeAngabe(jetzt: Instant = Instant.now()): String {
    val minuten = java.time.Duration.between(this, jetzt).toMinutes()
    return when {
        minuten < 1 -> "gerade eben"
        minuten < 60 -> "vor $minuten Minuten"
        minuten < 60 * 24 -> "vor ${minuten / 60} Stunden"
        minuten < 60 * 24 * 2 -> "gestern"
        else -> "vor ${minuten / (60 * 24)} Tagen"
    }
}

/** EAN in Viererbloecken, damit sie lesbar bleibt. */
fun String.eanLesbar(): String = chunked(4).joinToString(" ")

/**
 * Dieselbe Angabe, nur kurz — fuer Zeilen, in denen wenig Platz ist.
 *
 * "Aktualisiert vor 4 Minuten" brach in der Aktionsliste mitten im Wort um.
 */
fun Instant.relativeKurz(jetzt: Instant = Instant.now()): String {
    val minuten = java.time.Duration.between(this, jetzt).toMinutes()
    return when {
        minuten < 1 -> "gerade eben"
        minuten < 60 -> "vor $minuten Min."
        minuten < 60 * 24 -> "vor ${minuten / 60} Std."
        minuten < 60 * 24 * 2 -> "gestern"
        else -> "vor ${minuten / (60 * 24)} Tagen"
    }
}
