package io.github.carsonfiechtner.keepup.ui.today

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

class TodayStateBuilderTest {

    private val today = LocalDate.parse("2026-11-30")
    private val anchor = LocalDate.parse("2026-11-25")
    private val done = MeasureUnit(1, "Done", "", UnitKind.BINARY, 0, 1.0, true)
    private val cups = MeasureUnit(2, "Cups", "cups", UnitKind.NUMBER, 0, 1.0, false)

    private fun habit(
        id: Long,
        unit: MeasureUnit = done,
        period: Period = Period.DAILY,
        direction: Direction = Direction.AT_LEAST,
        target: Double = 1.0,
        anchorDate: LocalDate = anchor,
    ) = Habit(
        id, "H$id", null, "✅", 0, period, anchorDate, unit,
        listOf(Goal(direction, target, anchorDate)), null, id.toInt(), Instant.EPOCH,
    )

    @Test
    fun `completed habits sink below unfinished ones`() {
        val habits = listOf(habit(1), habit(2), habit(3))
        val totals = mapOf(1L to mapOf(today to 1.0))
        val state = TodayStateBuilder.build(habits, totals, today, today)
        assertThat(state.daily.map { it.habit.id }).containsExactly(2L, 3L, 1L).inOrder()
        assertThat(state.daily.last().result.status).isEqualTo(PeriodStatus.COMPLETED)
    }

    @Test
    fun `daily and periodic habits are split into sections`() {
        val habits = listOf(habit(1), habit(2, period = Period.WEEKLY, target = 3.0))
        val state = TodayStateBuilder.build(habits, emptyMap(), today, today)
        assertThat(state.daily.map { it.habit.id }).containsExactly(1L)
        assertThat(state.periodic.map { it.habit.id }).containsExactly(2L)
        // Week 11-25..12-01 → 1 day left after 11-30.
        assertThat(state.periodic.single().daysLeft).isEqualTo(1)
    }

    @Test
    fun `at most habit offers Log 0 until something is logged`() {
        val coffee = habit(1, unit = cups, direction = Direction.AT_MOST, target = 2.0)
        assertThat(TodayStateBuilder.build(listOf(coffee), emptyMap(), today, today).daily.single().action)
            .isEqualTo(RowAction.LOG_ZERO)
        val logged = mapOf(1L to mapOf(today to 0.0))
        assertThat(TodayStateBuilder.build(listOf(coffee), logged, today, today).daily.single().action)
            .isEqualTo(RowAction.QUICK_ADD)
    }

    @Test
    fun `past days are read only and hide habits that did not exist yet`() {
        val habits = listOf(habit(1), habit(2, anchorDate = today))
        val state = TodayStateBuilder.build(habits, emptyMap(), today.minusDays(1), today)
        assertThat(state.isToday).isFalse()
        assertThat(state.daily.map { it.habit.id }).containsExactly(1L)
        assertThat(state.daily.single().action).isEqualTo(RowAction.NONE)
        assertThat(state.daily.single().result.status).isEqualTo(PeriodStatus.MISSED)
    }

    @Test
    fun `binary habits toggle`() {
        val state = TodayStateBuilder.build(listOf(habit(1)), mapOf(1L to mapOf(today to 1.0)), today, today)
        assertThat(state.daily.single().action).isEqualTo(RowAction.TOGGLE)
        assertThat(state.daily.single().isDoneOnDate).isTrue()
    }
}
