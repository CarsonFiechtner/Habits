package io.github.carsonfiechtner.keepup.data.export

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.carsonfiechtner.keepup.data.db.AppGroupEntity
import io.github.carsonfiechtner.keepup.data.db.AppGroupMemberEntity
import io.github.carsonfiechtner.keepup.data.db.EntryEntity
import io.github.carsonfiechtner.keepup.data.db.GoalEntity
import io.github.carsonfiechtner.keepup.data.db.HabitEntity
import io.github.carsonfiechtner.keepup.data.db.KeepUpDatabase
import io.github.carsonfiechtner.keepup.data.db.UnitEntity
import io.github.carsonfiechtner.keepup.data.db.UsageDailyEntity
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.EntryOrigin
import io.github.carsonfiechtner.keepup.domain.model.SourceType
import io.github.carsonfiechtner.keepup.domain.model.UnitKind
import io.github.carsonfiechtner.keepup.domain.period.Period
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

class InvalidBackupException(message: String) : Exception(message)

/** What a backup contains, shown before the user confirms a restore. */
data class BackupSummary(val exportedAt: String, val habits: Int, val entries: Int)

/**
 * Restores a JSON export, replacing all data (design doc §13). Before anything is replaced, the current
 * data is written to an automatic backup in app storage, so a bad restore can be undone.
 */
@Singleton
class ImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: KeepUpDatabase,
    private val exportService: ExportService,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ExportFile {
        val file = try {
            json.decodeFromString(ExportFile.serializer(), text)
        } catch (e: Exception) {
            throw InvalidBackupException("This isn't a KeepUp backup file")
        }
        validate(file)
        return file
    }

    suspend fun read(uri: Uri): ExportFile = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw InvalidBackupException("Couldn't open the file")
        parse(text)
    }

    fun summarize(file: ExportFile) = BackupSummary(file.exportedAt, file.habits.size, file.entries.size)

    /** Replaces all data with [file]. Returns the automatic backup of the previous data. */
    suspend fun restore(file: ExportFile): File {
        validate(file)
        val safety = writeSafetyBackup()
        val units = file.units.map {
            UnitEntity(it.id, it.name, it.label, UnitKind.valueOf(it.kind), it.decimals, it.quickAdd, it.isBuiltin)
        }
        val habits = file.habits.map {
            HabitEntity(
                id = it.id,
                name = it.name,
                description = it.description,
                icon = it.icon,
                color = it.color,
                period = Period.valueOf(it.period),
                anchorDate = LocalDate.parse(it.anchorDate),
                unitId = it.unitId,
                sourceType = it.sourceType,
                sourceConfig = it.sourceConfig,
                sortOrder = it.sortOrder,
                createdAt = Instant.parse(it.createdAt),
                archivedAt = it.archivedAt?.let(Instant::parse),
            )
        }
        val goals = file.goals.map {
            GoalEntity(
                habitId = it.habitId,
                effectiveFrom = LocalDate.parse(it.effectiveFrom),
                direction = Direction.valueOf(it.direction),
                target = it.target,
            )
        }
        val entries = file.entries.map {
            EntryEntity(
                id = it.id,
                habitId = it.habitId,
                date = LocalDate.parse(it.date),
                loggedAt = Instant.parse(it.loggedAt),
                value = it.value,
                note = it.note,
                origin = EntryOrigin.valueOf(it.origin),
                externalKey = it.externalKey,
            )
        }
        val groups = file.appGroups.map { AppGroupEntity(it.id, it.name) }
        val members = file.appGroups.flatMap { g -> g.packages.distinct().map { AppGroupMemberEntity(g.id, it) } }
        val usage = file.usageDaily.map { UsageDailyEntity(LocalDate.parse(it.date), it.packageName, it.foregroundMs) }
        val dao = db.restoreDao()
        db.withTransaction {
            dao.deleteAllEntries()
            dao.deleteAllGoals()
            dao.deleteAllHabits()
            dao.deleteAllUnits()
            dao.deleteAllAppGroups() // members cascade
            dao.deleteAllUsage()
            dao.insertUnits(units)
            dao.insertAppGroups(groups)
            dao.insertAppGroupMembers(members)
            dao.insertHabits(habits)
            dao.insertGoals(goals)
            dao.insertEntries(entries)
            dao.insertUsage(usage)
        }
        return safety
    }

    /** Writes the current data to files/backups, keeping the newest [KEEP_BACKUPS]. */
    private suspend fun writeSafetyBackup(): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val stamp = STAMP.format(clock.instant().atOffset(ZoneOffset.UTC))
        val file = File(dir, "keepup-pre-restore-$stamp.json")
        file.writeText(exportService.encode(exportService.buildExport()))
        dir.listFiles { f -> f.name.startsWith("keepup-pre-restore-") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP_BACKUPS)
            ?.forEach { it.delete() }
        file
    }

    private fun validate(file: ExportFile) {
        fun fail(message: String): Nothing = throw InvalidBackupException(message)
        if (file.app != "KeepUp") fail("This isn't a KeepUp backup file")
        if (file.schemaVersion > ExportFile.SCHEMA_VERSION) {
            fail("This backup is from a newer version of KeepUp. Update the app first.")
        }
        try {
            val unitIds = file.units.map { it.id }.toSet()
            file.units.forEach { UnitKind.valueOf(it.kind) }
            val habitIds = file.habits.map { it.id }.toSet()
            file.habits.forEach {
                if (it.unitId !in unitIds) fail("Habit \"${it.name}\" refers to a missing unit")
                Period.valueOf(it.period)
                LocalDate.parse(it.anchorDate)
                val source = SourceType.valueOf(it.sourceType)
                if (source == SourceType.USAGE_APP_GROUP && file.appGroups.none { g -> g.id.toString() == it.sourceConfig }) {
                    fail("Habit \"${it.name}\" refers to a missing app group")
                }
            }
            file.goals.forEach {
                if (it.habitId !in habitIds) fail("A goal refers to a missing habit")
                Direction.valueOf(it.direction)
            }
            file.habits.forEach { h ->
                if (file.goals.none { it.habitId == h.id }) fail("Habit \"${h.name}\" has no goal")
            }
            file.entries.forEach {
                if (it.habitId !in habitIds) fail("An entry refers to a missing habit")
                EntryOrigin.valueOf(it.origin)
                LocalDate.parse(it.date)
            }
        } catch (e: InvalidBackupException) {
            throw e
        } catch (e: Exception) {
            fail("The backup file is damaged: ${e.message}")
        }
    }

    private companion object {
        const val KEEP_BACKUPS = 5
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
