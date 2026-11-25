package io.github.carsonfiechtner.keepup.domain.reminder

import com.google.common.truth.Truth.assertThat
import io.github.carsonfiechtner.keepup.domain.model.Direction.AT_MOST
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.domain.testing.DONE
import io.github.carsonfiechtner.keepup.domain.testing.STEPS
import io.github.carsonfiechtner.keepup.domain.testing.d
import io.github.carsonfiechtner.keepup.domain.testing.habit
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPlannerTest {
    private val anchor = d("2026-11-25") // Wednesday
    private val today = d("2026-11-30") // Monday

    @Test
    fun `daily at least habits not done are listed`() {
        val water = habit(anchor = anchor).copy(id = 1)
        val items = ReminderPlanner.items(listOf(water), mapOf(1L to mapOf(today to 1.0)), today)
        assertThat(items.single().reason).isEqualTo(ReminderReason.NOT_DONE)
        assertThat(ReminderPlanner.items(listOf(water), mapOf(1L to mapOf(today to 2.0)), today)).isEmpty()
    }

    @Test
    fun `daily at most habits need a log`() {
        val coffee = habit(anchor = anchor, direction = AT_MOST, target = 2.0).copy(id = 1)
        assertThat(ReminderPlanner.items(listOf(coffee), emptyMap(), today).single().reason)
            .isEqualTo(ReminderReason.NEEDS_LOG)
        assertThat(ReminderPlanner.items(listOf(coffee), mapOf(1L to mapOf(today to 0.0)), today)).isEmpty()
        // Already over the limit: nothing to do about it tonight.
        assertThat(ReminderPlanner.items(listOf(coffee), mapOf(1L to mapOf(today to 3.0)), today)).isEmpty()
    }

    @Test
    fun `weekly habits are listed only on the second to last day`() {
        // Week runs Wed 11-25 .. Tue 12-01; Monday 11-30 is the second-to-last day.
        val steps = habit(period = Period.WEEKLY, anchor = anchor, unit = STEPS, target = 70_000.0).copy(id = 1)
        assertThat(ReminderPlanner.items(listOf(steps), emptyMap(), today).single().reason)
            .isEqualTo(ReminderReason.PERIOD_ENDING)
        assertThat(ReminderPlanner.items(listOf(steps), emptyMap(), today.minusDays(1))).isEmpty()
        assertThat(ReminderPlanner.items(listOf(steps), emptyMap(), today.plusDays(1))).isEmpty()
    }

    @Test
    fun `binary weekly habit already met is not listed`() {
        val gym = habit(period = Period.WEEKLY, anchor = anchor, unit = DONE, target = 2.0).copy(id = 1)
        val totals = mapOf(1L to mapOf(d("2026-11-26") to 1.0, d("2026-11-28") to 1.0))
        assertThat(ReminderPlanner.items(listOf(gym), totals, today)).isEmpty()
    }

    @Test
    fun `habits not started yet and passive habits are skipped`() {
        val future = habit(anchor = today.plusDays(1)).copy(id = 1)
        val passive = habit(anchor = anchor).copy(id = 2)
        assertThat(ReminderPlanner.items(listOf(future, passive), emptyMap(), today) { it.id == 2L }).isEmpty()
    }

    @Test
    fun `next trigger is later today or tomorrow`() {
        val zone = ZoneId.of("America/Chicago")
        val nine = LocalTime.of(21, 0)
        val morning = ZonedDateTime.of(2026, 11, 30, 8, 0, 0, 0, zone)
        assertThat(ReminderPlanner.nextTrigger(morning, nine)).isEqualTo(ZonedDateTime.of(2026, 11, 30, 21, 0, 0, 0, zone))
        val night = ZonedDateTime.of(2026, 11, 30, 21, 0, 0, 0, zone)
        assertThat(ReminderPlanner.nextTrigger(night, nine)).isEqualTo(ZonedDateTime.of(2026, 12, 1, 21, 0, 0, 0, zone))
    }

    @Test
    fun `next trigger across a DST change keeps the wall-clock time`() {
        val zone = ZoneId.of("America/Chicago")
        val beforeChange = ZonedDateTime.of(2027, 3, 13, 22, 0, 0, 0, zone) // DST starts 2027-03-14
        assertThat(ReminderPlanner.nextTrigger(beforeChange, LocalTime.of(21, 0)).toLocalTime()).isEqualTo(LocalTime.of(21, 0))
    }
}
