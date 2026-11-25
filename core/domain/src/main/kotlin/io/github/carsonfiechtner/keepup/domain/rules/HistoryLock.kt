package io.github.carsonfiechtner.keepup.domain.rules

import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.EntryOrigin
import io.github.carsonfiechtner.keepup.domain.model.Entry
import io.github.carsonfiechtner.keepup.domain.model.Goal
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.period.PeriodCalculator
import java.time.LocalDate

/**
 * Integrity rules from design doc §6.5: past days are read-only, and goal changes only apply
 * going forward. The UI mirrors these, but they're enforced where data is written.
 */
object HistoryLock {

    /** New entries can only be dated today. */
    fun canLog(date: LocalDate, today: LocalDate): Boolean = date == today

    /** Only today's manual entries can be edited or deleted. */
    fun canModify(entry: Entry, today: LocalDate): Boolean =
        entry.date == today && entry.origin == EntryOrigin.MANUAL

    /** On the day a habit is created, anything about it can still change (fixing setup mistakes). */
    fun isSetupDay(habit: Habit, today: LocalDate): Boolean = habit.anchorDate == today

    /** Period, unit and data source are fixed after the setup day. */
    fun canChangeStructure(habit: Habit, today: LocalDate): Boolean = isSetupDay(habit, today)

    /** The date from which a goal edit made [today] applies. */
    fun goalEditEffectiveFrom(habit: Habit, today: LocalDate): LocalDate =
        if (isSetupDay(habit, today)) {
            habit.anchorDate
        } else {
            PeriodCalculator.nextPeriodStart(habit.period, habit.anchorDate, today)
        }

    /**
     * Returns the habit's goal list after the user sets the goal to [direction] / [target] on [today].
     * - On the setup day the goal is simply replaced.
     * - Otherwise a new goal version starts at the next period. Any earlier pending edit is replaced,
     *   and an edit back to the goal already in force just cancels the pending one.
     */
    fun applyGoalEdit(habit: Habit, direction: Direction, target: Double, today: LocalDate): List<Goal> {
        val effectiveFrom = goalEditEffectiveFrom(habit, today)
        val edited = Goal(direction, target, effectiveFrom)
        if (isSetupDay(habit, today)) return listOf(edited)

        val kept = habit.goals.filter { it.effectiveFrom.isBefore(effectiveFrom) }
        val inForce = kept.maxByOrNull { it.effectiveFrom }
        return if (inForce != null && inForce.sameAs(edited)) kept else kept + edited
    }
}
