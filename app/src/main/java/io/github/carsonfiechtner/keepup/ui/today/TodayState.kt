package io.github.carsonfiechtner.keepup.ui.today

import io.github.carsonfiechtner.keepup.domain.evaluation.DailyTotals
import io.github.carsonfiechtner.keepup.domain.evaluation.HabitEvaluator
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodResult
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakCalculator
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.period.Period
import java.time.LocalDate

/** The one-tap action shown at the end of a habit row. */
enum class RowAction { TOGGLE, QUICK_ADD, LOG_ZERO, NONE }

data class HabitRowState(
    val habit: Habit,
    /** The period containing the viewed date. */
    val result: PeriodResult,
    val streak: Int,
    /** Days left after today in the current weekly/monthly period; null for daily habits or closed periods. */
    val daysLeft: Long?,
    /** Total logged on the viewed date, or null if nothing was logged that day. */
    val dayTotal: Double?,
    val action: RowAction,
) {
    val isDoneOnDate: Boolean get() = habit.unit.isBinary && (dayTotal ?: 0.0) > 0
}

data class TodayUiState(
    val date: LocalDate,
    val today: LocalDate,
    val daily: List<HabitRowState>,
    val periodic: List<HabitRowState>,
    /** At least one active habit exists (it may not have started by [date]). */
    val hasHabits: Boolean,
    val loading: Boolean = false,
) {
    val isToday: Boolean get() = date == today

    companion object {
        fun loading(today: LocalDate) = TodayUiState(today, today, emptyList(), emptyList(), hasHabits = false, loading = true)
    }
}

object TodayStateBuilder {

    fun build(
        habits: List<Habit>,
        totals: Map<Long, DailyTotals>,
        date: LocalDate,
        today: LocalDate,
    ): TodayUiState {
        val rows = habits.mapNotNull { habit -> row(habit, totals[habit.id].orEmpty(), date, today) }
        val ordered = rows.sortedWith(
            compareBy<HabitRowState> { it.result.status == PeriodStatus.COMPLETED }
                .thenBy { it.habit.sortOrder }
                .thenBy { it.habit.id },
        )
        return TodayUiState(
            date = date,
            today = today,
            daily = ordered.filter { it.habit.period == Period.DAILY },
            periodic = ordered.filter { it.habit.period != Period.DAILY },
            hasHabits = habits.isNotEmpty(),
        )
    }

    private fun row(habit: Habit, totals: DailyTotals, date: LocalDate, today: LocalDate): HabitRowState? {
        val result = HabitEvaluator.evaluateOn(habit, date, totals, today) ?: return null
        val dayTotal = totals[date]
        val action = when {
            date != today -> RowAction.NONE
            habit.unit.isBinary -> RowAction.TOGGLE
            habit.goalFor(result.range.start).direction == Direction.AT_MOST && dayTotal == null -> RowAction.LOG_ZERO
            else -> RowAction.QUICK_ADD
        }
        return HabitRowState(
            habit = habit,
            result = result,
            streak = StreakCalculator.compute(habit, totals, today).current,
            daysLeft = if (habit.period != Period.DAILY && today in result.range) result.range.daysLeftAfter(today) else null,
            dayTotal = dayTotal,
            action = action,
        )
    }
}
