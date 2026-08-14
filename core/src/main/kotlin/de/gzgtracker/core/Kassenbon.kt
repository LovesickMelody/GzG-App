package de.gzgtracker.core

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Liest Gesamtbetrag und Kaufdatum aus dem erkannten Text eines Kassenbons.
 *
 * Die Texterkennung selbst passiert auf dem Gerät (siehe `data/receipt/BonLeser`);
 * hier steht nur die Auswertung — bewusst als reines Kotlin, damit sie ohne
 * Android und ohne Kamera testbar ist. Genau das ist auch der schwierige Teil:
 * Auf einem Bon stehen zwanzig Zahlen, und nur eine davon ist der Betrag, den
 * man bezahlt hat.
 *
 * Das Ergebnis ist ein **Vorschlag**, kein Ergebnis. Die App trägt ihn ein und
 * sagt dazu, dass er aus dem Bon gelesen wurde; ändern lässt er sich immer.
 * Deshalb ist die Regel hier auch eher vorsichtig: lieber kein Vorschlag als
 * ein falscher, den man übersieht.
 */
data class Bonauswertung(
    val preisCents: Int? = null,
    val datum: LocalDate? = null,
) {
    val hatVorschlag: Boolean get() = preisCents != null || datum != null
}

object Kassenbon {

    /**
     * Zeilen, die den Endbetrag ankündigen — in der Reihenfolge ihrer
     * Verlässlichkeit. "Summe" und "zu zahlen" stehen auf praktisch jedem
     * deutschen Bon.
     */
    private val ENDBETRAG_WOERTER = listOf(
        "zu zahlen",
        "summe",
        "gesamtbetrag",
        "gesamtsumme",
        "gesamt",
        "total",
    )

    /**
     * Zeilen, die zwar einen Betrag enthalten, aber nie den bezahlten Preis.
     *
     * "Bar 20,00" und "Rückgeld 7,66" sind die klassischen Fallen: Der
     * gegebene Schein ist immer größer als die Summe, ein Größter-Wert-Ansatz
     * würde also regelmäßig danebenliegen.
     */
    private val NIE_DER_PREIS = listOf(
        "rückgeld", "ruckgeld", "rueckgeld",
        "gegeben", "bar ", "barzahlung", "wechselgeld",
        "mwst", "mehrwertsteuer", "ust", "steuer",
        "netto", "brutto",
        "gutschein", "rabatt", "ersparnis", "punkte",
        "trinkgeld",
    )

    private val BETRAG = Regex("""(?<![\d.,])(\d{1,4}[.,]\d{2})(?![\d.,])""")

    private val DATUM = Regex("""(?<!\d)(\d{1,2})[.\-/](\d{1,2})[.\-/](\d{2}|\d{4})(?!\d)""")

    fun auswerten(text: String, heute: LocalDate = LocalDate.now()): Bonauswertung =
        Bonauswertung(
            preisCents = lesePreis(text),
            datum = leseDatum(text, heute),
        )

    /**
     * Sucht den bezahlten Gesamtbetrag.
     *
     * Vorgehen: erst die Zeile mit dem verlässlichsten Schlüsselwort, sonst
     * gar nichts. Ein "größter Betrag auf dem Bon"-Ansatz wäre verlockend,
     * trifft aber bei Barzahlung den gegebenen Schein und bei einer
     * Mehrfachpackung den Einzelpreis — beides fällt beim Einreichen auf, wenn
     * es zu spät ist.
     */
    fun lesePreis(text: String): Int? {
        val zeilen = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        for (wort in ENDBETRAG_WOERTER) {
            for ((index, zeile) in zeilen.withIndex()) {
                val klein = zeile.lowercase()
                if (!klein.contains(wort)) continue
                if (NIE_DER_PREIS.any { klein.contains(it) }) continue

                // Der Betrag steht meist in derselben Zeile, bei schmalen Bons
                // aber auch in der nächsten — die Texterkennung bricht dann um.
                betragAusZeile(zeile)?.let { return it }
                zeilen.getOrNull(index + 1)
                    ?.takeIf { folge -> NIE_DER_PREIS.none { folge.lowercase().contains(it) } }
                    ?.let { folge -> betragAusZeile(folge)?.let { return it } }
            }
        }

        return null
    }

    private fun betragAusZeile(zeile: String): Int? {
        // Bei "Summe EUR 12,34 3 Artikel" gewinnt der letzte Betrag der Zeile:
        // Stückzahlen und Positionsnummern stehen links, der Preis rechts.
        val treffer = BETRAG.findAll(zeile).lastOrNull() ?: return null
        val cents = Money.parseOrNull(treffer.groupValues[1].replace('.', ',')) ?: return null
        // Ein Bon über 0,00 € oder über 10.000 € ist keiner.
        return cents.takeIf { it in 1..1_000_000 }
    }

    /**
     * Sucht das Kaufdatum.
     *
     * Auf einem Bon stehen mehrere Daten: Kaufdatum, manchmal ein
     * Mindesthaltbarkeitsdatum, bei Bons mit Gutschein auch dessen Frist. Der
     * Kauf kann nicht in der Zukunft liegen und liegt praktisch nie länger als
     * ein Jahr zurück — von den verbleibenden Daten gewinnt das jüngste.
     */
    fun leseDatum(text: String, heute: LocalDate = LocalDate.now()): LocalDate? {
        val fruehestens = heute.minusYears(1)

        return DATUM.findAll(text)
            .mapNotNull { treffer ->
                val (tag, monat, jahr) = treffer.destructured
                val volljahr = jahr.toInt().let { if (jahr.length == 2) 2000 + it else it }
                try {
                    LocalDate.of(volljahr, monat.toInt(), tag.toInt())
                } catch (fehler: DateTimeParseException) {
                    null
                } catch (fehler: java.time.DateTimeException) {
                    // Etwa der 31. Februar — auf einem schlecht erkannten Bon
                    // durchaus möglich.
                    null
                }
            }
            .filter { it in fruehestens..heute }
            .maxOrNull()
    }
}
