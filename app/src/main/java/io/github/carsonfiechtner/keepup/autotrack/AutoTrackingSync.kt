package io.github.carsonfiechtner.keepup.autotrack

import io.github.carsonfiechtner.keepup.data.db.UsageDailyEntity
import io.github.carsonfiechtner.keepup.data.db.UsageDao
import io.github.carsonfiechtner.keepup.data.prefs.SettingsRepository
import io.github.carsonfiechtner.keepup.data.repo.AppGroupRepository
import io.github.carsonfiechtner.keepup.data.repo.EntryRepository
import io.github.carsonfiechtner.keepup.data.repo.HabitRepository
import io.github.carsonfiechtner.keepup.domain.model.EntryOrigin
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.model.SourceType
import io.github.carsonfiechtner.keepup.domain.rules.HistoryLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls values from automatic sources into entries (design doc §10.1). Each habit gets at most one
 * entry per day per source, keyed so re-syncing replaces rather than adds. Only days inside the
 * settle window ([HistoryLock.canAutoUpdate]) are written; older days stay frozen.
 */
@Singleton
class AutoTrackingSync @Inject constructor(
    private val habits: HabitRepository,
    private val entries: EntryRepository,
    private val steps: StepsSource,
    private val usage: UsageSource,
    private val usageDao: UsageDao,
    private val appGroups: AppGroupRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Returns true if every source that's in use could be read. */
    suspend fun syncAll(): Boolean = mutex.withLock {
        val today = LocalDate.now(clock)
        val active = habits.activeHabits.first().filter { it.source.isAutomatic && !it.anchorDate.isAfter(today) }
        if (active.isEmpty()) return@withLock true
        var ok = true

        val stepHabits = active.filter { it.source.type == SourceType.HEALTH_STEPS }
        if (stepHabits.isNotEmpty()) {
            ok = syncSteps(stepHabits, today) && ok
        }
        val usageHabits = active.filter {
            it.source.type == SourceType.USAGE_SCREEN_TIME || it.source.type == SourceType.USAGE_APP_GROUP
        }
        if (usageHabits.isNotEmpty()) {
            ok = syncUsage(usageHabits, today) && ok
        }
        settings.setLastAutoSyncAt(clock.instant())
        ok
    }

    private suspend fun syncSteps(stepHabits: List<Habit>, today: LocalDate): Boolean {
        if (steps.status() != SourceStatus.READY) return false
        val from = stepHabits.minOf { window(it, today).first() }
        val values = try {
            steps.dailySteps(from, today)
        } catch (_: Exception) {
            // Background reads without permission, Health Connect busy, etc. Try again next time.
            return false
        }
        val now = clock.instant()
        stepHabits.forEach { habit ->
            for (date in window(habit, today)) {
                val value = values[date] ?: continue
                entries.upsertAuto(habit.id, date, value.toDouble(), EntryOrigin.HEALTH_CONNECT, "hc:steps:$date", now)
            }
        }
        return true
    }

    private suspend fun syncUsage(usageHabits: List<Habit>, today: LocalDate): Boolean {
        if (!usage.hasAccess()) return false
        val from = usageHabits.minOf { window(it, today).first() }
        val measured = try {
            usage.foregroundByDay(from, today)
        } catch (_: Exception) {
            return false
        }
        // Save per-app totals first: Android discards raw events after a few days.
        usageDao.upsert(
            measured.flatMap { (date, perApp) -> perApp.map { (pkg, ms) -> UsageDailyEntity(date, pkg, ms) } },
        )
        val saved = usageDao.between(from, today).groupBy { it.date }
        val excluded = usage.excludedFromScreenTime()
        val now = clock.instant()
        usageHabits.forEach { habit ->
            val group = if (habit.source.type == SourceType.USAGE_APP_GROUP) {
                habit.source.appGroupId?.let { appGroups.get(it) } ?: return@forEach
            } else {
                null
            }
            val counts: (String) -> Boolean = if (group != null) {
                { pkg -> pkg in group.packages }
            } else {
                { pkg -> pkg !in excluded }
            }
            val keyPrefix = if (group != null) "usage:group:${group.id}" else "usage:total"
            for (date in window(habit, today)) {
                val ms = saved[date].orEmpty().filter { counts(it.packageName) }.sumOf { it.foregroundMs }
                // Access was granted and the query succeeded, so no usage really is zero minutes.
                entries.upsertAuto(habit.id, date, ms / 60_000.0, EntryOrigin.USAGE_STATS, "$keyPrefix:$date", now)
            }
        }
        return true
    }

    /** Days this habit may still receive values: its start date or the settle window, through today. */
    private fun window(habit: Habit, today: LocalDate): List<LocalDate> {
        val first = maxOf(habit.anchorDate, today.minusDays(HistoryLock.AUTO_SETTLE_DAYS))
        return generateSequence(first) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .filter { HistoryLock.canAutoUpdate(it, today) }
            .toList()
    }
}
