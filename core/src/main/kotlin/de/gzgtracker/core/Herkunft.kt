package de.gzgtracker.core

import java.net.URI

/**
 * Wer betreibt die Seite, auf der man gerade steht?
 *
 * Der eingebettete Browser folgt jeder Weiterleitung. Ohne diese Angabe sieht
 * eine fremde Domain genauso aus wie die Aktionsseite selbst — und darunter
 * sitzt der Knopf, der IBAN, Bankverbindung, Geburtsdatum und Anschrift in die
 * Formularfelder schreibt.
 */
fun hostVon(adresse: String?): String? {
    if (adresse.isNullOrBlank()) return null
    return try {
        URI(adresse).host?.removePrefix("www.")?.lowercase()?.takeIf { it.isNotBlank() }
    } catch (fehler: Exception) {
        // Eine unlesbare Adresse ist kein Gastgeber. Lieber nichts anzeigen als
        // etwas Falsches.
        null
    }
}

/**
 * Gehoert die aktuelle Seite noch zur Aktion? Wenn nicht: ihr Gastgeber.
 *
 * Gleich oder Unterdomaene gilt als dieselbe Herkunft — eine Kampagne unter
 * `airwick.justsnap.eu`, deren Formular auf `justsnap.eu` liegt, ist keine
 * Abweichung. `boeses.example` schon.
 *
 * Bewusst ohne Public-Suffix-Liste; deshalb muessen beide Namen einen Punkt
 * haben, sonst gaelte jeder `.de`-Host als verwandt mit jedem anderen.
 *
 * Solange nichts geladen ist oder die Startadresse fehlt, wird **nicht**
 * gewarnt: Eine Warnung ohne Grundlage lehrt nur, sie wegzuklicken.
 */
fun fremderGastgeber(aktuell: String?, erwartet: String?): String? {
    val hier = hostVon(aktuell) ?: return null
    val dort = hostVon(erwartet) ?: return null

    val zusammen = hier == dort || (
        hier.contains('.') && dort.contains('.') &&
            (hier.endsWith(".$dort") || dort.endsWith(".$hier"))
        )

    return if (zusammen) null else hier
}
