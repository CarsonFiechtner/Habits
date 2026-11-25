package io.github.carsonfiechtner.keepup.ui.detail

import io.github.carsonfiechtner.keepup.domain.evaluation.DailyTotals
import io.github.carsonfiechtner.keepup.domain.evaluation.HabitEvaluator
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.Habit
import java.time.DayOfWeek
import java.time.LocalDate

/** One square of the heatmap. */
sealed interface HeatCell {
    val date: LocalDate

    data class Before(override val date: LocalDate) : HeatCell
    data class Future(override val date: LocalDate) : HeatCell

    /** 0 = missed / nothing, 1–2 = partial, 3 = goal met, 4 = well over (at least 1.25× the target). */
    data class Level(override val date: LocalDate, val level: Int) : HeatCell
}

/** Weeks (Monday first) of a daily habit's days, oldest week first, ending with the week of [asOf]. */
object HeatmapBuilder {

    fun build(habit: Habit, totals: DailyTotals, asOf: LocalDate, weeks: Int): List<List<HeatCell>> {
        val monday = asOf.with(DayOfWeek.MONDAY)
        val first = monday.minusWeeks((weeks - 1).toLong())
        return (0 until weeks).map { w ->
            (0 until 7).map { d -> cell(habit, totals, first.plusWeeks(w.toLong()).plusDays(d.toLong()), asOf) }
        }
    }

    private fun cell(habit: Habit, totals: DailyTotals, date: LocalDate, asOf: LocalDate): HeatCell {
        if (date.isAfter(asOf)) return HeatCell.Future(date)
        val result = HabitEvaluator.evaluateOn(habit, date, totals, asOf) ?: return HeatCell.Before(date)
        val target = result.goal.target
        val ratio = if (target > 0) result.value / target else if (result.value > 0) 1.0 else 0.0
        val level = when (result.goal.direction) {
            Direction.AT_LEAST -> when {
                result.status == PeriodStatus.COMPLETED -> if (ratio >= 1.25) 4 else 3
                !result.hasData || result.value <= 0 -> 0
                ratio < 0.5 -> 1
                else -> 2
            }
            Direction.AT_MOST -> when (result.status) {
                PeriodStatus.COMPLETED -> 3
                PeriodStatus.IN_PROGRESS -> if (result.hasData) 2 else 0
                else -> if (result.hasData) 1 else 0
            }
        }
        return HeatCell.Level(date, level)
    }
}
