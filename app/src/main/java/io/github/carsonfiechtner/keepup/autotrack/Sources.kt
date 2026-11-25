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

/** An app the user can put in an app group. */
data class InstalledApp(val packageName: String, val label: String)

/** Per-app foreground time from Android's Usage Stats (design doc §10.3). */
interface UsageSource {
    /** Whether the user granted Usage access in system settings. */
    fun hasAccess(): Boolean

    /** Foreground milliseconds per local day per app for [from]..[to]; today counts up to now. */
    suspend fun foregroundByDay(from: LocalDate, to: LocalDate): Map<LocalDate, Map<String, Long>>

    /** Packages that don't count toward total screen time: home screens and system UI. */
    fun excludedFromScreenTime(): Set<String>

    /** Apps with a launcher icon, for the app group picker. */
    fun launchableApps(): List<InstalledApp>
}
