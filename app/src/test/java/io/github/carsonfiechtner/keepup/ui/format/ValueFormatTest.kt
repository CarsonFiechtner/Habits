package io.github.carsonfiechtner.keepup.ui.format

import com.google.common.truth.Truth.assertThat
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.Goal
import io.github.carsonfiechtner.keepup.domain.model.MeasureUnit
import io.github.carsonfiechtner.keepup.domain.model.UnitKind
import io.github.carsonfiechtner.keepup.ui.common.HabitText
import org.junit.Test
import java.time.LocalDate

class ValueFormatTest {
    private val minutes = MeasureUnit(3, "Minutes", "min", UnitKind.NUMBER, 0, 5.0, isBuiltin = true)
    private val liters = MeasureUnit(6, "Liters", "L", UnitKind.NUMBER, 2, 0.25, isBuiltin = true)

    @Test
    fun `durations`() {
        assertThat(ValueFormat.duration(0.0)).isEqualTo("0m")
        assertThat(ValueFormat.duration(45.4)).isEqualTo("45m")
        assertThat(ValueFormat.duration(85.0)).isEqualTo("1h 25m")
        assertThat(ValueFormat.duration(120.0)).isEqualTo("2h")
    }

    @Test
    fun `built-in minutes are shown as durations, other units as numbers`() {
        assertThat(ValueFormat.withUnit(95.0, minutes)).isEqualTo("1h 35m")
        assertThat(ValueFormat.withUnit(1.25, liters)).isEqualTo("1.25 L")
        val custom = minutes.copy(isBuiltin = false)
        assertThat(ValueFormat.withUnit(95.0, custom)).isEqualTo("95 min")
    }

    @Test
    fun `progress text for a screen time limit`() {
        val goal = Goal(Direction.AT_MOST, 120.0, LocalDate.parse("2026-11-25"))
        assertThat(HabitText.progress(75.0, goal, minutes)).isEqualTo("1h 15m / max 2h")
    }

    @Test
    fun `parsing accepts comma decimals and rejects junk`() {
        assertThat(ValueFormat.parse("1,5")).isEqualTo(1.5)
        assertThat(ValueFormat.parse(" 2 ")).isEqualTo(2.0)
        assertThat(ValueFormat.parse("-1")).isNull()
        assertThat(ValueFormat.parse("abc")).isNull()
    }
}
