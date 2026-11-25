package io.github.carsonfiechtner.keepup.data.repo

import io.github.carsonfiechtner.keepup.data.db.EntryDao
import io.github.carsonfiechtner.keepup.data.db.EntryEntity
import io.github.carsonfiechtner.keepup.data.db.toDomain
import io.github.carsonfiechtner.keepup.domain.evaluation.DailyTotals
import io.github.carsonfiechtner.keepup.domain.model.Entry
import io.github.carsonfiechtner.keepup.domain.model.EntryOrigin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(
    private val entryDao: EntryDao,
) {
    /** Daily totals per habit id. */
    val dailyTotals: Flow<Map<Long, DailyTotals>> = entryDao.observeDailyTotals().map { rows ->
        rows.groupBy({ it.habitId }, { it.date to it.total }).mapValues { (_, days) -> days.toMap() }
    }

    fun observeOn(habitId: Long, date: LocalDate): Flow<List<Entry>> =
        entryDao.observeOn(habitId, date).map { rows -> rows.map { it.toDomain() } }

    suspend fun getOn(habitId: Long, date: LocalDate): List<Entry> = entryDao.getOn(habitId, date).map { it.toDomain() }

    suspend fun get(id: Long): Entry? = entryDao.get(id)?.toDomain()

    suspend fun insert(
        habitId: Long,
        date: LocalDate,
        loggedAt: Instant,
        value: Double,
        note: String?,
        origin: EntryOrigin = EntryOrigin.MANUAL,
    ): Long = entryDao.insert(
        EntryEntity(habitId = habitId, date = date, loggedAt = loggedAt, value = value, note = note, origin = origin),
    )

    suspend fun updateValue(entry: Entry, value: Double, note: String?) {
        val row = entryDao.get(entry.id) ?: return
        entryDao.update(row.copy(value = value, note = note))
    }

    suspend fun delete(id: Long) = entryDao.delete(id)
}
