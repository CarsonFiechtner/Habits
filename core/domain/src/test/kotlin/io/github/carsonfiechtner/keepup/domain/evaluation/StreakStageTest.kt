package io.github.carsonfiechtner.keepup.domain.evaluation

import com.google.common.truth.Truth.assertThat
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakStage.OAK
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakStage.SAPLING
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakStage.SEED
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakStage.SILVERBOUGH
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakStage.SPROUT
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakStage.WORLD_TREE
import io.github.carsonfiechtner.keepup.domain.period.Period
import org.junit.Test

class StreakStageTest {
    @Test
    fun `daily stages`() {
        assertThat(StreakStage.of(0, Period.DAILY)).isEqualTo(SEED)
        assertThat(StreakStage.of(6, Period.DAILY)).isEqualTo(SEED)
        assertThat(StreakStage.of(7, Period.DAILY)).isEqualTo(SPROUT)
        assertThat(StreakStage.of(29, Period.DAILY)).isEqualTo(SPROUT)
        assertThat(StreakStage.of(30, Period.DAILY)).isEqualTo(SAPLING)
        assertThat(StreakStage.of(90, Period.DAILY)).isEqualTo(OAK)
        assertThat(StreakStage.of(364, Period.DAILY)).isEqualTo(OAK)
        assertThat(StreakStage.of(365, Period.DAILY)).isEqualTo(SILVERBOUGH)
        assertThat(StreakStage.of(1095, Period.DAILY)).isEqualTo(WORLD_TREE)
    }

    @Test
    fun `weekly and monthly use their own units`() {
        assertThat(StreakStage.of(1, Period.WEEKLY)).isEqualTo(SPROUT)
        assertThat(StreakStage.of(13, Period.WEEKLY)).isEqualTo(OAK)
        assertThat(StreakStage.of(156, Period.WEEKLY)).isEqualTo(WORLD_TREE)
        assertThat(StreakStage.of(0, Period.MONTHLY)).isEqualTo(SEED)
        assertThat(StreakStage.of(2, Period.MONTHLY)).isEqualTo(SAPLING)
        assertThat(StreakStage.of(12, Period.MONTHLY)).isEqualTo(SILVERBOUGH)
    }

    @Test
    fun `next stage and distance`() {
        assertThat(StreakStage.next(23, Period.DAILY)).isEqualTo(SAPLING to 7)
        assertThat(StreakStage.next(0, Period.DAILY)).isEqualTo(SPROUT to 7)
        assertThat(StreakStage.next(1100, Period.DAILY)).isNull()
        assertThat(StreakStage.startOf(SAPLING, Period.DAILY)).isEqualTo(30)
        assertThat(StreakStage.startOf(SEED, Period.WEEKLY)).isEqualTo(0)
    }
}
