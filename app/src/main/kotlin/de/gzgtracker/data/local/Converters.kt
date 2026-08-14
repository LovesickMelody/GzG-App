package de.gzgtracker.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/**
 * Room speichert Datumsangaben als ISO-Text und Zeitpunkte als Epochenmillis.
 * Listen werden mit einem Trennzeichen zusammengelegt, das in Haendlernamen und
 * EANs nicht vorkommt (US, ASCII 31).
 */
class Converters {

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? =
        value?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)

    @TypeConverter
    fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun stringListToString(value: List<String>?): String =
        value.orEmpty().joinToString(SEPARATOR)

    @TypeConverter
    fun stringToStringList(value: String?): List<String> =
        value?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val SEPARATOR = "\u001F"
    }
}
