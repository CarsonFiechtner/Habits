package io.github.carsonfiechtner.keepup.domain.period

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One concrete period of a habit, e.g. the week Wed 2026-11-25 .. Tue 2026-12-01.
 *
 * @property index 0 for the habit's first period (the one starting on its anchor date), 1 for the next, …
 * @property start first day of the period (inclusive)
 * @property endInclusive last day of the period (inclusive)
 */
data class PeriodRange(
    val index: Long,
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    init {
        require(!endInclusive.isBefore(start)) { "endInclusive ($endInclusive) is before start ($start)" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)

    /** Number of days in the period. */
    val lengthInDays: Long get() = ChronoUnit.DAYS.between(start, endInclusive) + 1

    /** Days remaining in the period after [today], i.e. 0 on the last day. */
    fun daysLeftAfter(today: LocalDate): Long {
        require(today in this) { "$today is not in $this" }
        return ChronoUnit.DAYS.between(today, endInclusive)
    }
}
