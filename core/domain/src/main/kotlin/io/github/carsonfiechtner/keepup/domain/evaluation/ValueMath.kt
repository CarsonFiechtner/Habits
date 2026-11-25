package io.github.carsonfiechtner.keepup.domain.evaluation

import java.math.BigDecimal
import java.math.RoundingMode

object ValueMath {
    /**
     * Rounds to the unit's display precision, so that comparisons match what the user sees
     * (0.1 + 0.2 L meets a 0.3 L goal). See design doc §9.2.
     */
    fun round(value: Double, decimals: Int): Double =
        BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toDouble()
}
