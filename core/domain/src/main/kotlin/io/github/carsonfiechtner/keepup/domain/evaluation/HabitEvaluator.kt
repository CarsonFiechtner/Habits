package io.github.carsonfiechtner.keepup.domain.evaluation

import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.period.PeriodCalculator
import io.github.carsonfiechtner.keepup.domain.period.PeriodRange
import java.time.LocalDate

/**
 * Per-day totals for one habit. A key is present iff the day has at least one entry, even one
 * with value 0 ("Log 0"), which is how "no data" is told apart from "zero".
 */
typealias DailyTotals = Map<LocalDate, Double>

/** Status rules from design doc §6.2. */
object HabitEvaluator {

    fun evaluate(habit: Habit, range: PeriodRange, dailyTotals: DailyTotals, today: LocalDate): PeriodResult {
        val goal = habit.goalFor(range.start)
        if (range.start.isAfter(today)) {
            return PeriodResult(range, goal, 0.0, hasData = false, status = PeriodStatus.NOT_SCHEDULED)
        }

        var sum = 0.0
        var hasData = false
        var day = range.start
        while (!day.isAfter(range.endInclusive)) {
            dailyTotals[day]?.let {
                sum += it
                hasData = true
            }
            day = day.plusDays(1)
        }

        val decimals = habit.unit.decimals
        val value = ValueMath.round(sum, decimals)
        val target = ValueMath.round(goal.target, decimals)
        val isCurrent = today in range

        val status = when (goal.direction) {
            Direction.AT_LEAST -> when {
                hasData && value >= target -> PeriodStatus.COMPLETED
                isCurrent -> PeriodStatus.IN_PROGRESS
                else -> PeriodStatus.MISSED
            }
            Direction.AT_MOST -> when {
                value > target -> PeriodStatus.MISSED
                isCurrent -> PeriodStatus.IN_PROGRESS
                hasData -> PeriodStatus.COMPLETED
                else -> PeriodStatus.MISSED
            }
        }
        return PeriodResult(range, goal, value, hasData, status)
    }

    /** Evaluates the period that contains [date], or returns null if the habit didn't exist yet. */
    fun evaluateOn(habit: Habit, date: LocalDate, dailyTotals: DailyTotals, today: LocalDate): PeriodResult? =
        PeriodCalculator.periodContaining(habit.period, habit.anchorDate, date)
            ?.let { evaluate(habit, it, dailyTotals, today) }
}
