package io.github.carsonfiechtner.keepup

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.carsonfiechtner.keepup.autotrack.SyncWorker
import io.github.carsonfiechtner.keepup.reminders.ReminderNotifier
import io.github.carsonfiechtner.keepup.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KeepUpApplication : Application() {

    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var reminderNotifier: ReminderNotifier

    /** Lives as long as the process; for short fire-and-forget work at startup. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        reminderNotifier.ensureChannel()
        appScope.launch { reminderScheduler.reschedule() }
        SyncWorker.schedule(this)
    }
}
