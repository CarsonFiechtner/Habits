package io.github.carsonfiechtner.keepup.domain.model

/** Where a habit's values come from (design doc §10). */
enum class SourceType {
    /** Logged by hand. */
    MANUAL,

    /** Daily step count from Health Connect. */
    HEALTH_STEPS,

    /** Total screen time from Usage Stats, in minutes. */
    USAGE_SCREEN_TIME,

    /** Time spent in a named group of apps, in minutes. */
    USAGE_APP_GROUP,
    ;

    val isAutomatic: Boolean get() = this != MANUAL
}

data class HabitSource(
    val type: SourceType = SourceType.MANUAL,
    /** For [SourceType.USAGE_APP_GROUP]: the app group's id. */
    val appGroupId: Long? = null,
) {
    val isAutomatic: Boolean get() = type.isAutomatic

    companion object {
        val MANUAL = HabitSource()
    }
}
