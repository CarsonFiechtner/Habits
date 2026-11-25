package io.github.carsonfiechtner.keepup.ui.common

import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.Goal
import io.github.carsonfiechtner.keepup.domain.model.MeasureUnit
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.ui.format.ValueFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object HabitText {
    private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

    fun date(date: LocalDate): String = date.format(shortDate)

    fun fullDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

    fun relativeDay(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date(date)
    }

    private fun periodPhrase(period: Period) = when (period) {
        Period.DAILY -> "every day"
        Period.WEEKLY -> "each week"
        Period.MONTHLY -> "each month"
    }

    /** "At least 2 L every day", "Done on at least 3 days each week", "Done every day". */
    fun goalSentence(goal: Goal, unit: MeasureUnit, period: Period): String {
        val direction = if (goal.direction == Direction.AT_LEAST) "At least" else "At most"
        return if (unit.isBinary) {
            if (period == Period.DAILY) {
                "Done every day"
            } else {
                val n = ValueFormat.number(goal.target, 0)
                "Done on ${direction.lowercase()} $n ${if (goal.target == 1.0) "day" else "days"} ${periodPhrase(period)}"
            }
        } else {
            "$direction ${ValueFormat.withUnit(goal.target, unit)} ${periodPhrase(period)}"
        }
    }

    /** Compact progress: "1.2 / 2 L", "1 / max 2 cups", "2 / 3 days". */
    fun progress(value: Double, goal: Goal, unit: MeasureUnit): String {
        val target = ValueFormat.number(goal.target, unit.decimals)
        val max = if (goal.direction == Direction.AT_MOST) "max " else ""
        val label = if (unit.isBinary) "days" else unit.label
        val suffix = if (label.isBlank()) "" else " $label"
        return "${ValueFormat.number(value, unit.decimals)} / $max$target$suffix"
    }
}
