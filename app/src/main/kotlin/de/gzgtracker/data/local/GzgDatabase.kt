package de.gzgtracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AccountEntity::class,
        PromoActionEntity::class,
        SubmissionEntity::class,
        WatchlistEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GzgDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao

    abstract fun promoActionDao(): PromoActionDao

    abstract fun submissionDao(): SubmissionDao

    abstract fun watchlistDao(): WatchlistDao

    companion object {
        const val NAME = "gzg-tracker.db"
    }
}
