package io.github.carsonfiechtner.keepup.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App-wide settings backed by Preferences DataStore. */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.SYSTEM
    }

    /** When the user last exported their data, or null if never. */
    val lastExportAt: Flow<Instant?> = dataStore.data.map { prefs -> prefs[Keys.LAST_EXPORT_AT]?.let(Instant::ofEpochMilli) }

    suspend fun setLastExportAt(instant: Instant) {
        dataStore.edit { it[Keys.LAST_EXPORT_AT] = instant.toEpochMilli() }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_EXPORT_AT = longPreferencesKey("last_export_at")
    }
}
