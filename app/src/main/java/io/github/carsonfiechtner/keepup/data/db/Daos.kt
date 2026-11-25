package io.github.carsonfiechtner.keepup.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface UnitDao {
    @Query("SELECT * FROM unit ORDER BY is_builtin DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<UnitEntity>>

    @Query("SELECT * FROM unit ORDER BY id")
    suspend fun getAll(): List<UnitEntity>

    @Query("SELECT * FROM unit WHERE id = :id")
    suspend fun get(id: Long): UnitEntity?

    @Query("SELECT COUNT(*) FROM habit WHERE unit_id = :unitId")
    suspend fun habitCount(unitId: Long): Int

    @Insert
    suspend fun insert(unit: UnitEntity): Long

    @Update
    suspend fun update(unit: UnitEntity)

    @Query("DELETE FROM unit WHERE id = :id AND is_builtin = 0")
    suspend fun deleteCustom(id: Long)
}

@Dao
interface HabitDao {
    @Transaction
    @Query("SELECT * FROM habit WHERE archived_at IS NULL ORDER BY sort_order, id")
    fun observeActive(): Flow<List<HabitWithDetails>>

    @Transaction
    @Query("SELECT * FROM habit WHERE archived_at IS NOT NULL ORDER BY archived_at DESC")
    fun observeArchived(): Flow<List<HabitWithDetails>>

    @Transaction
    @Query("SELECT * FROM habit WHERE id = :id")
    suspend fun get(id: Long): HabitWithDetails?

    @Transaction
    @Query("SELECT * FROM habit WHERE id = :id")
    fun observe(id: Long): Flow<HabitWithDetails?>

    @Transaction
    @Query("SELECT * FROM habit ORDER BY id")
    suspend fun getAll(): List<HabitWithDetails>

    @Query("SELECT * FROM habit_goal ORDER BY habit_id, effective_from")
    suspend fun getAllGoals(): List<GoalEntity>

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM habit")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insertHabit(habit: HabitEntity): Long

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Query("UPDATE habit SET sort_order = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Transaction
    suspend fun reorder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Query("UPDATE habit SET archived_at = :archivedAt WHERE id = :id")
    suspend fun setArchivedAt(id: Long, archivedAt: Instant?)

    @Query("DELETE FROM habit WHERE id = :id")
    suspend fun deleteHabit(id: Long)

    @Query("DELETE FROM habit_goal WHERE habit_id = :habitId")
    suspend fun deleteGoals(habitId: Long)

    @Insert
    suspend fun insertGoals(goals: List<GoalEntity>)

    @Transaction
    suspend fun insertHabitWithGoals(habit: HabitEntity, goals: List<GoalEntity>): Long {
        val id = insertHabit(habit)
        insertGoals(goals.map { it.copy(id = 0, habitId = id) })
        return id
    }

    @Transaction
    suspend fun updateHabitWithGoals(habit: HabitEntity, goals: List<GoalEntity>) {
        updateHabit(habit)
        deleteGoals(habit.id)
        insertGoals(goals.map { it.copy(id = 0, habitId = habit.id) })
    }
}

@Dao
interface EntryDao {
    /** Per-day totals for every habit. Small enough to keep in memory (design doc §6.4). */
    @Query("SELECT habit_id, date, SUM(value) AS total FROM entry GROUP BY habit_id, date")
    fun observeDailyTotals(): Flow<List<DailyTotalRow>>

    @Query("SELECT * FROM entry WHERE habit_id = :habitId AND date = :date ORDER BY logged_at")
    fun observeOn(habitId: Long, date: LocalDate): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entry WHERE habit_id = :habitId AND date = :date ORDER BY logged_at")
    suspend fun getOn(habitId: Long, date: LocalDate): List<EntryEntity>

    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun get(id: Long): EntryEntity?

    @Query("SELECT * FROM entry WHERE habit_id = :habitId ORDER BY date DESC, logged_at DESC")
    fun observeForHabit(habitId: Long): Flow<List<EntryEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM entry WHERE habit_id = :habitId)")
    suspend fun hasAny(habitId: Long): Boolean

    @Query("SELECT * FROM entry WHERE habit_id = :habitId AND external_key = :key")
    suspend fun getByExternalKey(habitId: Long, key: String): EntryEntity?

    /** Inserts or updates the entry with the same habit and external key (idempotent auto-sync). */
    @Transaction
    suspend fun upsertExternal(entry: EntryEntity) {
        val key = requireNotNull(entry.externalKey)
        val existing = getByExternalKey(entry.habitId, key)
        when {
            existing == null -> insert(entry)
            existing.value != entry.value -> update(existing.copy(value = entry.value, loggedAt = entry.loggedAt))
        }
    }

    @Query("SELECT * FROM entry ORDER BY habit_id, date, logged_at")
    suspend fun getAll(): List<EntryEntity>

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Update
    suspend fun update(entry: EntryEntity)

    @Query("DELETE FROM entry WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Bulk operations for restoring a backup. Only used inside a transaction by ImportService. */
@Dao
interface RestoreDao {
    @Query("DELETE FROM entry")
    suspend fun deleteAllEntries()

    @Query("DELETE FROM habit_goal")
    suspend fun deleteAllGoals()

    @Query("DELETE FROM habit")
    suspend fun deleteAllHabits()

    @Query("DELETE FROM unit")
    suspend fun deleteAllUnits()

    @Query("DELETE FROM app_group")
    suspend fun deleteAllAppGroups()

    @Query("DELETE FROM usage_daily")
    suspend fun deleteAllUsage()

    @Insert
    suspend fun insertAppGroups(groups: List<AppGroupEntity>)

    @Insert
    suspend fun insertAppGroupMembers(members: List<AppGroupMemberEntity>)

    @Insert
    suspend fun insertUsage(rows: List<UsageDailyEntity>)

    @Insert
    suspend fun insertUnits(units: List<UnitEntity>)

    @Insert
    suspend fun insertHabits(habits: List<HabitEntity>)

    @Insert
    suspend fun insertGoals(goals: List<GoalEntity>)

    @Insert
    suspend fun insertEntries(entries: List<EntryEntity>)
}

@Dao
interface AppGroupDao {
    @Transaction
    @Query("SELECT * FROM app_group ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<AppGroupWithMembers>>

    @Transaction
    @Query("SELECT * FROM app_group WHERE id = :id")
    suspend fun get(id: Long): AppGroupWithMembers?

    @Transaction
    @Query("SELECT * FROM app_group ORDER BY id")
    suspend fun getAll(): List<AppGroupWithMembers>

    @Insert
    suspend fun insertGroup(group: AppGroupEntity): Long

    @Update
    suspend fun updateGroup(group: AppGroupEntity)

    @Query("DELETE FROM app_group_member WHERE group_id = :groupId")
    suspend fun clearMembers(groupId: Long)

    @Insert
    suspend fun insertMembers(members: List<AppGroupMemberEntity>)

    @Query("DELETE FROM app_group WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM habit WHERE source_type = 'USAGE_APP_GROUP' AND source_config = :groupId")
    suspend fun habitCount(groupId: String): Int

    @Transaction
    suspend fun save(id: Long?, name: String, packages: Set<String>): Long {
        val groupId = if (id == null) insertGroup(AppGroupEntity(name = name)) else id.also { updateGroup(AppGroupEntity(it, name)) }
        clearMembers(groupId)
        insertMembers(packages.map { AppGroupMemberEntity(groupId, it) })
        return groupId
    }
}

@Dao
interface UsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<UsageDailyEntity>)

    @Query("SELECT * FROM usage_daily WHERE date BETWEEN :from AND :to")
    suspend fun between(from: LocalDate, to: LocalDate): List<UsageDailyEntity>

    @Query("SELECT * FROM usage_daily ORDER BY date, package")
    suspend fun all(): List<UsageDailyEntity>
}
