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

    /**
     * v3: Eine Einreichung kann mehrere Belegfotos haben.
     *
     * Bisher gab es nur das Bonfoto. Die Portale verlangen aber je nach Aktion
     * das Produkt allein, den Bon allein oder beides zusammen auf einem Bild.
     * Die bestehende Spalte bleibt, wie sie ist — vorhandene Bonfotos wandern
     * also nicht und gehen nicht verloren.
     */
    val VON_2_AUF_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE submissions ADD COLUMN productImagePath TEXT")
            db.execSQL("ALTER TABLE submissions ADD COLUMN comboImagePath TEXT")
        }
    }

    /**
     * v4: Die Merkliste.
     *
     * Eigene Tabelle, damit eine Merkung den Feed-Abgleich überlebt — Aktionen
     * werden dabei ersetzt und verschwundene weggeräumt.
     */
    val VON_3_AUF_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS watchlist (
                    actionId TEXT NOT NULL PRIMARY KEY,
                    imWagen INTEGER NOT NULL DEFAULT 0,
                    addedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    val ALLE = arrayOf(VON_1_AUF_2, VON_2_AUF_3, VON_3_AUF_4)
}
