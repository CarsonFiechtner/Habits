package io.github.carsonfiechtner.keepup.domain.period

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PeriodCalculatorTest {

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `dates before the anchor have no period`() {
        Period.entries.forEach { period ->
            assertThat(PeriodCalculator.periodContaining(period, d("2026-11-25"), d("2026-11-24"))).isNull()
        }
    }

    @Test
    fun `daily period is the day itself`() {
        val range = PeriodCalculator.periodContaining(Period.DAILY, d("2026-11-25"), d("2026-12-03"))!!
        assertThat(range).isEqualTo(PeriodRange(8, d("2026-12-03"), d("2026-12-03")))
    }

    @Test
    fun `weekly periods start on the anchor weekday`() {
        val anchor = d("2026-11-25") // Wednesday
        assertThat(anchor.dayOfWeek).isEqualTo(DayOfWeek.WEDNESDAY)

        val first = PeriodCalculator.periodContaining(Period.WEEKLY, anchor, anchor)!!
        assertThat(first).isEqualTo(PeriodRange(0, d("2026-11-25"), d("2026-12-01")))
        assertThat(first.endInclusive.dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)

        // Last day of week 0 and first day of week 1.
        assertThat(PeriodCalculator.periodContaining(Period.WEEKLY, anchor, d("2026-12-01"))!!.index).isEqualTo(0)
        assertThat(PeriodCalculator.periodContaining(Period.WEEKLY, anchor, d("2026-12-02")))
            .isEqualTo(PeriodRange(1, d("2026-12-02"), d("2026-12-08")))
    }

    @Test
    fun `weekly periods cross year boundaries and are always 7 days`() {
        val anchor = d("2026-11-25")
        var date = anchor
        repeat(800) {
            val range = PeriodCalculator.periodContaining(Period.WEEKLY, anchor, date)!!
            assertThat(range.lengthInDays).isEqualTo(7)
            assertThat(range.start.dayOfWeek).isEqualTo(DayOfWeek.WEDNESDAY)
            assertThat(date in range).isTrue()
            date = date.plusDays(1)
        }
    }

    @Test
    fun `monthly periods start on the anchor day of month`() {
        val anchor = d("2026-11-15")
        assertThat(PeriodCalculator.periodContaining(Period.MONTHLY, anchor, d("2026-12-14")))
            .isEqualTo(PeriodRange(0, d("2026-11-15"), d("2026-12-14")))
        assertThat(PeriodCalculator.periodContaining(Period.MONTHLY, anchor, d("2026-12-15")))
            .isEqualTo(PeriodRange(1, d("2026-12-15"), d("2027-01-14")))
    }

    @Test
    fun `monthly anchor on the 31st clamps in short months without drifting`() {
        val anchor = d("2027-01-31")
        assertThat(PeriodCalculator.periodAt(Period.MONTHLY, anchor, 0))
            .isEqualTo(PeriodRange(0, d("2027-01-31"), d("2027-02-27")))
        assertThat(PeriodCalculator.periodAt(Period.MONTHLY, anchor, 1))
            .isEqualTo(PeriodRange(1, d("2027-02-28"), d("2027-03-30")))
        assertThat(PeriodCalculator.periodAt(Period.MONTHLY, anchor, 2))
            .isEqualTo(PeriodRange(2, d("2027-03-31"), d("2027-04-29")))
        // Mar 1 belongs to the period that started Feb 28.
        assertThat(PeriodCalculator.periodContaining(Period.MONTHLY, anchor, d("2027-03-01"))!!.index).isEqualTo(1)
    }

    @Test
    fun `monthly anchor on Feb 29 in a leap year`() {
        val anchor = d("2028-02-29")
        assertThat(PeriodCalculator.periodAt(Period.MONTHLY, anchor, 12).start).isEqualTo(d("2029-02-28"))
        assertThat(PeriodCalculator.periodAt(Period.MONTHLY, anchor, 13).start).isEqualTo(d("2029-03-29"))
    }

    @Test
    fun `monthly periods tile every day with no gaps or overlaps`() {
        for (anchor in listOf(d("2026-01-31"), d("2026-11-15"), d("2028-02-29"), d("2026-03-30"))) {
            var expectedStart = anchor
            for (index in 0L until 40L) {
                val range = PeriodCalculator.periodAt(Period.MONTHLY, anchor, index)
                assertThat(range.start).isEqualTo(expectedStart)
                // Every day in the range maps back to the same period.
                var day = range.start
                while (!day.isAfter(range.endInclusive)) {
                    assertThat(PeriodCalculator.periodContaining(Period.MONTHLY, anchor, day)).isEqualTo(range)
                    day = day.plusDays(1)
                }
                expectedStart = range.endInclusive.plusDays(1)
            }
        }
    }

    @Test
    fun `next period start is the day after the current period ends`() {
        val anchor = d("2026-11-25")
        assertThat(PeriodCalculator.nextPeriodStart(Period.DAILY, anchor, d("2026-11-30"))).isEqualTo(d("2026-12-01"))
        assertThat(PeriodCalculator.nextPeriodStart(Period.WEEKLY, anchor, d("2026-11-30"))).isEqualTo(d("2026-12-02"))
        assertThat(PeriodCalculator.nextPeriodStart(Period.MONTHLY, anchor, d("2026-11-30"))).isEqualTo(d("2026-12-25"))
    }

    @Test
    fun `days left counts days after today`() {
        val range = PeriodRange(0, d("2026-11-25"), d("2026-12-01"))
        assertThat(range.daysLeftAfter(d("2026-11-25"))).isEqualTo(6)
        assertThat(range.daysLeftAfter(d("2026-11-30"))).isEqualTo(1) // second-to-last day: reminder night
        assertThat(range.daysLeftAfter(d("2026-12-01"))).isEqualTo(0)
    }
}
