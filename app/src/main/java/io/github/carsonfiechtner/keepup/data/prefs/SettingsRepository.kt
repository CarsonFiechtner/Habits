package io.github.carsonfiechtner.keepup.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalTime
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

    /** When automatic tracking last synced successfully. */
    val lastAutoSyncAt: Flow<Instant?> = dataStore.data.map { prefs -> prefs[Keys.LAST_AUTO_SYNC_AT]?.let(Instant::ofEpochMilli) }

    suspend fun setLastAutoSyncAt(instant: Instant) {
        dataStore.edit { it[Keys.LAST_AUTO_SYNC_AT] = instant.toEpochMilli() }
    }

    /** Nightly reminder (design doc §11.1): on by default at 21:00. */
    val reminderEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.REMINDER_ENABLED] ?: true }

    val reminderTime: Flow<LocalTime> = dataStore.data.map { prefs ->
        LocalTime.ofSecondOfDay(((prefs[Keys.REMINDER_MINUTE] ?: DEFAULT_REMINDER_MINUTE) * 60).toLong())
    }

    /** Whether we've already asked for the notification permission once. */
    val notificationPermissionAsked: Flow<Boolean> = dataStore.data.map { it[Keys.NOTIFICATIONS_ASKED] ?: false }

    suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderTime(time: LocalTime) {
        dataStore.edit { it[Keys.REMINDER_MINUTE] = time.hour * 60 + time.minute }
    }

    suspend fun setNotificationPermissionAsked() {
        dataStore.edit { it[Keys.NOTIFICATIONS_ASKED] = true }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    private companion object {
        const val DEFAULT_REMINDER_MINUTE = 21 * 60
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_EXPORT_AT = longPreferencesKey("last_export_at")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val LAST_AUTO_SYNC_AT = longPreferencesKey("last_auto_sync_at")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val NOTIFICATIONS_ASKED = booleanPreferencesKey("notifications_asked")
    }
}
