package io.github.carsonfiechtner.keepup.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakStage
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.Goal
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.model.MeasureUnit
import io.github.carsonfiechtner.keepup.domain.model.UnitKind
import io.github.carsonfiechtner.keepup.domain.period.Period
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class AppIconManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = AppIconManager(context)

    private fun enabledAliases(): List<Int> = (1..6).filter { n ->
        val state = context.packageManager.getComponentEnabledSetting(
            ComponentName(context.packageName, "${AppIconManager.ALIAS_PACKAGE}.Stage$n"),
        )
        state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && n == 1)
    }

    @Test
    fun `starts on the seed icon and switches to exactly one other`() {
        assertThat(manager.current()).isEqualTo(StreakStage.SEED)
        manager.apply(StreakStage.OAK)
        assertThat(enabledAliases()).containsExactly(4)
        assertThat(manager.current()).isEqualTo(StreakStage.OAK)
        manager.apply(StreakStage.WORLD_TREE)
        assertThat(enabledAliases()).containsExactly(6)
        manager.apply(StreakStage.SEED)
        assertThat(enabledAliases()).containsExactly(1)
    }

    @Test
    fun `best stage is the highest current streak`() {
        val today = LocalDate.parse("2026-11-25")
        val done = MeasureUnit(1, "Done", "", UnitKind.BINARY, 0, 1.0, true)
        fun habit(id: Long, anchor: LocalDate) = Habit(
            id, "H$id", null, "✅", 0, Period.DAILY, anchor, done,
            listOf(Goal(Direction.AT_LEAST, 1.0, anchor)), null, 0, Instant.EPOCH,
        )
        val young = habit(1, today.minusDays(3))
        val old = habit(2, today.minusDays(40))
        val totals = mapOf(
            1L to (0L..3L).associate { today.minusDays(it) to 1.0 },
            2L to (1L..35L).associate { today.minusDays(it) to 1.0 }, // 35 days, today not done yet
        )
        assertThat(AppIconManager.bestStage(listOf(young, old), totals, today)).isEqualTo(StreakStage.SAPLING)
        assertThat(AppIconManager.bestStage(emptyList(), emptyMap(), today)).isEqualTo(StreakStage.SEED)
    }
}
