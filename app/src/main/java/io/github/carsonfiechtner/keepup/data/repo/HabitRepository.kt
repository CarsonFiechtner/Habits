package io.github.carsonfiechtner.keepup.data.repo

import io.github.carsonfiechtner.keepup.data.db.HabitDao
import io.github.carsonfiechtner.keepup.data.db.UnitDao
import io.github.carsonfiechtner.keepup.data.db.UnitEntity
import io.github.carsonfiechtner.keepup.data.db.toDomain
import io.github.carsonfiechtner.keepup.data.db.toEntity
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.model.MeasureUnit
import io.github.carsonfiechtner.keepup.domain.model.UnitKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val unitDao: UnitDao,
) {
    val activeHabits: Flow<List<Habit>> = habitDao.observeActive().map { rows -> rows.map { it.toDomain() } }

    val archivedHabits: Flow<List<Habit>> = habitDao.observeArchived().map { rows -> rows.map { it.toDomain() } }

    val units: Flow<List<MeasureUnit>> = unitDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getHabit(id: Long): Habit? = habitDao.get(id)?.toDomain()

    suspend fun nextSortOrder(): Int = habitDao.maxSortOrder() + 1

    /** Inserts a new habit with its goals; the habit's id is ignored. */
    suspend fun insert(habit: Habit): Long =
        habitDao.insertHabitWithGoals(habit.toEntity().copy(id = 0), habit.goals.map { it.toEntity(0) })

    /** Replaces the habit row and its whole goal list. */
    suspend fun update(habit: Habit) {
        habitDao.updateHabitWithGoals(habit.toEntity(), habit.goals.map { it.toEntity(habit.id) })
    }

    suspend fun setArchivedAt(id: Long, archivedAt: Instant?) = habitDao.setArchivedAt(id, archivedAt)

    suspend fun delete(id: Long) = habitDao.deleteHabit(id)

    suspend fun getUnit(id: Long): MeasureUnit? = unitDao.get(id)?.toDomain()

    suspend fun createUnit(name: String, label: String, decimals: Int, quickAdd: Double): Long =
        unitDao.insert(
            UnitEntity(
                name = name,
                label = label,
                kind = UnitKind.NUMBER,
                decimals = decimals,
                quickAdd = quickAdd,
                isBuiltin = false,
            ),
        )

    suspend fun updateUnit(unit: MeasureUnit) = unitDao.update(unit.toEntity())

    /** Deletes a custom unit. Returns false (and does nothing) if a habit still uses it. */
    suspend fun deleteUnit(id: Long): Boolean {
        if (unitDao.habitCount(id) > 0) return false
        unitDao.deleteCustom(id)
        return true
    }
}
