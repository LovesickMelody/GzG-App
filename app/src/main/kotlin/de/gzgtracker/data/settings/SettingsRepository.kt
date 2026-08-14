package de.gzgtracker.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import de.gzgtracker.BuildConfig
import de.gzgtracker.core.DuplicateAccountRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "einstellungen")

/** Alles, was der Nutzer in den Einstellungen umstellen kann. */
data class Settings(
    val duplicateRule: DuplicateAccountRule = DuplicateAccountRule.DEFAULT,
    val feedUrl: String = BuildConfig.ACTIONS_FEED_URL,
    val lastSyncAt: Instant? = null,
    val autoSyncBeimStart: Boolean = true,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val settings: Flow<Settings> = context.settingsDataStore.data.map { prefs ->
        Settings(
            duplicateRule = prefs[Keys.DUPLICATE_RULE]
                ?.let { gespeichert ->
                    runCatching { DuplicateAccountRule.valueOf(gespeichert) }.getOrNull()
                }
                ?: DuplicateAccountRule.DEFAULT,
            feedUrl = prefs[Keys.FEED_URL]?.takeIf { it.isNotBlank() }
                ?: BuildConfig.ACTIONS_FEED_URL,
            lastSyncAt = prefs[Keys.LAST_SYNC_AT]?.let(Instant::ofEpochMilli),
            autoSyncBeimStart = prefs[Keys.AUTO_SYNC] ?: true,
        )
    }

    suspend fun setzeDuplicateRule(rule: DuplicateAccountRule) {
        context.settingsDataStore.edit { it[Keys.DUPLICATE_RULE] = rule.name }
    }

    suspend fun setzeFeedUrl(url: String) {
        context.settingsDataStore.edit { prefs ->
            val bereinigt = url.trim()
            if (bereinigt.isEmpty()) prefs.remove(Keys.FEED_URL) else prefs[Keys.FEED_URL] = bereinigt
        }
    }

    suspend fun setzeAutoSync(aktiv: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_SYNC] = aktiv }
    }

    suspend fun merkeSync(zeitpunkt: Instant) {
        context.settingsDataStore.edit { it[Keys.LAST_SYNC_AT] = zeitpunkt.toEpochMilli() }
    }

    private object Keys {
        val DUPLICATE_RULE: Preferences.Key<String> = stringPreferencesKey("duplicate_rule")
        val FEED_URL: Preferences.Key<String> = stringPreferencesKey("feed_url")
        val LAST_SYNC_AT: Preferences.Key<Long> = longPreferencesKey("last_sync_at")
        val AUTO_SYNC: Preferences.Key<Boolean> = booleanPreferencesKey("auto_sync")
    }
}
