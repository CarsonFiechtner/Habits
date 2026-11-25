package io.github.carsonfiechtner.keepup.data.repo

import io.github.carsonfiechtner.keepup.data.db.AppGroupDao
import io.github.carsonfiechtner.keepup.data.db.AppGroupWithMembers
import io.github.carsonfiechtner.keepup.domain.model.AppGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppGroupRepository @Inject constructor(private val dao: AppGroupDao) {

    val groups: Flow<List<AppGroup>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: Long): AppGroup? = dao.get(id)?.toDomain()

    suspend fun save(id: Long?, name: String, packages: Set<String>): Long {
        require(name.isNotBlank()) { "Name is required" }
        require(packages.isNotEmpty()) { "Pick at least one app" }
        return dao.save(id, name.trim(), packages)
    }

    /** Deletes a group. Returns false (and does nothing) if a habit still tracks it. */
    suspend fun delete(id: Long): Boolean {
        if (dao.habitCount(id.toString()) > 0) return false
        dao.delete(id)
        return true
    }

    private fun AppGroupWithMembers.toDomain() =
        AppGroup(group.id, group.name, members.map { it.packageName }.toSet())
}
