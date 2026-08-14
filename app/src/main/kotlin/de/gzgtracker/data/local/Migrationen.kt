package de.gzgtracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schemaänderungen zwischen zwei Versionen.
 *
 * Bewusst echte Migrationen statt `fallbackToDestructiveMigration()`: In dieser
 * Datenbank stehen Einreichungen, Konten und die Pfade zu den Bonfotos. Ein
 * Update, das die Belege eines halben Jahres wegwirft, wäre der schlimmste
 * denkbare Fehler dieser App — schlimmer als jeder Absturz.
 */
object Migrationen {

    /**
     * v2: Aktionen bekommen den Einreichungslink und die Teilnahme-Checkliste.
     *
     * Beide Spalten sind für bestehende Zeilen leer. Beim nächsten Feed-Abruf
     * füllen sie sich von selbst; bis dahin verlinkt die App weiter auf die
     * Portalseite und sagt bei der Checkliste ehrlich, dass sie nichts weiß.
     */
    val VON_1_AUF_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE promo_actions ADD COLUMN submitUrl TEXT")
            // Listen liegen als zusammengefügter Text vor (siehe Converters),
            // leer ist deshalb der leere String und nicht NULL.
            db.execSQL(
                "ALTER TABLE promo_actions ADD COLUMN requirements TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    val ALLE = arrayOf(VON_1_AUF_2)
}
