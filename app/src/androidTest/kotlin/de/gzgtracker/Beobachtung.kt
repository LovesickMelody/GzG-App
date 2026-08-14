package de.gzgtracker

/**
 * Markiert eine Pruefung, die berichtet statt zu blockieren.
 *
 * Gedacht fuer Fragen, deren Antwort im kopflosen Emulator nicht verlaesslich ist —
 * etwa ob ein Element pixelgenau im Fenster liegt. Solche Pruefungen sind nuetzlich,
 * duerfen aber nicht darueber entscheiden, ob eine APK ausgeliefert wird: Ein roter
 * Lauf soll heissen "die App ist kaputt", nicht "eine Messung war unschluessig".
 * Sonst gewoehnt man sich an rote Haken und uebersieht den einen, der zaehlt.
 *
 * Alles, was einen echten Absturz oder Datenverlust anzeigt, gehoert **nicht**
 * hierher, sondern bleibt blockierend.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class Beobachtung
