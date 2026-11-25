package io.github.carsonfiechtner.keepup.ui.history

import com.google.common.truth.Truth.assertThat
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.Goal
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.model.MeasureUnit
import io.github.carsonfiechtner.keepup.domain.model.UnitKind
import io.github.carsonfiechtner.keepup.domain.period.Period
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HistoryStateBuilderTest {
    private val today = LocalDate.parse("2026-11-30")
    private val done = MeasureUnit(1, "Done", "", UnitKind.BINARY, 0, 1.0, true)

    private fun habit(id: Long, anchor: LocalDate, period: Period = Period.DAILY, target: Double = 1.0) = Habit(
        id, "H$id", null, "✅", 0, period, anchor, done,
        listOf(Goal(Direction.AT_LEAST, target, anchor)), null, id.toInt(), Instant.EPOCH,
    )

    @Test
    fun `daily rows have one cell per day, null before the start`() {
        val h = habit(1, today.minusDays(2))
        val totals = mapOf(1L to mapOf(today.minusDays(2) to 1.0))
        val row = HistoryStateBuilder.build(listOf(h), totals, today).daily.single()
        assertThat(row.cells).containsExactly(
            null, null, null, null, PeriodStatus.COMPLETED, PeriodStatus.MISSED, PeriodStatus.IN_PROGRESS,
        ).inOrder()
    }

    @Test
    fun `periodic rows show recent periods`() {
        val weekly = habit(1, today.minusDays(8), Period.WEEKLY, 3.0)
        val row = HistoryStateBuilder.build(listOf(weekly), emptyMap(), today).periodic.single()
        assertThat(row.cells.takeLast(2)).containsExactly(PeriodStatus.MISSED, PeriodStatus.IN_PROGRESS).inOrder()
        assertThat(row.cells.dropLast(2).all { it == null }).isTrue()
    }

    @Test
    fun `trend counts decided daily habits per day`() {
        val a = habit(1, today.minusDays(1))
        val b = habit(2, today.minusDays(1))
        val totals = mapOf(1L to mapOf(today.minusDays(1) to 1.0))
        val trend = HistoryStateBuilder.build(listOf(a, b), totals, today).trend
        assertThat(trend.last().ratio).isNull() // today: nothing decided yet
        assertThat(trend[trend.size - 2].ratio).isEqualTo(0.5f)
    }

    @Test
    fun `habits that have not started are left out`() {
        val state = HistoryStateBuilder.build(listOf(habit(1, today.plusDays(1))), emptyMap(), today)
        assertThat(state.daily).isEmpty()
    }
}
