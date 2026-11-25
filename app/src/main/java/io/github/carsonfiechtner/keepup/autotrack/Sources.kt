package io.github.carsonfiechtner.keepup.autotrack

import java.time.LocalDate

/** Whether a data source can be read right now. */
enum class SourceStatus {
    /** Not on this device. */
    UNAVAILABLE,

    /** Installed but needs an update (Health Connect). */
    NEEDS_UPDATE,

    /** Available, but the user hasn't granted access yet. */
    NEEDS_PERMISSION,
    READY,
}

/** Daily step counts (design doc §10.2). */
interface StepsSource {
    suspend fun status(): SourceStatus

    /** Permissions to request; pass to Health Connect's permission contract. */
    suspend fun permissionsToRequest(): Set<String>

    /** Steps per local day in [from]..[to]. Days without any data are absent. */
    suspend fun dailySteps(from: LocalDate, to: LocalDate): Map<LocalDate, Long>
}
