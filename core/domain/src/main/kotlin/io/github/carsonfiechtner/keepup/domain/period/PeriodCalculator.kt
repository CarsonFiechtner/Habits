package io.github.carsonfiechtner.keepup.domain.period

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Computes period boundaries for a habit.
 *
 * Periods are anchored to the habit's anchor date (its creation date), see design doc §6.0:
 * - DAILY: every calendar day.
 * - WEEKLY: 7-day blocks starting on the anchor's weekday (anchor on a Wednesday → Wed..Tue).
 * - MONTHLY: one-month blocks starting on the anchor's day of month (anchor on the 15th → 15th..14th).
 *   Period k starts at `anchor.plusMonths(k)`, always computed from the anchor so that anchors on the
 *   29th–31st clamp in short months without drifting (Jan 31 → Feb 28 → Mar 31).
 */
object PeriodCalculator {

    /**
     * Returns the period of a habit with the given [period] and [anchor] that contains [date],
     * or `null` if [date] is before the anchor (the habit didn't exist yet).
     */
    fun periodContaining(period: Period, anchor: LocalDate, date: LocalDate): PeriodRange? {
        if (date.isBefore(anchor)) return null
        return when (period) {
            Period.DAILY -> PeriodRange(ChronoUnit.DAYS.between(anchor, date), date, date)
            Period.WEEKLY -> periodAt(period, anchor, ChronoUnit.DAYS.between(anchor, date) / 7)
            Period.MONTHLY -> {
                // Month-of-year difference is either the right index or one too many
                // (when the date falls before the anchor's day in its month).
                var index = ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), date.withDayOfMonth(1))
                if (anchor.plusMonths(index).isAfter(date)) index--
                periodAt(period, anchor, index)
            }
        }
    }

    /** Returns the period with the given [index] (0 = the one starting on [anchor]). */
    fun periodAt(period: Period, anchor: LocalDate, index: Long): PeriodRange {
        require(index >= 0) { "index must be >= 0, was $index" }
        val start = startOf(period, anchor, index)
        val nextStart = startOf(period, anchor, index + 1)
        return PeriodRange(index, start, nextStart.minusDays(1))
    }

    /** First day of the period after the one containing [date]: when a goal edit made on [date] takes effect. */
    fun nextPeriodStart(period: Period, anchor: LocalDate, date: LocalDate): LocalDate {
        val current = periodContaining(period, anchor, date) ?: return anchor
        return current.endInclusive.plusDays(1)
    }

    private fun startOf(period: Period, anchor: LocalDate, index: Long): LocalDate = when (period) {
        Period.DAILY -> anchor.plusDays(index)
        Period.WEEKLY -> anchor.plusWeeks(index)
        Period.MONTHLY -> anchor.plusMonths(index)
    }
}
