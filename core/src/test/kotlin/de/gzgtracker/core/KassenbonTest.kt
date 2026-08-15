package de.gzgtracker.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Die Texte stammen aus dem, was Kassenbons wirklich hergeben: Kopfzeile,
 * Positionen, Summe, Zahlungsart, Rückgeld, Steuertabelle. Genau in dieser
 * Umgebung muss die Auswertung den einen Betrag finden, der zählt.
 */
class KassenbonTest {

    private val heute = LocalDate.of(2026, 8, 14)

    private val rewe = """
        REWE Markt GmbH
        Musterstraße 1, 12345 Musterstadt
        14.08.2026 17:42 Bon-Nr. 4711

        BONDUELLE SALAT        2,49 A
        MILCH 1,5%             1,19 B
        BROT                   2,29 B

        SUMME EUR              5,97
        Geg. BAR              10,00
        Rückgeld               4,03

        MwSt A 19,00%  0,40
        MwSt B  7,00%  0,23
    """.trimIndent()

    @Test
    fun `findet die Summe zwischen zwanzig anderen Zahlen`() {
        assertEquals(597, Kassenbon.lesePreis(rewe))
    }

    @Test
    fun `nimmt nicht den gegebenen Schein`() {
        // "Geg. BAR 10,00" ist der groesste Betrag auf dem Bon — und nie der Preis.
        val preis = Kassenbon.lesePreis(rewe)
        assertEquals(597, preis)
    }

    @Test
    fun `findet das Kaufdatum in der Kopfzeile`() {
        assertEquals(LocalDate.of(2026, 8, 14), Kassenbon.leseDatum(rewe, heute))
    }

    @Test
    fun `erkennt zu zahlen`() {
        val text = """
            EDEKA
            Zu zahlen              12,34
        """.trimIndent()
        assertEquals(1234, Kassenbon.lesePreis(text))
    }

    @Test
    fun `liest den Betrag aus der naechsten Zeile wenn er umgebrochen ist`() {
        // Schmale Bons brechen um, die Texterkennung erst recht.
        val text = """
            SUMME
            8,45
        """.trimIndent()
        assertEquals(845, Kassenbon.lesePreis(text))
    }

    @Test
    fun `nimmt bei mehreren Betraegen in einer Zeile den rechten`() {
        // Links stehen Stueckzahl und Einzelpreis, rechts der Gesamtbetrag.
        assertEquals(1497, Kassenbon.lesePreis("Summe  3 x 4,99      14,97"))
    }

    @Test
    fun `verwechselt die Steuerzeile nicht mit dem Preis`() {
        val text = """
            Gesamt                 4,00
            MwSt 19,00%            0,64
        """.trimIndent()
        assertEquals(400, Kassenbon.lesePreis(text))
    }

    @Test
    fun `ohne Schluesselwort liefert lesePreis nichts`() {
        val text = """
            ARTIKEL A              2,49
            ARTIKEL B              1,19
        """.trimIndent()
        assertNull(Kassenbon.lesePreis(text))
    }

    @Test
    fun `raet den groessten Betrag wenn kein Schluesselwort dasteht`() {
        // Nicht jeder Markt schreibt "Summe" — beim ersten Versuch am Geraet
        // stand deshalb gar nichts im Feld. Lieber ein gekennzeichneter
        // Rateschluss als ein leeres Formular.
        val text = """
            ARTIKEL A              2,49
            ARTIKEL B              1,19
        """.trimIndent()
        val ergebnis = Kassenbon.auswerten(text, heute)
        assertEquals(249, ergebnis.preisCents)
        assertEquals(true, ergebnis.preisGeraten)
    }

    @Test
    fun `ein Treffer am Schluesselwort gilt nicht als geraten`() {
        assertEquals(false, Kassenbon.auswerten(rewe, heute).preisGeraten)
    }

    @Test
    fun `raet nicht den gegebenen Schein`() {
        val text = """
            ARTIKEL A              2,49
            Geg. BAR              20,00
            Rückgeld              17,51
        """.trimIndent()
        assertEquals(249, Kassenbon.auswerten(text, heute).preisCents)
    }

    @Test
    fun `schlaegt die Artikelzeilen als Produktnamen vor`() {
        // Raten waere aussichtslos — welche Position die Aktion betrifft, weiss
        // nur der Mensch davor. Also alle zur Auswahl stellen.
        val artikel = Kassenbon.leseArtikel(rewe)
        assertEquals(listOf("BONDUELLE SALAT", "MILCH 1,5%", "BROT"), artikel)
    }

    @Test
    fun `haelt Summe und Rueckgeld aus den Artikelvorschlaegen heraus`() {
        val artikel = Kassenbon.leseArtikel(rewe)
        assertEquals(false, artikel.any { it.contains("SUMME", ignoreCase = true) })
        assertEquals(false, artikel.any { it.contains("Rückgeld", ignoreCase = true) })
    }

    @Test
    fun `schneidet Mengenangabe und Steuerkennzeichen vom Artikel ab`() {
        assertEquals(listOf("BUTTER"), Kassenbon.leseArtikel("2 x BUTTER   3,98 A"))
    }

    @Test
    fun `merkt sich wenn gar kein Text erkannt wurde`() {
        // Der Unterschied zaehlt: "nichts gefunden" ist etwas anderes als
        // "Bild nicht lesbar", und die App sagt beides verschieden.
        assertEquals(false, Kassenbon.auswerten("", heute).textErkannt)
        assertEquals(true, Kassenbon.auswerten(rewe, heute).textErkannt)
    }

    @Test
    fun `verwirft einen Nullbetrag`() {
        assertNull(Kassenbon.lesePreis("Summe  0,00"))
    }

    @Test
    fun `nimmt das juengste plausible Datum`() {
        // Auf dem Bon steht auch die Frist eines Gutscheins in der Zukunft und
        // ein altes Datum aus der Fusszeile.
        val text = """
            01.02.2026 Kundenkarte seit
            14.08.2026 17:42
            Gutschein gültig bis 31.12.2027
        """.trimIndent()
        assertEquals(LocalDate.of(2026, 8, 14), Kassenbon.leseDatum(text, heute))
    }

    @Test
    fun `ignoriert Daten in der Zukunft`() {
        assertNull(Kassenbon.leseDatum("Mindestens haltbar bis 30.09.2027", heute))
    }

    @Test
    fun `ignoriert Daten weiter als ein Jahr zurueck`() {
        assertNull(Kassenbon.leseDatum("Ausgestellt am 01.01.2020", heute))
    }

    @Test
    fun `versteht zweistellige Jahre`() {
        assertEquals(LocalDate.of(2026, 8, 14), Kassenbon.leseDatum("14.08.26", heute))
    }

    @Test
    fun `verkraftet ein unmoegliches Datum`() {
        // Schlecht erkannte Bons liefern so etwas regelmaessig.
        assertNull(Kassenbon.leseDatum("31.02.2026", heute))
    }

    @Test
    fun `leerer Text ergibt keinen Vorschlag`() {
        val ergebnis = Kassenbon.auswerten("", heute)
        assertNull(ergebnis.preisCents)
        assertNull(ergebnis.datum)
        assertEquals(false, ergebnis.hatVorschlag)
    }

    @Test
    fun `wertet Preis und Datum zusammen aus`() {
        val ergebnis = Kassenbon.auswerten(rewe, heute)
        assertEquals(597, ergebnis.preisCents)
        assertEquals(LocalDate.of(2026, 8, 14), ergebnis.datum)
        assertEquals(true, ergebnis.hatVorschlag)
    }

    // --- Der Preis des Aktionsprodukts, nicht der des ganzen Einkaufs -------

    private val grosseinkauf = """
        REWE Markt GmbH
        Musterstraße 1, 12345 Musterstadt
        14.08.2026 17:42 Bon-Nr. 4711

        BOROTALCO DEO 150ML    3,45 A
        WASCHMITTEL 20WL      12,99 A
        BONDUELLE SALAT        2,49 A
        MILCH 1,5%             1,19 B
        KISTE WASSER          58,88 B

        SUMME EUR             79,00
        Geg. BAR              90,00
        Rückgeld              11,00
    """.trimIndent()

    @Test
    fun `nimmt den Posten des Aktionsprodukts statt der Summe`() {
        // Erstattet wird das Aktionsprodukt. Wer 79,00 € einreicht, weil auf
        // demselben Bon auch Waschmittel und Wasser stehen, bekommt nichts.
        assertEquals(345, Kassenbon.lesePreisFuerProdukt(grosseinkauf, "Borotalco Deo Invisible"))
    }

    @Test
    fun `versteht die Abkuerzungen auf dem Bon`() {
        val text = """
            BIFI TASTY B.          1,29 A
            SUMME                 24,80
        """.trimIndent()
        assertEquals(129, Kassenbon.lesePreisFuerProdukt(text, "BiFi Tasty Barbecue"))
    }

    @Test
    fun `nimmt die Zeile mit den meisten Treffern`() {
        val text = """
            DEO SPRAY FREMD        1,95 A
            BOROTALCO DEO 150ML    3,45 A
        """.trimIndent()
        assertEquals(345, Kassenbon.lesePreisFuerProdukt(text, "Borotalco Deo"))
    }

    @Test
    fun `verwechselt den Produktposten nicht mit der Summenzeile`() {
        // "Summe Borotalco-Aktion" gaebe es zwar selten, aber die Endbetragszeile
        // darf nie als Posten durchgehen.
        val text = """
            SUMME BOROTALCO       79,00
            BOROTALCO DEO          3,45 A
        """.trimIndent()
        assertEquals(345, Kassenbon.lesePreisFuerProdukt(text, "Borotalco"))
    }

    @Test
    fun `ohne passenden Posten gibt es keinen Produktpreis`() {
        assertNull(Kassenbon.lesePreisFuerProdukt(grosseinkauf, "Landliebe Pudding"))
    }

    @Test
    fun `ohne Produktnamen gibt es keinen Produktpreis`() {
        assertNull(Kassenbon.lesePreisFuerProdukt(grosseinkauf, "  "))
    }

    @Test
    fun `auswerten bevorzugt den Produktposten vor der Summe`() {
        val ergebnis = Kassenbon.auswerten(grosseinkauf, heute, produkt = "Borotalco Deo")
        assertEquals(345, ergebnis.preisCents)
        assertEquals(false, ergebnis.preisGeraten)
    }

    @Test
    fun `auswerten faellt ohne Treffer auf die Summe zurueck`() {
        val ergebnis = Kassenbon.auswerten(grosseinkauf, heute, produkt = "Landliebe Pudding")
        assertEquals(7900, ergebnis.preisCents)
        assertEquals(false, ergebnis.preisGeraten)
    }

    // --- Händler ------------------------------------------------------------

    @Test
    fun `liest den Haendler aus der Kopfzeile`() {
        assertEquals("Rewe", Kassenbon.leseHaendler(rewe))
    }

    @Test
    fun `erkennt dm trotz Bindestrich`() {
        val text = """
            dm-drogerie markt GmbH + Co. KG
            Musterstraße 1
            14.08.2026
        """.trimIndent()
        assertEquals("dm", Kassenbon.leseHaendler(text))
    }

    @Test
    fun `haelt dm aus einem anderen Wort heraus`() {
        // "Admiral" enthaelt "dm" — ein Haendlername ist das nicht.
        assertNull(Kassenbon.leseHaendler("Admiralstraße 3\nKiosk am Eck"))
    }

    @Test
    fun `sucht den Haendler nicht in der Fusszeile`() {
        // Weiter unten stehen Werbetexte, in denen ein Name zufaellig vorkommt.
        val text = """
            Kiosk am Eck
            Musterstraße 1
            14.08.2026
            ARTIKEL A              2,49
            SUMME                  2,49
            Geg. BAR               5,00
            Rückgeld               2,51
            MwSt A 19,00%          0,40
            Auch erhältlich bei Rossmann
        """.trimIndent()
        assertNull(Kassenbon.leseHaendler(text))
    }

    @Test
    fun `gibt den Haendler mit auswerten zurueck`() {
        assertEquals("Rewe", Kassenbon.auswerten(rewe, heute).haendler)
    }
}
