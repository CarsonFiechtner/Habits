package io.github.carsonfiechtner.keepup.domain.evaluation

import io.github.carsonfiechtner.keepup.domain.model.Goal
import io.github.carsonfiechtner.keepup.domain.period.PeriodRange

enum class PeriodStatus {
    /** Goal met. For AT_LEAST this can happen before the period ends. */
    COMPLETED,

    /** Current period, goal not decided yet. */
    IN_PROGRESS,

    /** Goal not met. Also: past period with no data, or an AT_MOST limit exceeded. */
    MISSED,

    /** The period hasn't started yet. */
    NOT_SCHEDULED,
}

data class PeriodResult(
    val range: PeriodRange,
    val goal: Goal,
    /** Sum of the period's entries, rounded to the unit's precision. */
    val value: Double,
    /** At least one entry exists in the period. A period without data is a miss. */
    val hasData: Boolean,
    val status: PeriodStatus,
) {
    /**
     * How full the progress indicator is, 0..1. For AT_LEAST it's progress toward the target;
     * for AT_MOST it's how much of the limit has been used.
     */
    val fill: Float
        get() = when {
            goal.target > 0 -> (value / goal.target).coerceIn(0.0, 1.0).toFloat()
            value > 0 -> 1f
            else -> 0f
        }
}
