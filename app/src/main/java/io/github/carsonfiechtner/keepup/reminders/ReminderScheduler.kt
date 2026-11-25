package io.github.carsonfiechtner.keepup.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.carsonfiechtner.keepup.data.prefs.SettingsRepository
import io.github.carsonfiechtner.keepup.domain.reminder.ReminderPlanner
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps exactly one alarm registered: the next nightly reminder. Called on app start, when the
 * setting changes, after each reminder fires, and after reboot / time or time-zone changes.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    // USE_EXACT_ALARM is declared in the manifest (granted at install for this sideloaded app) and
    // canScheduleExactAlarms() is checked before the exact call; lint can't see either.
    @SuppressLint("MissingPermission")
    suspend fun reschedule() {
        val intent = pendingIntent()
        alarmManager.cancel(intent)
        if (!settings.reminderEnabled.first()) return
        val time = settings.reminderTime.first()
        val at = ReminderPlanner.nextTrigger(ZonedDateTime.now(clock), time).toInstant().toEpochMilli()
        try {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
                return
            }
        } catch (_: SecurityException) {
            // Fall through to an inexact alarm.
        }
        // Can be a few minutes late, but still fires in Doze.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 2100
    }
}
