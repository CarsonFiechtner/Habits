package io.github.carsonfiechtner.keepup.domain.evaluation

import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.domain.period.PeriodCalculator
import java.time.LocalDate

/** Share of decided periods that were completed. */
data class CompletionRate(val completed: Int, val decided: Int) {
    /** 0..1, or null when no period has been decided yet. */
    val ratio: Float? get() = if (decided == 0) null else completed.toFloat() / decided
}

/** Summary used by the habit detail screen (design doc I1). */
data class HabitStats(
    val streaks: Streaks,
    /** Over the last [recentWindow] periods (30 days / 12 weeks / 12 months). */
    val recent: CompletionRate,
    val recentWindow: Int,
    val allTime: CompletionRate,
)

object HabitHistory {

    /**
     * Every period of the habit from its start up to the one containing [asOf], oldest first.
     * [asOf] is today for active habits, or the archive date for archived ones.
     */
    fun periods(habit: Habit, dailyTotals: DailyTotals, asOf: LocalDate): List<PeriodResult> {
        val current = PeriodCalculator.periodContaining(habit.period, habit.anchorDate, asOf) ?: return emptyList()
        return (0..current.index).map { index ->
            HabitEvaluator.evaluate(habit, PeriodCalculator.periodAt(habit.period, habit.anchorDate, index), dailyTotals, asOf)
        }
    }

    /**
     * Completion over [results]: completed / (completed + missed). An in-progress period only counts
     * once it's completed, so an unfinished today doesn't drag the rate down.
     */
    fun completionRate(results: List<PeriodResult>): CompletionRate {
        val completed = results.count { it.status == PeriodStatus.COMPLETED }
        val missed = results.count { it.status == PeriodStatus.MISSED }
        return CompletionRate(completed, completed + missed)
    }

    /** Number of recent periods used for the "recent" completion rate. */
    fun recentWindow(habit: Habit): Int = when (habit.period) {
        Period.DAILY -> 30
        Period.WEEKLY -> 12
        Period.MONTHLY -> 12
    }

    fun stats(habit: Habit, dailyTotals: DailyTotals, asOf: LocalDate): HabitStats {
        val all = periods(habit, dailyTotals, asOf)
        val window = recentWindow(habit)
        return HabitStats(
            streaks = StreakCalculator.compute(habit, dailyTotals, asOf),
            recent = completionRate(all.takeLast(window)),
            recentWindow = window,
            allTime = completionRate(all),
        )
    }
}
