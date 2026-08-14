package de.gzgtracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AccountEntity::class,
        PromoActionEntity::class,
        SubmissionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GzgDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao

    abstract fun promoActionDao(): PromoActionDao

    abstract fun submissionDao(): SubmissionDao

    companion object {
        const val NAME = "gzg-tracker.db"
    }
}
