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
    /**
     * True, wenn der Betrag nicht an einem Schlüsselwort hing, sondern der
     * größte plausible auf dem Bon ist. Die App sagt das dann auch dazu.
     */
    val preisGeraten: Boolean = false,
    /** Zeilen, die nach Artikeln aussehen — als Vorschlag für den Produktnamen. */
    val artikel: List<String> = emptyList(),
    /** Händler aus der Kopfzeile des Bons. */
    val haendler: String? = null,
    /** False, wenn die Texterkennung überhaupt nichts gefunden hat. */
    val textErkannt: Boolean = true,
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

    fun auswerten(
        text: String,
        heute: LocalDate = LocalDate.now(),
        /** Name des Produkts, um das es geht — steuert, welche Zeile zaehlt. */
        produkt: String? = null,
        haendlerliste: List<String> = BEKANNTE_HAENDLER,
    ): Bonauswertung {
        if (text.isBlank()) return Bonauswertung(textErkannt = false)

        // Reihenfolge nach Verlaesslichkeit: die Zeile des gesuchten Produkts,
        // dann die ausgewiesene Summe, dann der groesste Betrag.
        val zumProdukt = produkt?.let { lesePreisFuerProdukt(text, it) }
        val ueberSchluesselwort = if (zumProdukt == null) lesePreis(text) else null
        val geraten = if (zumProdukt == null && ueberSchluesselwort == null) {
            groessterBetrag(text)
        } else {
            null
        }

        return Bonauswertung(
            preisCents = zumProdukt ?: ueberSchluesselwort ?: geraten,
            datum = leseDatum(text, heute),
            preisGeraten = zumProdukt == null && ueberSchluesselwort == null && geraten != null,
            artikel = leseArtikel(text),
            haendler = leseHaendler(text, haendlerliste),
        )
    }

    /** Haendlernamen, die auf einem deutschen Kassenbon oben stehen. */
    val BEKANNTE_HAENDLER = listOf(
        "dm", "Rossmann", "Müller", "Edeka", "Rewe", "Kaufland", "Lidl", "Aldi",
        "Netto", "Penny", "Norma", "Globus", "Budni", "tegut", "Combi", "Famila",
        "Marktkauf", "Trinkgut", "Getränkeland", "Real",
    )

    /**
     * Sucht den Betrag der Zeile, die zum gesuchten Produkt gehoert.
     *
     * Das ist der eigentlich richtige Wert: Erstattet wird das Aktionsprodukt,
     * nicht der ganze Einkauf. Wer mit einem Bon ueber 79 € einreicht, weil dort
     * auch Waschmittel und Getraenke draufstehen, bekommt nichts — oder faellt
     * unangenehm auf.
     *
     * Bons kuerzen ab ("BIFI TASTY B."), deshalb zaehlt ein Wortanfang als
     * Treffer. Gewonnen hat die Zeile mit den meisten Treffern; bei null
     * Treffern gibt es kein Ergebnis, und der Aufrufer geht den naechsten Weg.
     */
    fun lesePreisFuerProdukt(text: String, produkt: String): Int? {
        val gesucht = teile(produkt)
        if (gesucht.isEmpty()) return null

        var bester: Pair<Int, Int>? = null // Treffer zu Betrag

        for (zeile in text.lines()) {
            val klein = zeile.lowercase()
            if (NIE_DER_PREIS.any { klein.contains(it) }) continue
            if (ENDBETRAG_WOERTER.any { klein.contains(it) }) continue

            val betrag = betragAusZeile(zeile) ?: continue
            val vorhanden = teile(BETRAG.replace(zeile, ""))
            val treffer = gesucht.count { wort -> vorhanden.any { passt(it, wort) } }

            if (treffer > 0 && (bester == null || treffer > bester!!.first)) {
                bester = treffer to betrag
            }
        }

        return bester?.second
    }

    /**
     * Liest den Haendler aus der Kopfzeile.
     *
     * Nur die ersten Zeilen: Weiter unten stehen Werbetexte und Adressen, in
     * denen ein Name zufaellig vorkommen kann.
     */
    fun leseHaendler(text: String, bekannte: List<String> = BEKANNTE_HAENDLER): String? {
        val kopf = text.lines().take(8).joinToString(" ").lowercase()
        // Laengste Uebereinstimmung zuerst, damit "Netto Marken-Discount" nicht
        // an "Netto" haengenbleibt, wenn beides in der Liste steht.
        return bekannte.sortedByDescending { it.length }.firstOrNull { name ->
            Regex("(?<![a-zäöüß])${Regex.escape(name.lowercase())}(?![a-zäöüß])").containsMatchIn(kopf)
        }
    }

    /** Zerlegt einen Text in vergleichbare Wortteile ab drei Zeichen. */
    private fun teile(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^a-zäöüß0-9]+"))
            .filter { it.length >= 3 }

    /** Ein Wortanfang reicht: Bons kuerzen ab. */
    private fun passt(a: String, b: String): Boolean =
        a.startsWith(b) || b.startsWith(a)

    /**
     * Notnagel, wenn kein Schlüsselwort auf dem Bon steht.
     *
     * Auf einem Bon mit mehreren Positionen ist die Summe die größte Zahl —
     * sobald der gegebene Schein und das Rückgeld draußen sind. Das ist ein
     * Rateschluss und wird in der App auch so gekennzeichnet. Ohne ihn stünde
     * bei jedem Markt mit ungewohnter Beschriftung gar nichts da, und das war
     * beim ersten Versuch am Gerät genau der Fall.
     */
    private fun groessterBetrag(text: String): Int? =
        text.lines()
            .filter { zeile -> NIE_DER_PREIS.none { zeile.lowercase().contains(it) } }
            .mapNotNull(::betragAusZeile)
            .maxOrNull()

    /**
     * Sammelt die Zeilen, die nach gekauften Artikeln aussehen.
     *
     * Daraus kann man den Produktnamen antippen, statt ihn abzutippen. Den
     * richtigen zu *raten* wäre aussichtslos: Auf dem Bon steht "BONDUEL SAL
     * 250G", und welche der acht Positionen die Aktion betrifft, weiß nur der
     * Mensch davor.
     */
    fun leseArtikel(text: String, hoechstens: Int = 8): List<String> {
        val steuerkennzeichen = Regex("""\s+[A-Z]\s*$""")
        val menge = Regex("""^\d+\s*[xX]\s*""")

        return text.lines()
            .map { it.trim() }
            .filter { zeile ->
                val klein = zeile.lowercase()
                BETRAG.containsMatchIn(zeile) &&
                    NIE_DER_PREIS.none { klein.contains(it) } &&
                    ENDBETRAG_WOERTER.none { klein.contains(it) }
            }
            .mapNotNull { zeile ->
                // Betrag, Steuerkennzeichen und Mengenangabe abschneiden — übrig
                // bleibt der Name.
                BETRAG.replace(zeile, "")
                    .replace(steuerkennzeichen, "")
                    .replace(menge, "")
                    .trim()
                    .takeIf { it.length in 3..40 && it.any(Char::isLetter) }
            }
            .distinct()
            .take(hoechstens)
    }

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
