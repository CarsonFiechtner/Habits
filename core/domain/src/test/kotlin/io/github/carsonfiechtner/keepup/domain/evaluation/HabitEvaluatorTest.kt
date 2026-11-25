package io.github.carsonfiechtner.keepup.domain.evaluation

import com.google.common.truth.Truth.assertThat
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus.COMPLETED
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus.IN_PROGRESS
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus.MISSED
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus.NOT_SCHEDULED
import io.github.carsonfiechtner.keepup.domain.model.Direction.AT_LEAST
import io.github.carsonfiechtner.keepup.domain.model.Direction.AT_MOST
import io.github.carsonfiechtner.keepup.domain.model.Goal
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.domain.testing.DONE
import io.github.carsonfiechtner.keepup.domain.testing.STEPS
import io.github.carsonfiechtner.keepup.domain.testing.d
import io.github.carsonfiechtner.keepup.domain.testing.habit
import io.github.carsonfiechtner.keepup.domain.testing.totals
import org.junit.Test

class HabitEvaluatorTest {

    private val today = d("2026-11-30")

    private fun statusOn(h: io.github.carsonfiechtner.keepup.domain.model.Habit, date: String, t: Map<java.time.LocalDate, Double>) =
        HabitEvaluator.evaluateOn(h, d(date), t, today)!!.status

    // --- AT_LEAST ---

    @Test
    fun `at least - today met completes early`() {
        assertThat(statusOn(habit(), "2026-11-30", totals("2026-11-30" to 2.0))).isEqualTo(COMPLETED)
    }

    @Test
    fun `at least - today not met is in progress`() {
        assertThat(statusOn(habit(), "2026-11-30", totals("2026-11-30" to 1.5))).isEqualTo(IN_PROGRESS)
        assertThat(statusOn(habit(), "2026-11-30", totals())).isEqualTo(IN_PROGRESS)
    }

    @Test
    fun `at least - past partial or empty is missed`() {
        assertThat(statusOn(habit(), "2026-11-29", totals("2026-11-29" to 1.99))).isEqualTo(MISSED)
        assertThat(statusOn(habit(), "2026-11-29", totals())).isEqualTo(MISSED)
    }

    @Test
    fun `at least - past met is completed`() {
        assertThat(statusOn(habit(), "2026-11-29", totals("2026-11-29" to 3.0))).isEqualTo(COMPLETED)
    }

    @Test
    fun `values are compared at the unit's precision`() {
        val h = habit(target = 0.3)
        assertThat(statusOn(h, "2026-11-29", totals("2026-11-29" to 0.1 + 0.2))).isEqualTo(COMPLETED)
    }

    @Test
    fun `before the anchor there is no period`() {
        assertThat(HabitEvaluator.evaluateOn(habit(), d("2026-11-24"), totals(), today)).isNull()
    }

    @Test
    fun `future period is not scheduled`() {
        val h = habit()
        val range = io.github.carsonfiechtner.keepup.domain.period.PeriodCalculator
            .periodContaining(Period.DAILY, h.anchorDate, d("2026-12-01"))!!
        assertThat(HabitEvaluator.evaluate(h, range, totals(), today).status).isEqualTo(NOT_SCHEDULED)
    }

    // --- AT_MOST ---

    @Test
    fun `at most - today within limit is in progress, even with no data`() {
        val h = habit(direction = AT_MOST, target = 2.0)
        assertThat(statusOn(h, "2026-11-30", totals("2026-11-30" to 1.0))).isEqualTo(IN_PROGRESS)
        assertThat(statusOn(h, "2026-11-30", totals())).isEqualTo(IN_PROGRESS)
    }

    @Test
    fun `at most - exceeding the limit misses immediately`() {
        val h = habit(direction = AT_MOST, target = 2.0)
        assertThat(statusOn(h, "2026-11-30", totals("2026-11-30" to 2.5))).isEqualTo(MISSED)
    }

    @Test
    fun `at most - past day with no data is missed, but a logged zero completes`() {
        val h = habit(direction = AT_MOST, target = 2.0)
        assertThat(statusOn(h, "2026-11-29", totals())).isEqualTo(MISSED)
        assertThat(statusOn(h, "2026-11-29", totals("2026-11-29" to 0.0))).isEqualTo(COMPLETED)
        assertThat(statusOn(h, "2026-11-29", totals("2026-11-29" to 2.0))).isEqualTo(COMPLETED)
    }

    @Test
    fun `at most zero - any amount misses, logged zero completes`() {
        val h = habit(direction = AT_MOST, target = 0.0)
        assertThat(statusOn(h, "2026-11-29", totals("2026-11-29" to 0.0))).isEqualTo(COMPLETED)
        assertThat(statusOn(h, "2026-11-29", totals("2026-11-29" to 1.0))).isEqualTo(MISSED)
    }

    // --- Periods ---

    @Test
    fun `weekly total sums the anchored week`() {
        // Anchor Wed 2026-11-25: week 0 is Wed 11-25 .. Tue 12-01, which contains "today" (11-30).
        val h = habit(period = Period.WEEKLY, unit = STEPS, target = 70_000.0)
        val t = totals("2026-11-25" to 10_000.0, "2026-11-28" to 30_000.0, "2026-11-30" to 25_000.0)
        val result = HabitEvaluator.evaluateOn(h, today, t, today)!!
        assertThat(result.value).isEqualTo(65_000.0)
        assertThat(result.status).isEqualTo(IN_PROGRESS)
        assertThat(result.fill).isWithin(1e-6f).of(65_000f / 70_000f)

        val more = t + (d("2026-11-30") to 30_000.0)
        assertThat(HabitEvaluator.evaluateOn(h, today, more, today)!!.status).isEqualTo(COMPLETED)
    }

    @Test
    fun `binary weekly counts done days`() {
        val h = habit(period = Period.WEEKLY, unit = DONE, target = 3.0)
        val t = totals("2026-11-25" to 1.0, "2026-11-27" to 1.0)
        assertThat(HabitEvaluator.evaluateOn(h, today, t, today)!!.status).isEqualTo(IN_PROGRESS)
        assertThat(HabitEvaluator.evaluateOn(h, today, t + (d("2026-11-30") to 1.0), today)!!.status)
            .isEqualTo(COMPLETED)
    }

    @Test
    fun `goal versions apply by period start`() {
        val h = habit(
            goals = listOf(
                Goal(AT_LEAST, 2.0, d("2026-11-25")),
                Goal(AT_LEAST, 3.0, d("2026-11-29")),
            ),
        )
        assertThat(statusOn(h, "2026-11-28", totals("2026-11-28" to 2.0))).isEqualTo(COMPLETED)
        assertThat(statusOn(h, "2026-11-29", totals("2026-11-29" to 2.0))).isEqualTo(MISSED)
        assertThat(statusOn(h, "2026-11-29", totals("2026-11-29" to 3.0))).isEqualTo(COMPLETED)
    }

    @Test
    fun `fill for at most shows how much of the limit is used`() {
        val h = habit(direction = AT_MOST, target = 4.0)
        val result = HabitEvaluator.evaluateOn(h, today, totals("2026-11-30" to 1.0), today)!!
        assertThat(result.fill).isWithin(1e-6f).of(0.25f)
    }
}
