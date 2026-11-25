package io.github.carsonfiechtner.keepup.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.carsonfiechtner.keepup.MainActivity
import io.github.carsonfiechtner.keepup.R
import io.github.carsonfiechtner.keepup.domain.reminder.ReminderItem
import io.github.carsonfiechtner.keepup.domain.reminder.ReminderReason
import io.github.carsonfiechtner.keepup.ui.common.HabitText
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Nightly reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "One evening notification listing goals you haven't met yet"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Posts (or replaces) the reminder. Does nothing if [items] is empty or notifications are off. */
    fun show(items: List<ReminderItem>) {
        if (items.isEmpty() || !canNotify()) return
        ensureChannel()
        val title = if (items.size == 1) "1 goal left today" else "${items.size} goals left today"
        val lines = items.map(::line)
        val style = NotificationCompat.InboxStyle().also { inbox ->
            lines.take(MAX_LINES).forEach(inbox::addLine)
            if (lines.size > MAX_LINES) inbox.setSummaryText("+${lines.size - MAX_LINES} more")
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.joinToString(" · "))
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        // One-tap "Done" when exactly one done/not-done habit is left.
        val single = items.singleOrNull()
        if (single != null && single.habit.unit.isBinary && single.reason == ReminderReason.NOT_DONE) {
            val done = PendingIntent.getBroadcast(
                context,
                single.habit.id.toInt(),
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ReminderReceiver.ACTION_MARK_DONE)
                    .putExtra(ReminderReceiver.EXTRA_HABIT_ID, single.habit.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Done", done)
        }
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    fun cancel() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun line(item: ReminderItem): String {
        val habit = item.habit
        val progress = HabitText.progress(item.result.value, item.result.goal, habit.unit)
        return when (item.reason) {
            ReminderReason.NOT_DONE -> if (habit.unit.isBinary) "${habit.icon} ${habit.name}" else "${habit.icon} ${habit.name} $progress"
            ReminderReason.NEEDS_LOG -> "${habit.icon} ${habit.name}: log today's amount (or 0)"
            ReminderReason.PERIOD_ENDING -> "${habit.icon} ${habit.name} $progress, tomorrow is the last day"
        }
    }

    companion object {
        const val CHANNEL_ID = "nightly_reminder"
        const val NOTIFICATION_ID = 21
        private const val MAX_LINES = 6
    }
}
