package io.github.carsonfiechtner.keepup.reminders

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.carsonfiechtner.keepup.data.repo.EntryRepository
import io.github.carsonfiechtner.keepup.data.repo.HabitRepository
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneOffset
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class ReminderReceiverTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var actions: HabitActions
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var entries: EntryRepository

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val today = TestDataModule.NOW.atZone(ZoneOffset.UTC).toLocalDate()

    @Before
    fun setUp() {
        hilt.inject()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun createHabit(name: String, binary: Boolean): Long = runBlocking {
        val units = habits.units.first()
        val unit = if (binary) units.first { it.isBinary } else units.first { it.name == "Liters" }
        actions.saveHabit(
            HabitDraft(null, name, null, "✅", 0xFF2E7D32, Period.DAILY, unit.id, Direction.AT_LEAST, 2.0, today),
        ).habitId
    }

    private fun send(intent: Intent) {
        app.sendBroadcast(intent.setClass(app, ReminderReceiver::class.java))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitNotification(): android.app.Notification? {
        val manager = shadowOf(app.getSystemService(NotificationManager::class.java))
        repeat(50) {
            manager.allNotifications.firstOrNull()?.let { return it }
            Thread.sleep(100)
            shadowOf(Looper.getMainLooper()).idle()
        }
        return null
    }

    @Test
    fun `reminder lists unfinished habits and Done logs a single binary habit`() {
        val meditate = createHabit("Meditate", binary = true)

        send(Intent(ReminderReceiver.ACTION_FIRE))
        val notification = awaitNotification()
        assertThat(notification).isNotNull()
        assertThat(notification!!.extras.getString(NotificationCompat.EXTRA_TITLE)).isEqualTo("1 goal left today")
        assertThat(notification.actions.map { it.title.toString() }).containsExactly("Done")

        send(Intent(ReminderReceiver.ACTION_MARK_DONE).putExtra(ReminderReceiver.EXTRA_HABIT_ID, meditate))
        val done = (1..50).firstNotNullOfOrNull {
            runBlocking { entries.getOn(meditate, today) }.takeIf { it.isNotEmpty() } ?: run {
                Thread.sleep(100)
                null
            }
        }
        assertThat(done).hasSize(1)
    }

    @Test
    fun `several items are summarised`() {
        createHabit("Meditate", binary = true)
        createHabit("Water", binary = false)
        send(Intent(ReminderReceiver.ACTION_FIRE))
        val notification = awaitNotification()!!
        assertThat(notification.extras.getString(NotificationCompat.EXTRA_TITLE)).isEqualTo("2 goals left today")
        assertThat(notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
            .contains("Water 0 / 2 L")
        assertThat(notification.actions).isNull()
    }

    @Test
    fun `nothing is posted when everything is done`() {
        val meditate = createHabit("Meditate", binary = true)
        runBlocking { actions.markDone(meditate) }
        send(Intent(ReminderReceiver.ACTION_FIRE))
        Thread.sleep(500)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications).isEmpty()
    }
}
