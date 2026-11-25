package io.github.carsonfiechtner.keepup.domain.evaluation

import com.google.common.truth.Truth.assertThat
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.domain.testing.STEPS
import io.github.carsonfiechtner.keepup.domain.testing.d
import io.github.carsonfiechtner.keepup.domain.testing.habit
import io.github.carsonfiechtner.keepup.domain.testing.totals
import org.junit.Test

class HabitHistoryTest {

    private val h = habit() // daily, at least 2 L, from 2026-11-25

    @Test
    fun `periods run from the start date to the period containing asOf`() {
        val periods = HabitHistory.periods(h, totals(), d("2026-11-28"))
        assertThat(periods.map { it.range.start }).containsExactly(
            d("2026-11-25"), d("2026-11-26"), d("2026-11-27"), d("2026-11-28"),
        ).inOrder()
        assertThat(periods.last().status).isEqualTo(PeriodStatus.IN_PROGRESS)
    }

    @Test
    fun `no periods before the start date`() {
        assertThat(HabitHistory.periods(habit(anchor = d("2026-12-01")), totals(), d("2026-11-28"))).isEmpty()
    }

    @Test
    fun `completion rate ignores the unfinished current period`() {
        val t = totals("2026-11-25" to 2.0, "2026-11-26" to 1.0, "2026-11-27" to 2.0)
        val rate = HabitHistory.completionRate(HabitHistory.periods(h, t, d("2026-11-28")))
        assertThat(rate).isEqualTo(CompletionRate(completed = 2, decided = 3))
        assertThat(rate.ratio).isWithin(1e-6f).of(2f / 3f)
    }

    @Test
    fun `a completed current period counts`() {
        val t = totals("2026-11-25" to 2.0, "2026-11-26" to 2.0)
        assertThat(HabitHistory.completionRate(HabitHistory.periods(h, t, d("2026-11-26"))))
            .isEqualTo(CompletionRate(2, 2))
    }

    @Test
    fun `no decided periods yields no ratio`() {
        assertThat(HabitHistory.completionRate(HabitHistory.periods(h, totals(), d("2026-11-25"))).ratio).isNull()
    }

    @Test
    fun `stats use a recent window that matches the period`() {
        val weekly = habit(period = Period.WEEKLY, unit = STEPS, target = 70_000.0)
        assertThat(HabitHistory.stats(weekly, totals(), d("2026-12-30")).recentWindow).isEqualTo(12)
        val stats = HabitHistory.stats(h, totals("2026-11-25" to 2.0), d("2026-11-26"))
        assertThat(stats.recentWindow).isEqualTo(30)
        assertThat(stats.streaks).isEqualTo(Streaks(1, 1))
        assertThat(stats.allTime).isEqualTo(CompletionRate(1, 1))
    }
}
