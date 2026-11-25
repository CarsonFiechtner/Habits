package io.github.carsonfiechtner.keepup.autotrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.github.carsonfiechtner.keepup.data.repo.EntryRepository
import io.github.carsonfiechtner.keepup.data.repo.HabitRepository
import io.github.carsonfiechtner.keepup.di.AutoTrackModule
import io.github.carsonfiechtner.keepup.di.TestDataModule
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.EntryOrigin
import io.github.carsonfiechtner.keepup.domain.model.Goal
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.model.HabitSource
import io.github.carsonfiechtner.keepup.domain.model.SourceType
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.domainops.HabitActions
import io.github.carsonfiechtner.keepup.domainops.HistoryLockedException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

class FakeStepsSource : StepsSource {
    var status = SourceStatus.READY
    val steps = mutableMapOf<LocalDate, Long>()
    var calls = 0

    override suspend fun status() = status
    override suspend fun permissionsToRequest() = setOf("android.permission.health.READ_STEPS")
    override suspend fun dailySteps(from: LocalDate, to: LocalDate): Map<LocalDate, Long> {
        calls++
        return steps.filterKeys { !it.isBefore(from) && !it.isAfter(to) }
    }
}

class FakeUsageSource : UsageSource {
    var access = true
    val usage = mutableMapOf<LocalDate, Map<String, Long>>()
    override fun hasAccess() = access
    override suspend fun foregroundByDay(from: LocalDate, to: LocalDate) =
        usage.filterKeys { !it.isBefore(from) && !it.isAfter(to) }
    override fun excludedFromScreenTime() = setOf("launcher")
    override fun launchableApps() = listOf(InstalledApp("ig", "Instagram"), InstalledApp("rd", "Reddit"))
}

@HiltAndroidTest
@UninstallModules(AutoTrackModule::class)
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class AutoTrackingSyncTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @BindValue @JvmField val fakeSteps: StepsSource = FakeStepsSource()
    private val fake get() = fakeSteps as FakeStepsSource

    @BindValue @JvmField val fakeUsageSource: UsageSource = FakeUsageSource()
    private val fakeUsage get() = fakeUsageSource as FakeUsageSource

    @Inject lateinit var appGroups: io.github.carsonfiechtner.keepup.data.repo.AppGroupRepository

    @Inject lateinit var sync: AutoTrackingSync
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var entries: EntryRepository
    @Inject lateinit var actions: HabitActions

    private val today = TestDataModule.NOW.atZone(ZoneOffset.UTC).toLocalDate() // 2026-11-25

    @Before fun setUp() = hilt.inject()

    private fun usageHabit(anchor: LocalDate, source: HabitSource): Long = runBlocking {
        val unit = habits.units.first().first { it.name == "Minutes" }
        habits.insert(
            Habit(
                id = 0, name = "Screen", description = null, icon = "📵", color = 0, period = Period.DAILY,
                anchorDate = anchor, unit = unit, goals = listOf(Goal(Direction.AT_MOST, 60.0, anchor)),
                archivedAt = null, sortOrder = 0, createdAt = TestDataModule.NOW, source = source,
            ),
        )
    }

    private fun stepsHabit(anchor: LocalDate): Long = runBlocking {
        val unit = habits.units.first().first { it.name == "Steps" }
        habits.insert(
            Habit(
                id = 0, name = "Steps", description = null, icon = "🚶", color = 0, period = Period.DAILY,
                anchorDate = anchor, unit = unit, goals = listOf(Goal(Direction.AT_LEAST, 10_000.0, anchor)),
                archivedAt = null, sortOrder = 0, createdAt = TestDataModule.NOW,
                source = HabitSource(SourceType.HEALTH_STEPS),
            ),
        )
    }

    private fun totals(habitId: Long) = runBlocking { entries.dailyTotals.first()[habitId].orEmpty() }

    @Test
    fun `syncs only days inside the settle window`() = runBlocking<Unit> {
        val id = stepsHabit(anchor = today.minusDays(5))
        (0L..5L).forEach { fake.steps[today.minusDays(it)] = 1000L * (it + 1) }

        assertThat(sync.syncAll()).isTrue()

        assertThat(totals(id)).containsExactly(
            today, 1000.0,
            today.minusDays(1), 2000.0,
            today.minusDays(2), 3000.0,
        )
    }

    @Test
    fun `re-syncing replaces values instead of adding`() = runBlocking<Unit> {
        val id = stepsHabit(anchor = today)
        fake.steps[today] = 4000
        sync.syncAll()
        fake.steps[today] = 6500
        sync.syncAll()
        assertThat(totals(id)).containsExactly(today, 6500.0)
        val stored = entries.getOn(id, today)
        assertThat(stored).hasSize(1)
        assertThat(stored.single().origin).isEqualTo(EntryOrigin.HEALTH_CONNECT)
    }

    @Test
    fun `nothing happens without permission`() = runBlocking<Unit> {
        val id = stepsHabit(anchor = today)
        fake.status = SourceStatus.NEEDS_PERMISSION
        fake.steps[today] = 4000
        assertThat(sync.syncAll()).isFalse()
        assertThat(totals(id)).isEmpty()
    }

    @Test
    fun `no automatic habits means no reads`() = runBlocking<Unit> {
        assertThat(sync.syncAll()).isTrue()
        assertThat(fake.calls).isEqualTo(0)
    }

    @Test
    fun `automatic habits cannot be logged by hand`() {
        val id = stepsHabit(anchor = today)
        assertThrows(HistoryLockedException::class.java) { runBlocking { actions.logValue(id, 500.0) } }
    }

    @Test
    fun `screen time sums all apps except excluded ones, in minutes`() = runBlocking<Unit> {
        val id = usageHabit(today.minusDays(1), HabitSource(SourceType.USAGE_SCREEN_TIME))
        fakeUsage.usage[today] = mapOf("ig" to 30 * 60_000L, "rd" to 15 * 60_000L, "launcher" to 99 * 60_000L)
        assertThat(sync.syncAll()).isTrue()
        // Yesterday had no usage events at all: that's a real zero, not missing data.
        assertThat(totals(id)).containsExactly(today, 45.0, today.minusDays(1), 0.0)
    }

    @Test
    fun `app group counts only its apps`() = runBlocking<Unit> {
        val groupId = appGroups.save(null, "Social", setOf("ig"))
        val id = usageHabit(today, HabitSource(SourceType.USAGE_APP_GROUP, groupId))
        fakeUsage.usage[today] = mapOf("ig" to 20 * 60_000L, "rd" to 15 * 60_000L)
        sync.syncAll()
        assertThat(totals(id)).containsExactly(today, 20.0)

        // Usage keeps growing during the day; re-syncing replaces today's value.
        fakeUsage.usage[today] = mapOf("ig" to 35 * 60_000L)
        sync.syncAll()
        assertThat(totals(id)).containsExactly(today, 35.0)
    }

    @Test
    fun `without usage access nothing is written`() = runBlocking<Unit> {
        val id = usageHabit(today, HabitSource(SourceType.USAGE_SCREEN_TIME))
        fakeUsage.access = false
        assertThat(sync.syncAll()).isFalse()
        assertThat(totals(id)).isEmpty()
    }

    @Test
    fun `a group used by a habit cannot be deleted`() = runBlocking<Unit> {
        val groupId = appGroups.save(null, "Social", setOf("ig"))
        usageHabit(today, HabitSource(SourceType.USAGE_APP_GROUP, groupId))
        assertThat(appGroups.delete(groupId)).isFalse()
        val unused = appGroups.save(null, "Games", setOf("rd"))
        assertThat(appGroups.delete(unused)).isTrue()
    }
}
