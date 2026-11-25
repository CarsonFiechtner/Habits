package io.github.carsonfiechtner.keepup.domain.evaluation

import com.google.common.truth.Truth.assertThat
import io.github.carsonfiechtner.keepup.domain.model.Direction.AT_MOST
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.domain.testing.STEPS
import io.github.carsonfiechtner.keepup.domain.testing.d
import io.github.carsonfiechtner.keepup.domain.testing.habit
import io.github.carsonfiechtner.keepup.domain.testing.totals
import org.junit.Test

class StreakCalculatorTest {

    // Habit created Wed 2026-11-25.
    private val h = habit()

    @Test
    fun `no history means no streak`() {
        assertThat(StreakCalculator.compute(h, totals(), d("2026-11-25"))).isEqualTo(Streaks(0, 0))
    }

    @Test
    fun `today in progress does not break the streak`() {
        val t = totals("2026-11-25" to 2.0, "2026-11-26" to 2.0, "2026-11-27" to 2.0)
        assertThat(StreakCalculator.compute(h, t, d("2026-11-28"))).isEqualTo(Streaks(3, 3))
    }

    @Test
    fun `today completed counts`() {
        val t = totals("2026-11-25" to 2.0, "2026-11-26" to 2.0)
        assertThat(StreakCalculator.compute(h, t, d("2026-11-26"))).isEqualTo(Streaks(2, 2))
    }

    @Test
    fun `a missed or partial day ends the streak`() {
        val t = totals(
            "2026-11-25" to 2.0, "2026-11-26" to 2.0, "2026-11-27" to 2.0, // 3
            "2026-11-28" to 1.0, // partial = miss
            "2026-11-29" to 2.0, "2026-11-30" to 2.0, // 2
        )
        assertThat(StreakCalculator.compute(h, t, d("2026-12-01"))).isEqualTo(Streaks(2, 3))
    }

    @Test
    fun `an empty day ends the streak`() {
        val t = totals("2026-11-25" to 2.0, "2026-11-27" to 2.0)
        assertThat(StreakCalculator.compute(h, t, d("2026-11-28"))).isEqualTo(Streaks(1, 1))
    }

    @Test
    fun `at most - exceeding today zeroes the current streak`() {
        val limit = habit(direction = AT_MOST, target = 2.0)
        val t = totals("2026-11-25" to 0.0, "2026-11-26" to 1.0, "2026-11-27" to 3.0)
        assertThat(StreakCalculator.compute(limit, t, d("2026-11-27"))).isEqualTo(Streaks(0, 2))
    }

    @Test
    fun `weekly streak counts weeks`() {
        val weekly = habit(period = Period.WEEKLY, unit = STEPS, target = 70_000.0)
        val t = totals(
            "2026-11-26" to 70_000.0, // week 0 (11-25..12-01)
            "2026-12-05" to 80_000.0, // week 1 (12-02..12-08)
            "2026-12-09" to 10_000.0, // week 2, in progress
        )
        assertThat(StreakCalculator.compute(weekly, t, d("2026-12-10"))).isEqualTo(Streaks(2, 2))
    }
}
