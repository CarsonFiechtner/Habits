package io.github.carsonfiechtner.keepup.domain.evaluation

import io.github.carsonfiechtner.keepup.domain.period.Period

/**
 * The six streak levels. A habit's streak, its badge and its color all follow its stage.
 * A missed period ends the streak, so the habit falls back to [SEED].
 */
enum class StreakStage(val displayName: String) {
    SEED("Seed"),
    SPROUT("Sprout"),
    SAPLING("Sapling"),
    OAK("Oak"),
    SILVERBOUGH("Silverbough"),
    WORLD_TREE("World Tree"),
    ;

    companion object {
        /**
         * Streak length (in the habit's own periods) at which each stage after [SEED] begins.
         * Daily: 1 week, 1 month, 3 months, 1 year, 3 years. Weekly and monthly habits use the
         * nearest equivalent count of weeks or months.
         */
        fun thresholds(period: Period): List<Int> = when (period) {
            Period.DAILY -> listOf(7, 30, 90, 365, 1095)
            Period.WEEKLY -> listOf(1, 4, 13, 52, 156)
            Period.MONTHLY -> listOf(1, 2, 3, 12, 36)
        }

        fun of(streak: Int, period: Period): StreakStage =
            entries[thresholds(period).count { streak >= it }]

        /** The next stage and how many more periods it takes, or null at the top. */
        fun next(streak: Int, period: Period): Pair<StreakStage, Int>? {
            val current = of(streak, period)
            if (current == WORLD_TREE) return null
            val threshold = thresholds(period)[current.ordinal]
            return entries[current.ordinal + 1] to (threshold - streak)
        }

        /** The streak length at which [stage] starts (0 for [SEED]). */
        fun startOf(stage: StreakStage, period: Period): Int =
            if (stage == SEED) 0 else thresholds(period)[stage.ordinal - 1]
    }
}
