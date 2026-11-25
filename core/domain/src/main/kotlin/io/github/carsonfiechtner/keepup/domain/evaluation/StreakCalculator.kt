package io.github.carsonfiechtner.keepup.domain.evaluation

import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.period.PeriodCalculator
import java.time.LocalDate

data class Streaks(
    /** Consecutive completed periods up to now. An in-progress current period doesn't break it. */
    val current: Int,
    val best: Int,
)

/** Strict streaks (design doc §6.3): a missed period ends the streak; no skips or freezes. */
object StreakCalculator {

    fun compute(habit: Habit, dailyTotals: DailyTotals, today: LocalDate): Streaks {
        val currentPeriod = PeriodCalculator.periodContaining(habit.period, habit.anchorDate, today)
            ?: return Streaks(0, 0)

        var run = 0
        var best = 0
        for (index in 0..currentPeriod.index) {
            val range = PeriodCalculator.periodAt(habit.period, habit.anchorDate, index)
            when (HabitEvaluator.evaluate(habit, range, dailyTotals, today).status) {
                PeriodStatus.COMPLETED -> {
                    run++
                    if (run > best) best = run
                }
                PeriodStatus.MISSED -> run = 0
                PeriodStatus.IN_PROGRESS, PeriodStatus.NOT_SCHEDULED -> Unit
            }
        }
        return Streaks(current = run, best = best)
    }
}
