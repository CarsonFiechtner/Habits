package io.github.carsonfiechtner.keepup

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.carsonfiechtner.keepup.autotrack.SyncWorker
import io.github.carsonfiechtner.keepup.data.repo.EntryRepository
import io.github.carsonfiechtner.keepup.data.repo.HabitRepository
import io.github.carsonfiechtner.keepup.widget.KeepUpWidget
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import io.github.carsonfiechtner.keepup.reminders.ReminderNotifier
import io.github.carsonfiechtner.keepup.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltAndroidApp
class KeepUpApplication : Application() {

    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var reminderNotifier: ReminderNotifier
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var entries: EntryRepository

    /** Lives as long as the process; for short fire-and-forget work at startup. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        reminderNotifier.ensureChannel()
        appScope.launch { reminderScheduler.reschedule() }
        SyncWorker.schedule(this)
        // Keep home-screen widgets in step with any change made while the app process is alive.
        appScope.launch {
            combine(habits.activeHabits, entries.dailyTotals) { _, _ -> }
                .debounce(500)
                .collect { KeepUpWidget.refresh(this@KeepUpApplication) }
        }
    }
}
