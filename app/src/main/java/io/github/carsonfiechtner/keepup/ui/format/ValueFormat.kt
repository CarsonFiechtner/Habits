package io.github.carsonfiechtner.keepup.ui.format

import io.github.carsonfiechtner.keepup.domain.model.MeasureUnit
import java.text.NumberFormat

object ValueFormat {
    /** Formats with up to [decimals] fraction digits and locale grouping: 10,000 / 1.25 / 2. */
    fun number(value: Double, decimals: Int): String =
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = decimals
            isGroupingUsed = true
        }.format(value)

    fun withUnit(value: Double, unit: MeasureUnit): String {
        if (isMinutes(unit)) return duration(value)
        val n = number(value, unit.decimals)
        return if (unit.label.isBlank()) n else "$n ${unit.label}"
    }

    /** The built-in Minutes unit is shown as a duration ("1h 25m"). */
    fun isMinutes(unit: MeasureUnit): Boolean = unit.isBuiltin && unit.label == "min"

    /** 45 → "45m", 85 → "1h 25m", 120 → "2h". */
    fun duration(minutes: Double): String {
        val total = Math.round(minutes).toInt()
        val h = total / 60
        val m = total % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** Parses user input; accepts "." or "," as the decimal separator. Null if not a valid non-negative number. */
    fun parse(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}
