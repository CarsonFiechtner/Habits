package io.github.carsonfiechtner.keepup.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.carsonfiechtner.keepup.di.TestDataModule
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.domainops.HabitActions
import io.github.carsonfiechtner.keepup.domainops.HabitDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneOffset
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class HabitRepositoryTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var actions: HabitActions

    @Before fun setUp() = hilt.inject()

    @Test
    fun `reorder changes the listing order`() = runBlocking<Unit> {
        val today = TestDataModule.NOW.atZone(ZoneOffset.UTC).toLocalDate()
        val done = habits.units.first().first { it.isBinary }
        val ids = listOf("A", "B", "C").map { name ->
            actions.saveHabit(HabitDraft(null, name, null, "✅", 0, Period.DAILY, done.id, Direction.AT_LEAST, 1.0, today)).habitId
        }
        assertThat(habits.activeHabits.first().map { it.name }).containsExactly("A", "B", "C").inOrder()
        habits.reorder(listOf(ids[2], ids[0], ids[1]))
        assertThat(habits.activeHabits.first().map { it.name }).containsExactly("C", "A", "B").inOrder()
    }
}
