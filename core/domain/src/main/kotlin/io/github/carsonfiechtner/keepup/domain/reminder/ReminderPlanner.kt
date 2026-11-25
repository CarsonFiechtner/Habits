package io.github.carsonfiechtner.keepup.domain.reminder

import io.github.carsonfiechtner.keepup.domain.evaluation.DailyTotals
import io.github.carsonfiechtner.keepup.domain.evaluation.HabitEvaluator
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodResult
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.period.Period
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

enum class ReminderReason {
    /** A daily "at least" goal isn't met yet. */
    NOT_DONE,

    /** An "at most" habit has nothing logged, which would count as a miss: log 0 or the amount. */
    NEEDS_LOG,

    /** Tomorrow is the last day of a weekly/monthly period and its goal isn't met yet. */
    PERIOD_ENDING,
}

data class ReminderItem(val habit: Habit, val result: PeriodResult, val reason: ReminderReason)

/** Decides what the nightly reminder lists (design doc §11.1). */
object ReminderPlanner {

    fun items(
        habits: List<Habit>,
        totals: Map<Long, DailyTotals>,
        today: LocalDate,
        /** Habits whose values come from a data source the user can't act on (e.g. screen time limits). */
        isPassive: (Habit) -> Boolean = { false },
    ): List<ReminderItem> = habits
        .filter { !it.isArchived && !isPassive(it) }
        .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        .mapNotNull { habit ->
            val t = totals[habit.id].orEmpty()
            val result = HabitEvaluator.evaluateOn(habit, today, t, today) ?: return@mapNotNull null
            if (result.status == PeriodStatus.COMPLETED || result.status == PeriodStatus.MISSED) return@mapNotNull null
            val direction = result.goal.direction
            val reason = when (habit.period) {
                Period.DAILY -> when {
                    direction == Direction.AT_LEAST -> ReminderReason.NOT_DONE
                    t[today] == null -> ReminderReason.NEEDS_LOG
                    else -> null
                }
                Period.WEEKLY, Period.MONTHLY -> when {
                    result.range.daysLeftAfter(today) != 1L -> null
                    direction == Direction.AT_LEAST -> ReminderReason.PERIOD_ENDING
                    !result.hasData -> ReminderReason.NEEDS_LOG
                    else -> null
                }
            }
            reason?.let { ReminderItem(habit, result, it) }
        }

    /** The next time the reminder should fire: [time] today if it's still ahead, otherwise tomorrow. */
    fun nextTrigger(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val todayAt = now.toLocalDate().atTime(time).atZone(now.zone)
        return if (todayAt.isAfter(now)) todayAt else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
    }
}
