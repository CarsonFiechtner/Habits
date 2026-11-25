package io.github.carsonfiechtner.keepup.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.carsonfiechtner.keepup.data.repo.EntryRepository
import io.github.carsonfiechtner.keepup.data.repo.HabitRepository
import io.github.carsonfiechtner.keepup.domain.evaluation.HabitHistory
import io.github.carsonfiechtner.keepup.domain.evaluation.HabitStats
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodResult
import io.github.carsonfiechtner.keepup.domain.model.Entry
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.ui.navigation.HabitDetailRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HabitDetailState(
    val loading: Boolean = true,
    /** Null once loaded means the habit was deleted. */
    val habit: Habit? = null,
    val today: LocalDate = LocalDate.MIN,
    /** Evaluation cutoff: today, or the archive date for archived habits. */
    val asOf: LocalDate = LocalDate.MIN,
    val stats: HabitStats? = null,
    /** All periods so far, oldest first. */
    val periods: List<PeriodResult> = emptyList(),
    val entries: List<Entry> = emptyList(),
    /** Daily habits only: up to a year of weeks for the heatmap, oldest first. */
    val heatmap: List<List<HeatCell>> = emptyList(),
)

@HiltViewModel
class HabitDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    habits: HabitRepository,
    entries: EntryRepository,
    clock: Clock,
) : ViewModel() {

    private val habitId = savedStateHandle.toRoute<HabitDetailRoute>().habitId

    val state: StateFlow<HabitDetailState> = combine(
        habits.observeHabit(habitId),
        entries.dailyTotals.map { it[habitId].orEmpty() },
        entries.observeForHabit(habitId),
    ) { habit, totals, entryList ->
        val today = LocalDate.now(clock)
        if (habit == null) return@combine HabitDetailState(loading = false, today = today)
        val archivedOn = habit.archivedAt?.atZone(ZoneId.systemDefault())?.toLocalDate()
        val asOf = listOfNotNull(today, archivedOn).min()
        HabitDetailState(
            loading = false,
            habit = habit,
            today = today,
            asOf = asOf,
            stats = HabitHistory.stats(habit, totals, asOf),
            periods = HabitHistory.periods(habit, totals, asOf),
            entries = entryList,
            heatmap = if (habit.period == Period.DAILY) HeatmapBuilder.build(habit, totals, asOf, HEATMAP_WEEKS) else emptyList(),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitDetailState())
}

private const val HEATMAP_WEEKS = 53
