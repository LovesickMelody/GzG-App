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

    /**
     * v5: Das Konto traegt jetzt auch die Angaben zur Person.
     *
     * Bei einer Erstattung gehoeren Konto und Person ohnehin zusammen — das
     * Geld geht auf dieses Konto, also traegt man auch dessen Inhaber ins
     * Formular ein. Alle Spalten sind freiwillig und bleiben leer, bis jemand
     * sie fuellt.
     */
    val VON_4_AUF_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN iban TEXT")
            db.execSQL("ALTER TABLE accounts ADD COLUMN vorname TEXT")
            db.execSQL("ALTER TABLE accounts ADD COLUMN nachname TEXT")
            db.execSQL("ALTER TABLE accounts ADD COLUMN strasse TEXT")
            db.execSQL("ALTER TABLE accounts ADD COLUMN hausnummer TEXT")
            db.execSQL("ALTER TABLE accounts ADD COLUMN plz TEXT")
            db.execSQL("ALTER TABLE accounts ADD COLUMN ort TEXT")
            db.execSQL("ALTER TABLE accounts ADD COLUMN telefon TEXT")
            db.execSQL("ALTER TABLE accounts ADD COLUMN email TEXT")
        }
    }

    /**
     * v6: Anrede und Geburtsdatum.
     *
     * Beides verlangen die Formulare der Anbieter regelmaessig, und beides
     * moechte niemand jedes Mal neu eintippen. Freiwillig wie der Rest des
     * Profils.
     */
    val VON_5_AUF_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN anrede TEXT")
            db.execSQL("ALTER TABLE accounts ADD COLUMN geburtsdatum TEXT")
        }
    }

    /** v7: BIC. Manche Formulare verlangen sie neben der IBAN. */
    val VON_6_AUF_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN bic TEXT")
        }
    }

    /**
     * v8: Erinnerungen.
     *
     * Eigene Tabelle, damit eine gestellte Erinnerung den Feed-Abgleich ueberlebt —
     * dabei werden Aktionen ersetzt.
     */
    val VON_7_AUF_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS reminders (
                    actionId TEXT NOT NULL PRIMARY KEY,
                    faelligAm INTEGER NOT NULL,
                    titel TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    /**
     * v9: Kontingent einer Aktion.
     *
     * Viele Anbieter geben nur eine feste Zahl Teilnahmen frei und setzen sie zu
     * einem festen Zeitpunkt zurueck. Wer das nicht weiss, kauft das Produkt und
     * merkt beim Einreichen, dass er zu spaet dran war.
     */
    val VON_8_AUF_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE promo_actions ADD COLUMN limitAnzahl INTEGER")
            db.execSQL("ALTER TABLE promo_actions ADD COLUMN limitZeitraum TEXT")
            db.execSQL("ALTER TABLE promo_actions ADD COLUMN limitReset TEXT")
            db.execSQL(
                "ALTER TABLE promo_actions ADD COLUMN limitErschoepft INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    /**
     * v10: Wiederkehrende Erinnerungen.
     *
     * Eine Erinnerung an eine Frist kommt einmal. Eine an die woechentliche
     * Freischaltung eines Kontingents muss jede Woche kommen.
     */
    val VON_9_AUF_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE reminders ADD COLUMN abstandMillis INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE reminders ADD COLUMN anlass TEXT NOT NULL DEFAULT 'frist'")
        }
    }

    val ALLE = arrayOf(
        VON_1_AUF_2, VON_2_AUF_3, VON_3_AUF_4, VON_4_AUF_5, VON_5_AUF_6, VON_6_AUF_7,
        VON_7_AUF_8, VON_8_AUF_9, VON_9_AUF_10,
    )
}
