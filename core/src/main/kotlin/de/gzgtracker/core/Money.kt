package de.gzgtracker.core

/**
 * Geldbetraege sind im gesamten Projekt `Int` in Cent. Nie Float, nie Double —
 * Rundungsfehler bei Erstattungssummen waeren sonst nicht auszuschliessen.
 *
 * Diese Datei ist die einzige Stelle, die zwischen Cent und deutscher
 * EUR-Darstellung ("3,99 €") uebersetzt.
 */
object Money {

    /** 1234 -> "12,34" (ohne Waehrungszeichen, fuer Eingabefelder). */
    fun formatPlain(cents: Int): String {
        val negative = cents < 0
        val abs = if (negative) -cents.toLong() else cents.toLong()
        val euro = abs / 100
        val rest = abs % 100
        val body = "${groupThousands(euro)},${rest.toString().padStart(2, '0')}"
        return if (negative) "-$body" else body
    }

    /** 1234 -> "12,34 €" (fuer Anzeige). */
    fun format(cents: Int): String = "${formatPlain(cents)} €"

    /**
     * Liest eine deutsche Betragseingabe als Cent.
     *
     * Akzeptiert Komma und Punkt als Dezimaltrenner, Leerzeichen, ein
     * Euro-Zeichen sowie Tausenderpunkte ("1.234,56"). Gibt `null` zurueck,
     * wenn die Eingabe kein Betrag ist — die aufrufende Stelle entscheidet
     * dann ueber die Fehlermeldung.
     */
    fun parseOrNull(input: String): Int? {
        val cleaned = buildString {
            for (character in input) {
                when {
                    character.isDigit() -> append(character)
                    character == ',' || character == '.' -> append(character)
                    character == '-' || character == '+' -> append(character)
                    character == '€' -> Unit // Euro-Zeichen
                    character.isWhitespace() -> Unit
                    // isWhitespace() deckt geschuetzte Leerzeichen nicht ab
                    character == '\u00A0' || character == '\u202F' -> Unit
                    else -> return null
                }
            }
        }
        if (cleaned.isEmpty()) return null

        val negative = cleaned.startsWith("-")
        val unsigned = cleaned.removePrefix("-").removePrefix("+")
        if (unsigned.isEmpty()) return null
        if (unsigned.any { it == '-' || it == '+' }) return null

        // Der letzte Trenner ist der Dezimaltrenner, wenn danach ein bis zwei
        // Ziffern stehen. Sonst ist er ein Tausenderpunkt ("1.234").
        val lastSeparator = maxOf(unsigned.lastIndexOf(','), unsigned.lastIndexOf('.'))
        val decimalsLength = if (lastSeparator >= 0) unsigned.length - lastSeparator - 1 else 0
        val hasDecimals = lastSeparator >= 0 && decimalsLength in 1..2

        val integerPart: String
        val fractionPart: String
        if (hasDecimals) {
            integerPart = unsigned.substring(0, lastSeparator)
            fractionPart = unsigned.substring(lastSeparator + 1)
            // Derselbe Trenner darf nicht zusaetzlich im Ganzzahlteil stehen:
            // "3,99,50" ist keine gueltige Eingabe.
            if (integerPart.contains(unsigned[lastSeparator])) return null
        } else {
            integerPart = unsigned
            fractionPart = ""
        }

        // Enthaelt der Ganzzahlteil einen Trenner, muss er sauber in
        // Dreiergruppen gegliedert sein.
        val groupSeparator = integerPart.firstOrNull { it == ',' || it == '.' }
        if (groupSeparator != null) {
            if (integerPart.any { !it.isDigit() && it != groupSeparator }) return null
            val groups = integerPart.split(groupSeparator)
            if (groups.first().isEmpty() || groups.first().length > 3) return null
            if (groups.drop(1).any { group -> group.length != 3 }) return null
        }

        val digits = integerPart.filter { it.isDigit() }
        if (digits.isEmpty() && fractionPart.isEmpty()) return null

        val euro = if (digits.isEmpty()) 0L else digits.toLongOrNull() ?: return null
        val cents = when (fractionPart.length) {
            0 -> 0L
            1 -> fractionPart.toLong() * 10
            else -> fractionPart.toLong()
        }

        val total = euro * 100 + cents
        if (total > Int.MAX_VALUE) return null
        return if (negative) (-total).toInt() else total.toInt()
    }

    private fun groupThousands(value: Long): String {
        val raw = value.toString()
        if (raw.length <= 3) return raw
        return raw.reversed()
            .chunked(3)
            .joinToString(".")
            .reversed()
    }
}
