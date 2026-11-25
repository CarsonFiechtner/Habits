package io.github.carsonfiechtner.keepup.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.carsonfiechtner.keepup.domain.evaluation.DailyTotals
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakCalculator
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakStage
import io.github.carsonfiechtner.keepup.domain.model.Habit
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Switches the launcher icon between six activity-aliases, one per streak stage. Android shows the
 * icon of whichever alias is enabled. The manifest enables the Seed alias by default.
 */
@Singleton
class AppIconManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val pm: PackageManager get() = context.packageManager

    private fun alias(stage: StreakStage) =
        ComponentName(context.packageName, "$ALIAS_PACKAGE.Stage${stage.ordinal + 1}")

    private fun isEnabled(stage: StreakStage): Boolean = when (pm.getComponentEnabledSetting(alias(stage))) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> stage == StreakStage.SEED // manifest default
        else -> false
    }

    /** The stage whose icon is showing now. */
    fun current(): StreakStage = StreakStage.entries.firstOrNull(::isEnabled) ?: StreakStage.SEED

    /** Shows [stage]'s icon. Does nothing if it's already showing. */
    fun apply(stage: StreakStage) {
        if (current() == stage && StreakStage.entries.count(::isEnabled) == 1) return
        // Enable the new alias before disabling the old ones, so there's always a launcher entry.
        pm.setComponentEnabledSetting(alias(stage), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        StreakStage.entries.filter { it != stage }.forEach {
            pm.setComponentEnabledSetting(it.let(::alias), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    companion object {
        /** Aliases are declared relative to the app's namespace, which is the same for debug and release. */
        const val ALIAS_PACKAGE = "io.github.carsonfiechtner.keepup.launcher"

        /** Icon to show when it doesn't follow the streak (the user turned that off). */
        val FIXED = StreakStage.SILVERBOUGH

        /** The highest stage among the current streaks of [habits]; Seed when there are none. */
        fun bestStage(habits: List<Habit>, totals: Map<Long, DailyTotals>, today: LocalDate): StreakStage =
            habits.filter { !it.isArchived }
                .maxOfOrNull { habit ->
                    StreakStage.of(StreakCalculator.compute(habit, totals[habit.id].orEmpty(), today).current, habit.period)
                } ?: StreakStage.SEED
    }
}
