package io.github.carsonfiechtner.keepup.domainops

import io.github.carsonfiechtner.keepup.data.repo.EntryRepository
import io.github.carsonfiechtner.keepup.data.repo.HabitRepository
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.Entry
import io.github.carsonfiechtner.keepup.domain.model.Goal
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.domain.rules.HistoryLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when a write would change locked history (design doc §6.5). */
class HistoryLockedException(message: String) : IllegalStateException(message)

/** What the habit form submits. */
data class HabitDraft(
    val id: Long?,
    val name: String,
    val description: String?,
    val icon: String,
    val color: Long,
    val period: Period,
    val unitId: Long,
    val direction: Direction,
    val target: Double,
)

data class SaveResult(
    val habitId: Long,
    /** When the goal change applies, or null if the goal didn't change. */
    val goalEffectiveFrom: LocalDate?,
)

/**
 * Every user-initiated write goes through here, so the history-lock rules are enforced in one
 * place regardless of what the UI allows.
 */
@Singleton
class HabitActions @Inject constructor(
    private val habits: HabitRepository,
    private val entries: EntryRepository,
    private val clock: Clock,
) {
    private fun today(): LocalDate = LocalDate.now(clock)

    private suspend fun requireHabit(habitId: Long): Habit =
        habits.getHabit(habitId) ?: throw IllegalArgumentException("No habit $habitId")

    private fun requireLoggable(habit: Habit, today: LocalDate) {
        if (habit.isArchived) throw HistoryLockedException("${habit.name} is archived")
        if (today.isBefore(habit.anchorDate)) throw HistoryLockedException("${habit.name} hasn't started yet")
    }

    /** Adds a manual entry for today. */
    suspend fun logValue(habitId: Long, value: Double, note: String? = null): Long {
        require(value >= 0 && value.isFinite()) { "value must be a non-negative number" }
        val today = today()
        val habit = requireHabit(habitId)
        requireLoggable(habit, today)
        check(HistoryLock.canLog(today, today))
        return entries.insert(habitId, today, clock.instant(), value, note?.takeIf { it.isNotBlank() })
    }

    /** "Log 0": explicitly records a zero day for an "at most" habit. */
    suspend fun logZero(habitId: Long): Long = logValue(habitId, 0.0)

    /** Binary habits: marks today done, or undoes it if it already is. Returns the new state. */
    suspend fun toggleDone(habitId: Long): Boolean {
        val today = today()
        val habit = requireHabit(habitId)
        requireLoggable(habit, today)
        val todays = entries.getOn(habitId, today).filter { HistoryLock.canModify(it, today) }
        return if (todays.isEmpty()) {
            entries.insert(habitId, today, clock.instant(), 1.0, null)
            true
        } else {
            todays.forEach { entries.delete(it.id) }
            false
        }
    }

    suspend fun editEntry(entryId: Long, value: Double, note: String?) {
        require(value >= 0 && value.isFinite()) { "value must be a non-negative number" }
        val entry = entries.get(entryId) ?: return
        if (!HistoryLock.canModify(entry, today())) throw HistoryLockedException("Past entries can't be edited")
        entries.updateValue(entry, value, note?.takeIf { it.isNotBlank() })
    }

    /** Deletes one of today's entries and returns it so the UI can offer undo. */
    suspend fun deleteEntry(entryId: Long): Entry? {
        val entry = entries.get(entryId) ?: return null
        if (!HistoryLock.canModify(entry, today())) throw HistoryLockedException("Past entries can't be deleted")
        entries.delete(entryId)
        return entry
    }

    /** Undo for [deleteEntry]. Only possible while the entry's day is still today. */
    suspend fun restoreEntry(entry: Entry): Boolean {
        if (!HistoryLock.canLog(entry.date, today())) return false
        entries.insert(entry.habitId, entry.date, entry.loggedAt, entry.value, entry.note, entry.origin)
        return true
    }

    /** Undo for a quick log: removes the entry if it's still editable. */
    suspend fun undoLog(entryId: Long) {
        val entry = entries.get(entryId) ?: return
        if (HistoryLock.canModify(entry, today())) entries.delete(entryId)
    }

    suspend fun saveHabit(draft: HabitDraft): SaveResult {
        val today = today()
        val name = draft.name.trim()
        require(name.isNotEmpty()) { "Name is required" }
        val unit = habits.getUnit(draft.unitId) ?: throw IllegalArgumentException("No unit ${draft.unitId}")

        // Binary daily habits always mean "done today".
        val (direction, target) =
            if (unit.isBinary && draft.period == Period.DAILY) Direction.AT_LEAST to 1.0 else draft.direction to draft.target
        require(target.isFinite() && target >= 0) { "Target must be a non-negative number" }
        require(direction == Direction.AT_MOST || target > 0) { "An \"at least\" target must be above 0" }

        if (draft.id == null) {
            val habit = Habit(
                id = 0,
                name = name,
                description = draft.description?.trim()?.takeIf { it.isNotEmpty() },
                icon = draft.icon,
                color = draft.color,
                period = draft.period,
                anchorDate = today,
                unit = unit,
                goals = listOf(Goal(direction, target, today)),
                archivedAt = null,
                sortOrder = habits.nextSortOrder(),
                createdAt = clock.instant(),
            )
            return SaveResult(habits.insert(habit), goalEffectiveFrom = null)
        }

        val existing = requireHabit(draft.id)
        val structureChanged = existing.period != draft.period || existing.unit.id != unit.id
        if (structureChanged && !HistoryLock.canChangeStructure(existing, today)) {
            throw HistoryLockedException("Period and unit can only change on the day a habit is created")
        }
        val withStructure = existing.copy(period = draft.period, unit = unit)

        val currentGoal = existing.pendingGoal(today) ?: existing.goalFor(today)
        val goalChanged = currentGoal.direction != direction || currentGoal.target != target || structureChanged
        val goals = if (goalChanged) HistoryLock.applyGoalEdit(withStructure, direction, target, today) else existing.goals

        habits.update(
            withStructure.copy(
                name = name,
                description = draft.description?.trim()?.takeIf { it.isNotEmpty() },
                icon = draft.icon,
                color = draft.color,
                goals = goals,
            ),
        )
        return SaveResult(
            habitId = existing.id,
            goalEffectiveFrom = if (goalChanged) HistoryLock.goalEditEffectiveFrom(existing, today) else null,
        )
    }

    suspend fun archive(habitId: Long) = habits.setArchivedAt(habitId, clock.instant())

    suspend fun unarchive(habitId: Long) = habits.setArchivedAt(habitId, null)

    suspend fun delete(habitId: Long) = habits.delete(habitId)
}
