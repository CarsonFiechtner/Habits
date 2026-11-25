package io.github.carsonfiechtner.keepup.autotrack

import io.github.carsonfiechtner.keepup.data.prefs.SettingsRepository
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

    /** Days this habit may still receive values: its start date or the settle window, through today. */
    private fun window(habit: Habit, today: LocalDate): List<LocalDate> {
        val first = maxOf(habit.anchorDate, today.minusDays(HistoryLock.AUTO_SETTLE_DAYS))
        return generateSequence(first) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .filter { HistoryLock.canAutoUpdate(it, today) }
            .toList()
    }
}
