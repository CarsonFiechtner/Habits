package io.github.carsonfiechtner.keepup.domain.model

import io.github.carsonfiechtner.keepup.domain.period.Period
import java.time.Instant
import java.time.LocalDate

data class Habit(
    val id: Long,
    val name: String,
    val description: String?,
    /** An emoji. */
    val icon: String,
    /** ARGB color. */
    val color: Long,
    val period: Period,
    /** First day of the habit's first period; equal to its creation date (design doc §6.0). */
    val anchorDate: LocalDate,
    val unit: MeasureUnit,
    /** All goal versions, in any order. Never empty. */
    val goals: List<Goal>,
    val archivedAt: Instant?,
    val sortOrder: Int,
    val createdAt: Instant,
) {
    init {
        require(goals.isNotEmpty()) { "A habit needs at least one goal" }
    }

    private val goalsByDate: List<Goal> = goals.sortedBy { it.effectiveFrom }

    val isArchived: Boolean get() = archivedAt != null

    /** The goal that applies to the period starting on [periodStart]. */
    fun goalFor(periodStart: LocalDate): Goal =
        goalsByDate.lastOrNull { !it.effectiveFrom.isAfter(periodStart) } ?: goalsByDate.first()

    /** A goal edit that hasn't taken effect yet as of [today], if any. */
    fun pendingGoal(today: LocalDate): Goal? = goalsByDate.firstOrNull { it.effectiveFrom.isAfter(today) }
}
