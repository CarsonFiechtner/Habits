package io.github.carsonfiechtner.keepup.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.carsonfiechtner.keepup.autotrack.AutoTrackingSync
import io.github.carsonfiechtner.keepup.data.repo.EntryRepository
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.data.repo.HabitRepository
import io.github.carsonfiechtner.keepup.domain.reminder.ReminderPlanner
import io.github.carsonfiechtner.keepup.domainops.HabitActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Fires the nightly reminder, handles its "Done" action, and re-arms the alarm after reboots and clock changes. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var entries: EntryRepository
    @Inject lateinit var actions: HabitActions
    @Inject lateinit var clock: Clock
    @Inject lateinit var autoSync: AutoTrackingSync

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> {
                        fire()
                        scheduler.reschedule()
                    }
                    ACTION_MARK_DONE -> {
                        val id = intent.getLongExtra(EXTRA_HABIT_ID, -1)
                        if (id > 0) actions.markDone(id)
                        notifier.cancel()
                    }
                    else -> scheduler.reschedule() // boot, time / time zone change, app update
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire() {
        autoSync.syncAll() // so step counts are current
        val today = LocalDate.now(clock)
        val items = ReminderPlanner.items(
            habits = habits.activeHabits.first(),
            totals = entries.dailyTotals.first(),
            today = today,
            // Nothing to do about an automatic limit (e.g. screen time) except not exceed it.
            isPassive = { it.source.isAutomatic && it.goalFor(today).direction == Direction.AT_MOST },
        )
        notifier.show(items)
    }

    companion object {
        const val ACTION_FIRE = "io.github.carsonfiechtner.keepup.REMINDER_FIRE"
        const val ACTION_MARK_DONE = "io.github.carsonfiechtner.keepup.REMINDER_MARK_DONE"
        const val EXTRA_HABIT_ID = "habit_id"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
