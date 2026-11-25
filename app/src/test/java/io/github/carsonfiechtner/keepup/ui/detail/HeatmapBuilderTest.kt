package io.github.carsonfiechtner.keepup.ui.detail

import com.google.common.truth.Truth.assertThat
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.Goal
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.model.MeasureUnit
import io.github.carsonfiechtner.keepup.domain.model.UnitKind
import io.github.carsonfiechtner.keepup.domain.period.Period
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class HeatmapBuilderTest {
    private val liters = MeasureUnit(1, "Liters", "L", UnitKind.NUMBER, 2, 0.25, true)
    private val today = LocalDate.parse("2026-11-25") // Wednesday
    private val anchor = LocalDate.parse("2026-11-18")
    private val water = Habit(
        1, "Water", null, "💧", 0, Period.DAILY, anchor, liters,
        listOf(Goal(Direction.AT_LEAST, 2.0, anchor)), null, 0, Instant.EPOCH,
    )

    @Test
    fun `weeks are Monday first and end with the current week`() {
        val weeks = HeatmapBuilder.build(water, emptyMap(), today, 2)
        assertThat(weeks).hasSize(2)
        assertThat(weeks.all { it.size == 7 }).isTrue()
        assertThat(weeks[0][0].date.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assertThat(weeks[1].map { it.date }).contains(today)
        assertThat(weeks[1].last()).isInstanceOf(HeatCell.Future::class.java)
        assertThat(weeks[0][0]).isInstanceOf(HeatCell.Before::class.java) // Mon Nov 16 is before the start
    }

    @Test
    fun `levels follow how much of the goal was reached`() {
        val totals = mapOf(
            LocalDate.parse("2026-11-18") to 2.6, // 1.3x → 4
            LocalDate.parse("2026-11-19") to 2.0, // met → 3
            LocalDate.parse("2026-11-20") to 1.2, // 60% → 2
            LocalDate.parse("2026-11-21") to 0.5, // 25% → 1
        )
        val cells = HeatmapBuilder.build(water, totals, today, 2).flatten().filterIsInstance<HeatCell.Level>()
            .associate { it.date to it.level }
        assertThat(cells[LocalDate.parse("2026-11-18")]).isEqualTo(4)
        assertThat(cells[LocalDate.parse("2026-11-19")]).isEqualTo(3)
        assertThat(cells[LocalDate.parse("2026-11-20")]).isEqualTo(2)
        assertThat(cells[LocalDate.parse("2026-11-21")]).isEqualTo(1)
        assertThat(cells[LocalDate.parse("2026-11-22")]).isEqualTo(0)
    }
}
