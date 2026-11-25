package io.github.carsonfiechtner.keepup.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.carsonfiechtner.keepup.MainActivity
import io.github.carsonfiechtner.keepup.di.TestDataModule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * End-to-end smoke test of M1 on Robolectric: create habits, log, view a past day. Also writes
 * screenshots to app/build/screenshots for a visual check.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeepUpFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun createHabitsAndLog() {
        waitFor("No habits yet")
        shot("01-empty")

        // An amount: 2 L of water per day.
        createHabit(name = "Water", amountUnit = "Liters (L)", target = "2") {
            shot("02-new-habit-form")
        }
        waitFor("0 / 2 L")

        // Done / not done.
        createHabit(name = "Meditate")
        // A limit: at most 2 cups of coffee.
        createHabit(name = "Coffee", amountUnit = "Times (times)", target = "2", atMost = true)
        // Weekly steps.
        createHabit(name = "Walk", amountUnit = "Steps (steps)", target = "70000", period = "Weekly")

        waitFor("Log 0")

        // Log water twice with the quick-add button and complete meditation.
        click("+0.25")
        waitFor("0.25 / 2 L")
        compose.onNodeWithContentDescription("Mark done").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Mark not done"), TIMEOUT)
        click("+1,000")
        waitFor("6 days left · 1,000 / 70,000 steps")
        compose.waitForIdle()
        shot("03-today")

        // Log sheet.
        click("Water")
        waitFor("Today's entries")
        waitFor("0.25 L", substring = true) // the logged entry is listed

        // Edit screen, reached from the sheet. The habit was created today, so everything is editable.
        click("Edit habit")
        waitFor("At least 2 L every day", substring = true)
        shot("04-edit-habit")
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("Log 0")

        // Habit detail: stats, chart and calendar.
        compose.onAllNodesWithText("Water", useUnmergedTree = true)[0].performTouchInput { longClick() }
        click("Details")
        waitFor("Best")
        shot("06-habit-detail")
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("Log 0")

        // History tab.
        click("History")
        waitFor("Last 7 days")
        shot("07-history")
        compose.onAllNodesWithText("Today", useUnmergedTree = true)[0].performClick()
        waitFor("Log 0")

        // A habit that starts in the future is listed as upcoming.
        createHabit(name = "Stretch", startInDays = 3)
        waitFor("Upcoming")
        waitFor("Starts", substring = true)

        // Automatic tracking options in the habit form.
        click("New habit")
        compose.waitUntilExactlyOneExists(hasSetTextAction() and hasText("Name"), TIMEOUT)
        click("Automatic")
        click("Screen time (all apps)")
        waitFor("usage access", substring = true)
        shot("09-automatic-form")
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("Log 0")

        // Yesterday is read-only and the habits didn't exist yet.
        compose.onNodeWithContentDescription("Previous day").performClick()
        waitFor("Past days are read-only")
        waitFor("No habits had started yet on this day.")
        click("Today")
        waitFor("Log 0")

        // Dark theme.
        click("Settings")
        click("Dark")
        // The setting is written to DataStore off the main thread; give it a moment to land.
        repeat(10) {
            Thread.sleep(100)
            compose.waitForIdle()
        }
        compose.onAllNodesWithText("Today", useUnmergedTree = true)[0].performClick()
        waitFor("Log 0")
        compose.waitForIdle()
        shot("08-today-dark")
    }

    private fun createHabit(
        name: String,
        amountUnit: String? = null,
        target: String? = null,
        atMost: Boolean = false,
        period: String? = null,
        startInDays: Int = 0,
        beforeSave: () -> Unit = {},
    ) {
        click("New habit")
        compose.waitUntilExactlyOneExists(hasSetTextAction() and hasText("Name"), TIMEOUT)
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput(name)
        if (amountUnit != null) {
            click("An amount")
            click("Times (times)") // opens the unit menu
            compose.onAllNodesWithText(amountUnit, useUnmergedTree = true).let { nodes ->
                nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
            }
        }
        period?.let { click(it) }
        if (atMost) click("At most")
        if (startInDays > 0) {
            click("Today (", substring = true)
            waitFor("OK")
            val day = TestDataModule.NOW.atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(startInDays.toLong())
            // The date picker labels days with their full date, e.g. "Saturday, November 28, 2026".
            val label = day.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.US))
            compose.onNode(hasText(label, substring = true) and hasClickAction()).performClick()
            click("OK")
        }
        target?.let { compose.onNode(hasSetTextAction() and hasText("Target per", substring = true)).performTextInput(it) }
        beforeSave()
        click("Save")
        // Back on Today once the form is gone and the new row is shown.
        compose.waitUntilDoesNotExist(hasSetTextAction() and hasText("Name"), TIMEOUT)
        waitFor(name)
    }

    private fun click(text: String, substring: Boolean = false) {
        compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true)[0].performClick()
    }

    private fun waitFor(text: String, substring: Boolean = false) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Captures the main window (Robolectric can't capture dialog windows such as bottom sheets). */
    private fun shot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
