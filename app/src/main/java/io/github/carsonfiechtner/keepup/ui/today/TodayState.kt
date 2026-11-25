package io.github.carsonfiechtner.keepup.ui.today

import io.github.carsonfiechtner.keepup.domain.evaluation.DailyTotals
import io.github.carsonfiechtner.keepup.domain.evaluation.HabitEvaluator
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodResult
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakCalculator
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakStage
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
    /** Stage of the current streak; also decides the habit's color. */
    val stage: StreakStage = StreakStage.of(streak, habit.period),
) {
    val isDoneOnDate: Boolean get() = habit.unit.isBinary && (dayTotal ?: 0.0) > 0
    val isCompleted: Boolean get() = result.status == PeriodStatus.COMPLETED
}

/** One day in the week strip. [ratio] is the share of daily habits completed that day, null if none existed. */
data class WeekDay(val date: LocalDate, val ratio: Float?, val selected: Boolean, val isToday: Boolean)

/** The summary card: how much of the viewed day is done, and the best running streak. */
data class TodaySummary(
    val total: Int,
    val done: Int,
    val openNames: List<String>,
    val best: HabitRowState?,
)

data class TodayUiState(
    val date: LocalDate,
    val today: LocalDate,
    val daily: List<HabitRowState>,
    val periodic: List<HabitRowState>,
    /** Habits whose start date is after the viewed date (only listed when viewing today). */
    val upcoming: List<Habit> = emptyList(),
    /** At least one active habit exists (it may not have started by [date]). */
    val hasHabits: Boolean,
    val loading: Boolean = false,
    val week: List<WeekDay> = emptyList(),
) {
    val isToday: Boolean get() = date == today
    val openDaily: List<HabitRowState> get() = daily.filter { !it.isCompleted }
    val openPeriodic: List<HabitRowState> get() = periodic.filter { !it.isCompleted }
    val done: List<HabitRowState> get() = (daily + periodic).filter { it.isCompleted }

    val summary: TodaySummary
        get() {
            val all = daily + periodic
            val best = all.filter { it.streak > 0 }
                .maxWithOrNull(compareBy<HabitRowState>({ it.stage.ordinal }, { it.streak }))
            return TodaySummary(
                total = all.size,
                done = all.count { it.isCompleted },
                openNames = all.filter { !it.isCompleted }.map { it.habit.name },
                best = best,
            )
        }

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
            upcoming = if (date == today) {
                habits.filter { it.anchorDate.isAfter(today) }.sortedWith(compareBy({ it.anchorDate }, { it.sortOrder }))
            } else {
                emptyList()
            },
            hasHabits = habits.isNotEmpty(),
            week = week(habits, totals, date, today),
        )
    }

    /** The 7 days ending today, each with the share of daily habits completed. */
    private fun week(habits: List<Habit>, totals: Map<Long, DailyTotals>, selected: LocalDate, today: LocalDate): List<WeekDay> =
        (6 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            val results = habits.filter { it.period == Period.DAILY }
                .mapNotNull { HabitEvaluator.evaluateOn(it, day, totals[it.id].orEmpty(), today) }
            val ratio = if (results.isEmpty()) null else results.count { it.status == PeriodStatus.COMPLETED }.toFloat() / results.size
            WeekDay(day, ratio, selected = day == selected, isToday = day == today)
        }

    private fun row(habit: Habit, totals: DailyTotals, date: LocalDate, today: LocalDate): HabitRowState? {
        val result = HabitEvaluator.evaluateOn(habit, date, totals, today) ?: return null
        val dayTotal = totals[date]
        val action = when {
            date != today || habit.source.isAutomatic -> RowAction.NONE
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
