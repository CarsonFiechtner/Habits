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
        val n = number(value, unit.decimals)
        return if (unit.label.isBlank()) n else "$n ${unit.label}"
    }

    /** Parses user input; accepts "." or "," as the decimal separator. Null if not a valid non-negative number. */
    fun parse(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}
