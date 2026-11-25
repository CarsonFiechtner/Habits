package io.github.carsonfiechtner.keepup.domain.model

import java.time.LocalDate

/**
 * A habit's goal, in force for every period that starts on or after [effectiveFrom]
 * (until a later goal takes over). See design doc §5 "Why goals are versioned".
 */
data class Goal(
    val direction: Direction,
    val target: Double,
    val effectiveFrom: LocalDate,
) {
    init {
        require(target >= 0) { "target must be >= 0, was $target" }
    }

    /** True if both goals ask for the same thing, ignoring when they take effect. */
    fun sameAs(other: Goal): Boolean = direction == other.direction && target == other.target
}
