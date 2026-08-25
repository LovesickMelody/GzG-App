package de.gzgtracker.core

/**
 * Die Nummer unter dem Strichcode.
 *
 * Warum eine Prüfung nötig ist: Ein Strichcode wird nicht immer sauber gelesen,
 * und manche Anbieter verlangen die Nummer im Formular. Eine falsch übernommene
 * Ziffer fällt niemandem auf — die Erstattung bleibt dann einfach aus, ohne dass
 * jemand sagt, warum. Die letzte Ziffer jedes EAN ist genau dafür da.
 */
object Ean {

    /** Erlaubte Längen: EAN-8, UPC-A (12), EAN-13, GTIN-14. */
    private val LAENGEN = setOf(8, 12, 13, 14)

    /** Was zwischen den Ziffern stehen darf: Gruppentrenner, sonst nichts. */
    private val TRENNZEICHEN = setOf(' ', '-', '.', ' ', '\t')

    /**
     * Gibt die Nummer zurück, wenn sie eine gültige EAN ist — sonst `null`.
     *
     * Trennzeichen fallen weg; auf Packungen steht die Nummer oft in Gruppen
     * gesetzt. Alles andere führt zur Ablehnung: Nicht jede Zeichenkette, in der
     * dreizehn Ziffern vorkommen, ist eine EAN — "Art.Nr ABC4008400202037" wäre
     * es nicht, und stillschweigend die Ziffern herauszuklauben hieße, eine
     * fremde Nummer ins Formular zu schreiben.
     */
    fun pruefe(roh: String?): String? {
        val getrimmt = roh?.trim() ?: return null
        if (getrimmt.any { !it.isDigit() && it !in TRENNZEICHEN }) return null

        val ziffern = getrimmt.filter(Char::isDigit)
        if (ziffern.length !in LAENGEN) return null
        return ziffern.takeIf { stimmtPruefziffer(it) }
    }

    /**
     * Prüfziffer nach dem Standard: Von rechts nach links werden die Stellen
     * abwechselnd mit 3 und 1 gewichtet, ohne die Prüfziffer selbst. Die Summe
     * auf das nächste Vielfache von zehn ergänzt, ergibt sie.
     */
    fun stimmtPruefziffer(ziffern: String): Boolean {
        if (ziffern.length < 2 || !ziffern.all(Char::isDigit)) return false

        val ohnePruefziffer = ziffern.dropLast(1)
        val summe = ohnePruefziffer
            .reversed()
            .mapIndexed { stelle, zeichen ->
                val wert = zeichen - '0'
                if (stelle % 2 == 0) wert * 3 else wert
            }
            .sum()

        val erwartet = (10 - summe % 10) % 10
        return erwartet == ziffern.last() - '0'
    }
}
