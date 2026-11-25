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

    /**
     * Until a habit's first day is over (its start date is today or still in the future), anything
     * about it can change, including the start date itself. This is for fixing setup mistakes.
     */
    fun isInSetup(habit: Habit, today: LocalDate): Boolean = !today.isAfter(habit.anchorDate)

    /** Period, unit, data source and start date are fixed once the first day is over. */
    fun canChangeStructure(habit: Habit, today: LocalDate): Boolean = isInSetup(habit, today)

    /** A start date can be today or any later day. */
    fun isValidStartDate(startDate: LocalDate, today: LocalDate): Boolean = !startDate.isBefore(today)

    /** The date from which a goal edit made [today] applies. */
    fun goalEditEffectiveFrom(habit: Habit, today: LocalDate): LocalDate =
        if (isInSetup(habit, today)) {
            habit.anchorDate
        } else {
            PeriodCalculator.nextPeriodStart(habit.period, habit.anchorDate, today)
        }

    /**
     * Returns the habit's goal list after the user sets the goal to [direction] / [target] on [today].
     * - During setup (see [isInSetup]) the goal is simply replaced, effective from the start date.
     * - Otherwise a new goal version starts at the next period. Any earlier pending edit is replaced,
     *   and an edit back to the goal already in force just cancels the pending one.
     */
    fun applyGoalEdit(habit: Habit, direction: Direction, target: Double, today: LocalDate): List<Goal> {
        val effectiveFrom = goalEditEffectiveFrom(habit, today)
        val edited = Goal(direction, target, effectiveFrom)
        if (isInSetup(habit, today)) return listOf(edited)

        val kept = habit.goals.filter { it.effectiveFrom.isBefore(effectiveFrom) }
        val inForce = kept.maxByOrNull { it.effectiveFrom }
        return if (inForce != null && inForce.sameAs(edited)) kept else kept + edited
    }
}
