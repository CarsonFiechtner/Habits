package io.github.carsonfiechtner.keepup.domain.rules

import com.google.common.truth.Truth.assertThat
import io.github.carsonfiechtner.keepup.domain.model.Direction.AT_LEAST
import io.github.carsonfiechtner.keepup.domain.model.Direction.AT_MOST
import io.github.carsonfiechtner.keepup.domain.model.Entry
import io.github.carsonfiechtner.keepup.domain.model.EntryOrigin
import io.github.carsonfiechtner.keepup.domain.model.Goal
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.domain.testing.d
import io.github.carsonfiechtner.keepup.domain.testing.habit
import org.junit.Test
import java.time.Instant

class HistoryLockTest {

    private val anchor = d("2026-11-25") // Wednesday

    private fun entry(date: String, origin: EntryOrigin = EntryOrigin.MANUAL) =
        Entry(1, 1, d(date), Instant.EPOCH, 1.0, null, origin)

    @Test
    fun `can only log today`() {
        assertThat(HistoryLock.canLog(d("2026-11-30"), d("2026-11-30"))).isTrue()
        assertThat(HistoryLock.canLog(d("2026-11-29"), d("2026-11-30"))).isFalse()
        assertThat(HistoryLock.canLog(d("2026-12-01"), d("2026-11-30"))).isFalse()
    }

    @Test
    fun `can only modify today's manual entries`() {
        val today = d("2026-11-30")
        assertThat(HistoryLock.canModify(entry("2026-11-30"), today)).isTrue()
        assertThat(HistoryLock.canModify(entry("2026-11-29"), today)).isFalse()
        assertThat(HistoryLock.canModify(entry("2026-11-30", EntryOrigin.HEALTH_CONNECT), today)).isFalse()
    }

    @Test
    fun `structure is editable only on the setup day`() {
        val h = habit(anchor = anchor)
        assertThat(HistoryLock.canChangeStructure(h, anchor)).isTrue()
        assertThat(HistoryLock.canChangeStructure(h, anchor.plusDays(1))).isFalse()
    }

    @Test
    fun `goal edit on the setup day replaces the goal`() {
        val h = habit(anchor = anchor, target = 2.0)
        assertThat(HistoryLock.applyGoalEdit(h, AT_LEAST, 3.0, anchor))
            .containsExactly(Goal(AT_LEAST, 3.0, anchor))
    }

    @Test
    fun `goal edit later takes effect at the next period`() {
        val daily = habit(anchor = anchor, target = 2.0)
        assertThat(HistoryLock.applyGoalEdit(daily, AT_LEAST, 3.0, d("2026-11-30")))
            .containsExactly(Goal(AT_LEAST, 2.0, anchor), Goal(AT_LEAST, 3.0, d("2026-12-01")))

        val weekly = habit(period = Period.WEEKLY, anchor = anchor, target = 70_000.0)
        assertThat(HistoryLock.goalEditEffectiveFrom(weekly, d("2026-11-30"))).isEqualTo(d("2026-12-02"))
    }

    @Test
    fun `a second edit in the same period replaces the pending one`() {
        val h = habit(anchor = anchor, target = 2.0)
        val first = h.copy(goals = HistoryLock.applyGoalEdit(h, AT_LEAST, 3.0, d("2026-11-30")))
        assertThat(HistoryLock.applyGoalEdit(first, AT_MOST, 1.0, d("2026-11-30")))
            .containsExactly(Goal(AT_LEAST, 2.0, anchor), Goal(AT_MOST, 1.0, d("2026-12-01")))
    }

    @Test
    fun `editing back to the goal in force cancels the pending edit`() {
        val h = habit(anchor = anchor, target = 2.0)
        val pending = h.copy(goals = HistoryLock.applyGoalEdit(h, AT_LEAST, 3.0, d("2026-11-30")))
        assertThat(HistoryLock.applyGoalEdit(pending, AT_LEAST, 2.0, d("2026-11-30")))
            .containsExactly(Goal(AT_LEAST, 2.0, anchor))
    }

    @Test
    fun `pending goal is visible until it takes effect`() {
        val h = habit(anchor = anchor, target = 2.0)
        val pending = h.copy(goals = HistoryLock.applyGoalEdit(h, AT_LEAST, 3.0, d("2026-11-30")))
        assertThat(pending.pendingGoal(d("2026-11-30"))).isEqualTo(Goal(AT_LEAST, 3.0, d("2026-12-01")))
        assertThat(pending.pendingGoal(d("2026-12-01"))).isNull()
        assertThat(pending.goalFor(d("2026-11-30")).target).isEqualTo(2.0)
        assertThat(pending.goalFor(d("2026-12-01")).target).isEqualTo(3.0)
    }
}
