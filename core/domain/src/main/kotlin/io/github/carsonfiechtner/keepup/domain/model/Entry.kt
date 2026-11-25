package io.github.carsonfiechtner.keepup.domain.model

import java.time.Instant
import java.time.LocalDate

data class Entry(
    val id: Long,
    val habitId: Long,
    /** The day this entry counts toward. */
    val date: LocalDate,
    val loggedAt: Instant,
    val value: Double,
    val note: String?,
    val origin: EntryOrigin,
)
