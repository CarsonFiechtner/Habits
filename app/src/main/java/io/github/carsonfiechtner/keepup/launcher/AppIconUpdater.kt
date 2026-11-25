package io.github.carsonfiechtner.keepup.launcher

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.carsonfiechtner.keepup.data.prefs.SettingsRepository
import io.github.carsonfiechtner.keepup.data.repo.EntryRepository
import io.github.carsonfiechtner.keepup.data.repo.HabitRepository
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the launcher icon on the stage of the best active streak. The switch is applied only while
 * KeepUp isn't on screen (some launchers close or refresh an app whose launcher entry changes).
 */
@Singleton
class AppIconUpdater @Inject constructor(
    private val icons: AppIconManager,
    private val habits: HabitRepository,
    private val entries: EntryRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : DefaultLifecycleObserver {

    @Volatile private var wanted: StreakStage? = null

    fun start(scope: CoroutineScope) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            combine(habits.activeHabits, entries.dailyTotals, settings.iconFollowsStreak) { h, t, follow ->
                if (follow) AppIconManager.bestStage(h, t, LocalDate.now(clock)) else AppIconManager.FIXED
            }.collect { stage ->
                wanted = stage
                val visible = withContext(Dispatchers.Main) {
                    ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                }
                if (!visible) icons.apply(stage)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        wanted?.let(icons::apply)
    }
}
