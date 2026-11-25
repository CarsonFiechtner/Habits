package io.github.carsonfiechtner.keepup.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Every schema change must keep existing data (design doc §9.2). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KeepUpDatabase::class.java)

    @Test
    fun `1 to 2 keeps habits and entries and adds usage tables`() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO unit (id, name, label, kind, decimals, quick_add, is_builtin) VALUES (1, 'Done', '', 'BINARY', 0, 1.0, 1)")
            execSQL(
                "INSERT INTO habit (id, name, description, icon, color, period, anchor_date, unit_id, source_type, source_config, sort_order, created_at, archived_at) " +
                    "VALUES (1, 'Meditate', NULL, 'x', 0, 'DAILY', 20417, 1, 'MANUAL', NULL, 0, 0, NULL)",
            )
            execSQL("INSERT INTO habit_goal (habit_id, effective_from, direction, target) VALUES (1, 20417, 'AT_LEAST', 1.0)")
            execSQL("INSERT INTO entry (habit_id, date, logged_at, value, note, origin, external_key) VALUES (1, 20417, 0, 1.0, 'hi', 'MANUAL', NULL)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 2, true)

        db.query("SELECT name FROM habit").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("Meditate")
        }
        db.query("SELECT note FROM entry").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("hi")
        }
        db.execSQL("INSERT INTO app_group (id, name) VALUES (1, 'Social')")
        db.execSQL("INSERT INTO app_group_member (group_id, package) VALUES (1, 'com.example')")
        db.execSQL("INSERT INTO usage_daily (date, package, foreground_ms) VALUES (20417, 'com.example', 1000)")
        db.close()
    }

    private companion object {
        const val DB = "migration-test"
    }
}
