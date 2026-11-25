package io.github.carsonfiechtner.keepup.data.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.carsonfiechtner.keepup.BuildConfig
import io.github.carsonfiechtner.keepup.data.db.EntryDao
import io.github.carsonfiechtner.keepup.data.db.HabitDao
import io.github.carsonfiechtner.keepup.data.db.UnitDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Versioned, lossless export of all user data (design doc §13). Dates are ISO-8601 strings. */
@Serializable
data class ExportFile(
    val schemaVersion: Int = SCHEMA_VERSION,
    val app: String = "KeepUp",
    val appVersion: String,
    val exportedAt: String,
    val units: List<UnitDto>,
    val habits: List<HabitDto>,
    val goals: List<GoalDto>,
    val entries: List<EntryDto>,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class UnitDto(
    val id: Long,
    val name: String,
    val label: String,
    val kind: String,
    val decimals: Int,
    val quickAdd: Double,
    val isBuiltin: Boolean,
)

@Serializable
data class HabitDto(
    val id: Long,
    val name: String,
    val description: String?,
    val icon: String,
    val color: Long,
    val period: String,
    val anchorDate: String,
    val unitId: Long,
    val sourceType: String,
    val sourceConfig: String?,
    val sortOrder: Int,
    val createdAt: String,
    val archivedAt: String?,
)

@Serializable
data class GoalDto(val habitId: Long, val effectiveFrom: String, val direction: String, val target: Double)

@Serializable
data class EntryDto(
    val id: Long,
    val habitId: Long,
    val date: String,
    val loggedAt: String,
    val value: Double,
    val note: String?,
    val origin: String,
    val externalKey: String?,
)

@Singleton
class ExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val unitDao: UnitDao,
    private val habitDao: HabitDao,
    private val entryDao: EntryDao,
    private val clock: Clock,
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun buildExport(): ExportFile = ExportFile(
        appVersion = BuildConfig.VERSION_NAME,
        exportedAt = clock.instant().toString(),
        units = unitDao.getAll().map {
            UnitDto(it.id, it.name, it.label, it.kind.name, it.decimals, it.quickAdd, it.isBuiltin)
        },
        habits = habitDao.getAll().map { row ->
            val h = row.habit
            HabitDto(
                h.id, h.name, h.description, h.icon, h.color, h.period.name, h.anchorDate.toString(), h.unitId,
                h.sourceType, h.sourceConfig, h.sortOrder, h.createdAt.toString(), h.archivedAt?.toString(),
            )
        },
        goals = habitDao.getAllGoals().map {
            GoalDto(it.habitId, it.effectiveFrom.toString(), it.direction.name, it.target)
        },
        entries = entryDao.getAll().map {
            EntryDto(
                it.id, it.habitId, it.date.toString(), it.loggedAt.toString(), it.value, it.note,
                it.origin.name, it.externalKey,
            )
        },
    )

    fun encode(file: ExportFile): String = json.encodeToString(ExportFile.serializer(), file)

    /** Writes the export to a document the user picked with the system file picker. */
    suspend fun writeTo(uri: Uri) = withContext(Dispatchers.IO) {
        val text = encode(buildExport())
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("Couldn't open the file for writing")
    }
}
