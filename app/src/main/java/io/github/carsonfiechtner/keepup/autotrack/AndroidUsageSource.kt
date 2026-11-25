package io.github.carsonfiechtner.keepup.autotrack

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.carsonfiechtner.keepup.domain.usage.UsageAggregator
import io.github.carsonfiechtner.keepup.domain.usage.UsageEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidUsageSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : UsageSource {

    // unsafeCheckOpNoThrow is deprecated in API 37 but remains the documented way to check this app op.
    @Suppress("DEPRECATION")
    override fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun foregroundByDay(from: LocalDate, to: LocalDate): Map<LocalDate, Map<String, Long>> =
        withContext(Dispatchers.IO) {
            val zone = clock.zone
            val start = from.atStartOfDay(zone).toInstant()
            val end = minOf(clock.instant(), to.plusDays(1).atStartOfDay(zone).toInstant())
            // Look back a little so an app already open at `start` is seen resuming.
            val queryStart = start.minus(LOOKBACK)
            val manager = context.getSystemService(UsageStatsManager::class.java)
            val raw = manager.queryEvents(queryStart.toEpochMilli(), end.toEpochMilli())
            val events = mutableListOf<UsageEvent>()
            val event = UsageEvents.Event()
            while (raw.hasNextEvent()) {
                raw.getNextEvent(event)
                val type = when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvent.Type.RESUMED
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> UsageEvent.Type.PAUSED
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                    UsageEvents.Event.KEYGUARD_SHOWN,
                    UsageEvents.Event.DEVICE_SHUTDOWN,
                    -> UsageEvent.Type.SCREEN_OFF
                    else -> null
                } ?: continue
                events += UsageEvent(event.packageName, Instant.ofEpochMilli(event.timeStamp), type)
            }
            // Count from queryStart, then drop anything attributed to days before `from`.
            UsageAggregator.foregroundByDay(events, zone, queryStart, end).filterKeys { !it.isBefore(from) }
        }

    override fun excludedFromScreenTime(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = context.packageManager
            .queryIntentActivities(home, PackageManager.ResolveInfoFlags.of(0))
            .map { it.activityInfo.packageName }
        return launchers.toSet() + "com.android.systemui"
    }

    override fun launchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private companion object {
        val LOOKBACK: Duration = Duration.ofHours(3)
    }
}
