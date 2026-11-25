package io.github.carsonfiechtner.keepup.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.EntryOrigin
import io.github.carsonfiechtner.keepup.domain.model.UnitKind
import io.github.carsonfiechtner.keepup.domain.period.Period
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "unit")
data class UnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val label: String,
    val kind: UnitKind,
    val decimals: Int,
    @ColumnInfo(name = "quick_add") val quickAdd: Double,
    @ColumnInfo(name = "is_builtin") val isBuiltin: Boolean,
)

@Entity(
    tableName = "habit",
    foreignKeys = [
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("unit_id")],
)
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String?,
    val icon: String,
    val color: Long,
    val period: Period,
    @ColumnInfo(name = "anchor_date") val anchorDate: LocalDate,
    @ColumnInfo(name = "unit_id") val unitId: Long,
    /** MANUAL for now; HEALTH_CONNECT / USAGE_STATS arrive in M4/M5. */
    @ColumnInfo(name = "source_type", defaultValue = "MANUAL") val sourceType: String = "MANUAL",
    @ColumnInfo(name = "source_config") val sourceConfig: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "archived_at") val archivedAt: Instant?,
)

@Entity(
    tableName = "habit_goal",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["habit_id", "effective_from"], unique = true)],
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "habit_id") val habitId: Long,
    @ColumnInfo(name = "effective_from") val effectiveFrom: LocalDate,
    val direction: Direction,
    val target: Double,
)

@Entity(
    tableName = "entry",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["habit_id", "date"]),
        Index(value = ["habit_id", "external_key"], unique = true),
    ],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "habit_id") val habitId: Long,
    val date: LocalDate,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
    val value: Double,
    val note: String?,
    val origin: EntryOrigin,
    /** Deterministic key for idempotent auto-sync upserts (M4/M5); null for manual entries. */
    @ColumnInfo(name = "external_key") val externalKey: String? = null,
)

data class HabitWithDetails(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "unit_id", entityColumn = "id") val unit: UnitEntity,
    @Relation(parentColumn = "id", entityColumn = "habit_id") val goals: List<GoalEntity>,
)

data class DailyTotalRow(
    @ColumnInfo(name = "habit_id") val habitId: Long,
    val date: LocalDate,
    val total: Double,
)

/** A named set of apps tracked together, e.g. "Social" (design doc §10.3). */
@Entity(tableName = "app_group")
data class AppGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "app_group_member",
    primaryKeys = ["group_id", "package"],
    foreignKeys = [
        ForeignKey(
            entity = AppGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AppGroupMemberEntity(
    @ColumnInfo(name = "group_id") val groupId: Long,
    @ColumnInfo(name = "package") val packageName: String,
)

data class AppGroupWithMembers(
    @Embedded val group: AppGroupEntity,
    @Relation(parentColumn = "id", entityColumn = "group_id") val members: List<AppGroupMemberEntity>,
)

/**
 * Foreground time per app per day, saved at every sync because Android only keeps raw usage events for
 * a few days. Lets app groups be recomputed later from what was saved.
 */
@Entity(tableName = "usage_daily", primaryKeys = ["date", "package"])
data class UsageDailyEntity(
    val date: LocalDate,
    @ColumnInfo(name = "package") val packageName: String,
    @ColumnInfo(name = "foreground_ms") val foregroundMs: Long,
)
