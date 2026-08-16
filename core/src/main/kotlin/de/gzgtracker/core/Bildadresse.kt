package de.gzgtracker.core

/**
 * Bildadressen auf Bildschirmgröße bringen.
 *
 * Der Feed liefert Vorschaubilder: mydealz hängt die gewünschte Kantenlänge in
 * die Adresse (`…/re/150x150/qt/55/…`), und 150 Pixel bei Qualität 55 reichen
 * für das Wappen in der Liste. Auf der Aktionsseite wird dasselbe Bild über die
 * volle Breite gezogen — auf einem heutigen Telefon sind das über 1000 Pixel,
 * also das Siebenfache. Das Ergebnis ist der Matsch, der in der App zu sehen war.
 *
 * Die Adresse trägt die Größe selbst, also lässt sie sich umschreiben. Wichtig:
 * Der Aufrufer muss auf die ursprüngliche Adresse zurückfallen können, falls der
 * Anbieter die größere Fassung nicht hergibt — ein unscharfes Bild ist besser
 * als gar keines.
 */
object Bildadresse {

    /** Kantenlänge, die für die volle Breite einer Aktionsseite reicht. */
    const val GROSSE_KANTE = 600

    /** Bildqualität, mit der neu angefragt wird. */
    private const val QUALITAET = 80

    // …/re/150x150/qt/55/… — Kantenlängen und Qualität stehen in der Adresse.
    private val MASSANGABE = Regex("""/re/(\d+)x(\d+)/qt/(\d+)/""")

    /**
     * Gibt dieselbe Adresse mit größerer Kante zurück — oder unverändert, wenn
     * sie keine Maßangabe trägt oder schon groß genug ist.
     *
     * Das Seitenverhältnis bleibt erhalten: Bei mydealz sind beide Kanten
     * gleich, aber die Regel darf nicht davon abhängen.
     */
    fun groesser(adresse: String, kante: Int = GROSSE_KANTE): String {
        val treffer = MASSANGABE.find(adresse) ?: return adresse

        val breite = treffer.groupValues[1].toIntOrNull() ?: return adresse
        val hoehe = treffer.groupValues[2].toIntOrNull() ?: return adresse
        val qualitaet = treffer.groupValues[3].toIntOrNull() ?: return adresse
        if (breite <= 0 || hoehe <= 0) return adresse

        // Schon groß genug: nicht anfassen. Ein Bild künstlich aufzublasen
        // kostet Daten und bringt keine Schärfe.
        val laengsteKante = maxOf(breite, hoehe)
        if (laengsteKante >= kante) return adresse

        val faktor = kante.toDouble() / laengsteKante
        val neueBreite = (breite * faktor).toInt().coerceAtLeast(1)
        val neueHoehe = (hoehe * faktor).toInt().coerceAtLeast(1)
        val neueQualitaet = maxOf(qualitaet, QUALITAET)

        return adresse.replaceRange(
            treffer.range,
            "/re/${neueBreite}x$neueHoehe/qt/$neueQualitaet/",
        )
    }
}
