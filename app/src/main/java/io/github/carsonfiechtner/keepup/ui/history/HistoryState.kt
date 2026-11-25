package io.github.carsonfiechtner.keepup.ui.history

import io.github.carsonfiechtner.keepup.domain.evaluation.DailyTotals
import io.github.carsonfiechtner.keepup.domain.evaluation.HabitEvaluator
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodResult
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakCalculator
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.domain.period.PeriodCalculator
import java.time.LocalDate

data class OverviewRow(
    val habit: Habit,
    /** Daily habits: one cell per day in [HistoryState.days]. Others: the last few periods, oldest first. */
    val cells: List<PeriodStatus?>,
    val current: PeriodResult?,
    val streak: Int,
)

/** Share of daily habits completed on one day; null when nothing was decided that day. */
data class DayCompletion(val date: LocalDate, val ratio: Float?)

data class HistoryState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.MIN,
    val days: List<LocalDate> = emptyList(),
    val daily: List<OverviewRow> = emptyList(),
    val periodic: List<OverviewRow> = emptyList(),
    val trend: List<DayCompletion> = emptyList(),
)

object HistoryStateBuilder {
    const val GRID_DAYS = 7
    const val PERIOD_CELLS = 6
    const val TREND_DAYS = 14

    fun build(habits: List<Habit>, totals: Map<Long, DailyTotals>, today: LocalDate): HistoryState {
        val started = habits.filter { !it.anchorDate.isAfter(today) }
        val days = (GRID_DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val rows = started.sortedWith(compareBy({ it.sortOrder }, { it.id })).map { habit ->
            val t = totals[habit.id].orEmpty()
            val cells = if (habit.period == Period.DAILY) {
                days.map { HabitEvaluator.evaluateOn(habit, it, t, today)?.status }
            } else {
                val current = PeriodCalculator.periodContaining(habit.period, habit.anchorDate, today)!!
                (PERIOD_CELLS - 1 downTo 0).map { back ->
                    val index = current.index - back
                    if (index < 0) {
                        null
                    } else {
                        HabitEvaluator.evaluate(habit, PeriodCalculator.periodAt(habit.period, habit.anchorDate, index), t, today).status
                    }
                }
            }
            OverviewRow(
                habit = habit,
                cells = cells,
                current = HabitEvaluator.evaluateOn(habit, today, t, today),
                streak = StreakCalculator.compute(habit, t, today).current,
            )
        }
        val dailyHabits = started.filter { it.period == Period.DAILY }
        val trend = (TREND_DAYS - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val statuses = dailyHabits.mapNotNull { HabitEvaluator.evaluateOn(it, date, totals[it.id].orEmpty(), today)?.status }
            val completed = statuses.count { it == PeriodStatus.COMPLETED }
            val decided = completed + statuses.count { it == PeriodStatus.MISSED }
            DayCompletion(date, if (decided == 0) null else completed.toFloat() / decided)
        }
        return HistoryState(
            loading = false,
            today = today,
            days = days,
            daily = rows.filter { it.habit.period == Period.DAILY },
            periodic = rows.filter { it.habit.period != Period.DAILY },
            trend = trend,
        )
    }
}
