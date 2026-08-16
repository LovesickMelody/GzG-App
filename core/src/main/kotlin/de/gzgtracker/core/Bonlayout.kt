package de.gzgtracker.core

import kotlin.math.abs

/**
 * Ein erkanntes Textstück mit seiner Lage auf dem Bild.
 *
 * Die Maße sind Bildpunkte, so wie die Texterkennung sie liefert. Absolute
 * Werte spielen keine Rolle — verglichen wird immer nur innerhalb desselben
 * Bildes.
 */
data class Textstueck(
    val text: String,
    val links: Int,
    val oben: Int,
    val unten: Int,
) {
    val mitte: Int get() = (oben + unten) / 2
    val hoehe: Int get() = (unten - oben).coerceAtLeast(1)
}

/**
 * Setzt die Zeilen eines Kassenbons wieder zusammen.
 *
 * Warum das nötig ist: Die Texterkennung gibt keinen Fließtext zurück, sondern
 * Blöcke. Auf einem Kassenbon stehen Artikelname und Preis weit auseinander —
 * links der Name, rechts am Rand der Betrag. Die Erkennung macht daraus gern
 * zwei getrennte Blöcke: erst alle Namen untereinander, dann alle Preise
 * untereinander. Im Text steht dann
 *
 * ```
 * BOROTALCO DEO
 * MILCH
 * 3,45
 * 1,19
 * ```
 *
 * und keine Auswertung der Welt kann daraus noch ablesen, welcher Betrag zu
 * welchem Artikel gehört. Genau daran scheiterten Produktpreis, Händler und
 * Kaufdatum am Gerät, obwohl die Erkennung selbst sauber gelesen hatte.
 *
 * Die Lösung ist die Lage auf dem Bild: Was auf gleicher Höhe steht, gehörte
 * auf dem Papier in eine Zeile. Danach steht wieder `BOROTALCO DEO   3,45` da.
 */
object Bonlayout {

    /**
     * Baut aus den erkannten Stücken einen Text, in dem eine Zeile wieder eine
     * Zeile ist — von oben nach unten, innerhalb der Zeile von links nach rechts.
     */
    fun zuText(stuecke: List<Textstueck>): String {
        val brauchbar = stuecke.filter { it.text.isNotBlank() }
        if (brauchbar.isEmpty()) return ""

        val zeilen = mutableListOf<MutableList<Textstueck>>()
        for (stueck in brauchbar.sortedBy { it.mitte }) {
            // Nur gegen die zuletzt begonnene Zeile prüfen: Die Stücke kommen
            // von oben nach unten, weiter oben liegende Zeilen sind erledigt.
            val letzte = zeilen.lastOrNull()
            if (letzte != null && letzte.any { gleicheZeile(it, stueck) }) {
                letzte.add(stueck)
            } else {
                zeilen.add(mutableListOf(stueck))
            }
        }

        return zeilen.joinToString("\n") { zeile ->
            // Drei Leerzeichen: Auf dem Papier ist zwischen Name und Betrag viel
            // Platz, und die Auswertung trennt ohnehin an Wortgrenzen.
            zeile.sortedBy { it.links }.joinToString("   ") { it.text.trim() }
        }
    }

    /**
     * Wirft Dopplungen aus überlappenden Ausschnitten weg.
     *
     * Ein ganzer Kassenbon ist zu fein für einen einzigen Durchgang der
     * Texterkennung — dann muss man mit dem Telefon dicht heran, und bei
     * Aktionen, die den vollständigen Bon verlangen, geht das nicht. Der Ausweg
     * ist, das Bild in überlappende Streifen zu zerlegen und jeden einzeln zu
     * lesen; jeder Streifen bekommt so mehr Bildpunkte je Zeile.
     *
     * Was im Überlappungsbereich liegt, kommt dabei zweimal an. Doppelt gelesene
     * Beträge wären fatal: Aus zwei mal `3,45` würde sonst ein zweiter Posten,
     * und die Artikelliste stimmte nicht mehr.
     */
    fun vereinige(stuecke: List<Textstueck>): List<Textstueck> {
        val behalten = mutableListOf<Textstueck>()
        for (stueck in stuecke.sortedBy { it.mitte }) {
            val schonDa = behalten.any { vorhanden ->
                vorhanden.text.trim() == stueck.text.trim() &&
                    abs(vorhanden.mitte - stueck.mitte) <= minOf(vorhanden.hoehe, stueck.hoehe) &&
                    abs(vorhanden.links - stueck.links) <= minOf(vorhanden.hoehe, stueck.hoehe)
            }
            if (!schonDa) behalten.add(stueck)
        }
        return behalten
    }

    /**
     * True, wenn zwei Stücke auf dem Papier in derselben Zeile standen.
     *
     * Maßstab ist die kleinere der beiden Höhen: Ein großer Betrag rechts und
     * ein klein gedruckter Name links gehören trotzdem zusammen, solange ihre
     * Mitten nah beieinander liegen. Grosszuegiger zu werden ist gefaehrlich —
     * dann rutschen zwei Artikelzeilen ineinander und der Preis landet beim
     * falschen Namen.
     */
    private fun gleicheZeile(a: Textstueck, b: Textstueck): Boolean =
        abs(a.mitte - b.mitte) <= minOf(a.hoehe, b.hoehe) * 0.6
}
