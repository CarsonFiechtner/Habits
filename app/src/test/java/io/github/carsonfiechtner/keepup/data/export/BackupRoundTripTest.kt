package io.github.carsonfiechtner.keepup.data.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.carsonfiechtner.keepup.data.db.EntryEntity
import io.github.carsonfiechtner.keepup.data.db.GoalEntity
import io.github.carsonfiechtner.keepup.data.db.HabitEntity
import io.github.carsonfiechtner.keepup.data.db.KeepUpDatabase
import io.github.carsonfiechtner.keepup.data.db.UnitEntity
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.EntryOrigin
import io.github.carsonfiechtner.keepup.domain.model.UnitKind
import io.github.carsonfiechtner.keepup.domain.period.Period
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BackupRoundTripTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC)
    private val dbs = mutableListOf<KeepUpDatabase>()

    private fun newDb(): KeepUpDatabase = Room.inMemoryDatabaseBuilder(context, KeepUpDatabase::class.java)
        .addCallback(KeepUpDatabase.SeedCallback)
        .allowMainThreadQueries()
        .build()
        .also { dbs += it }

    private fun services(db: KeepUpDatabase): Pair<ExportService, ImportService> {
        val export = ExportService(context, db.unitDao(), db.habitDao(), db.entryDao(), clock)
        return export to ImportService(context, db, export, clock)
    }

    @After
    fun close() = dbs.forEach { it.close() }

    @Test
    fun `export then restore into another database reproduces the data`() = runTest {
        val source = newDb()
        val customUnit = source.unitDao().insert(UnitEntity(name = "Glasses", label = "glasses", kind = UnitKind.NUMBER, decimals = 0, quickAdd = 1.0, isBuiltin = false))
        val habitId = source.habitDao().insertHabitWithGoals(
            HabitEntity(
                name = "Water", description = "stay hydrated", icon = "💧", color = 0xFF1E88E5, period = Period.WEEKLY,
                anchorDate = LocalDate.parse("2026-11-20"), unitId = customUnit, sortOrder = 0,
                createdAt = Instant.parse("2026-11-20T08:00:00Z"), archivedAt = null,
            ),
            listOf(
                GoalEntity(habitId = 0, effectiveFrom = LocalDate.parse("2026-11-20"), direction = Direction.AT_LEAST, target = 20.0),
                GoalEntity(habitId = 0, effectiveFrom = LocalDate.parse("2026-11-27"), direction = Direction.AT_MOST, target = 30.0),
            ),
        )
        source.entryDao().insert(EntryEntity(habitId = habitId, date = LocalDate.parse("2026-11-21"), loggedAt = Instant.parse("2026-11-21T10:00:00Z"), value = 3.0, note = "a, \"quoted\" note", origin = EntryOrigin.MANUAL))
        source.entryDao().insert(EntryEntity(habitId = habitId, date = LocalDate.parse("2026-11-22"), loggedAt = Instant.parse("2026-11-22T10:00:00Z"), value = 0.0, note = null, origin = EntryOrigin.MANUAL))

        val (sourceExport, _) = services(source)
        val original = sourceExport.buildExport()
        val text = sourceExport.encode(original)

        val target = newDb()
        val (targetExport, targetImport) = services(target)
        val safetyBackup: File = targetImport.restore(targetImport.parse(text))

        assertThat(targetExport.buildExport()).isEqualTo(original)
        assertThat(safetyBackup.exists()).isTrue()
        // The safety backup holds the target's previous contents (just the built-in units).
        assertThat(targetImport.parse(safetyBackup.readText()).habits).isEmpty()
    }

    @Test
    fun `invalid files are rejected`() {
        val (_, import) = services(newDb())
        assertThrows(InvalidBackupException::class.java) { import.parse("not json") }
        assertThrows(InvalidBackupException::class.java) {
            import.parse("""{"app":"KeepUp","appVersion":"x","exportedAt":"x","units":[],"habits":[{"id":1,"name":"H","description":null,"icon":"x","color":0,"period":"DAILY","anchorDate":"2026-11-25","unitId":9,"sourceType":"MANUAL","sourceConfig":null,"sortOrder":0,"createdAt":"2026-11-25T00:00:00Z","archivedAt":null}],"goals":[],"entries":[]}""")
        }
        assertThrows(InvalidBackupException::class.java) {
            import.parse("""{"schemaVersion":99,"app":"KeepUp","appVersion":"x","exportedAt":"x","units":[],"habits":[],"goals":[],"entries":[]}""")
        }
    }
}
