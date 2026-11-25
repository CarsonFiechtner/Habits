package io.github.carsonfiechtner.keepup.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.carsonfiechtner.keepup.data.repo.EntryRepository
import io.github.carsonfiechtner.keepup.data.repo.HabitRepository
import io.github.carsonfiechtner.keepup.domain.model.Entry
import io.github.carsonfiechtner.keepup.domain.rules.HistoryLock
import io.github.carsonfiechtner.keepup.domainops.HabitActions
import io.github.carsonfiechtner.keepup.domainops.HistoryLockedException
import io.github.carsonfiechtner.keepup.ui.format.ValueFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject

/** Something to undo from a snackbar. */
sealed interface UndoAction {
    data class RemoveEntry(val entryId: Long) : UndoAction
    data class RestoreEntry(val entry: Entry) : UndoAction
    data class Unarchive(val habitId: Long) : UndoAction
}

data class TodayMessage(val text: String, val undo: UndoAction? = null)

/** The log sheet for one habit on the viewed date. */
data class LogSheetState(
    val row: HabitRowState,
    val date: LocalDate,
    val entries: List<Entry>,
    val canLog: Boolean,
    val today: LocalDate,
) {
    fun canModify(entry: Entry): Boolean = HistoryLock.canModify(entry, today)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    habitRepository: HabitRepository,
    private val entryRepository: EntryRepository,
    private val actions: HabitActions,
    private val clock: Clock,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now(clock))

    /** 0 = today, -1 = yesterday, … Past days are view-only. */
    private val dayOffset = MutableStateFlow(0L)

    private val sheetHabitId = MutableStateFlow<Long?>(null)

    private val messageChannel = Channel<TodayMessage>(Channel.BUFFERED)
    val messages: Flow<TodayMessage> = messageChannel.receiveAsFlow()

    val state: StateFlow<TodayUiState> = combine(
        habitRepository.activeHabits,
        entryRepository.dailyTotals,
        today,
        dayOffset,
    ) { habits, totals, today, offset ->
        TodayStateBuilder.build(habits, totals, today.plusDays(offset), today)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.loading(today.value))

    val sheet: StateFlow<LogSheetState?> = combine(sheetHabitId, state) { id, state -> id to state }
        .flatMapLatest { (id, state) ->
            val row = id?.let { habitId -> (state.daily + state.periodic).firstOrNull { it.habit.id == habitId } }
            if (row == null) {
                flowOf(null)
            } else {
                entryRepository.observeOn(row.habit.id, state.date).map { entries ->
                    LogSheetState(row, state.date, entries, canLog = state.isToday, today = state.today)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Roll over to the new day at midnight while the screen is open.
        viewModelScope.launch {
            while (true) {
                val now = ZonedDateTime.now(clock)
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                delay(Duration.between(now, nextMidnight).toMillis() + 500)
                refreshToday()
            }
        }
    }

    /** Call on resume: the date may have changed while the app was in the background. */
    fun refreshToday() {
        today.value = LocalDate.now(clock)
    }

    fun previousDay() {
        dayOffset.value -= 1
    }

    fun nextDay() {
        if (dayOffset.value < 0) dayOffset.value += 1
    }

    fun goToToday() {
        dayOffset.value = 0
    }

    fun openSheet(habitId: Long) {
        sheetHabitId.value = habitId
    }

    fun closeSheet() {
        sheetHabitId.value = null
    }

    fun onRowAction(row: HabitRowState) {
        when (row.action) {
            RowAction.TOGGLE -> run { actions.toggleDone(row.habit.id) }
            RowAction.QUICK_ADD -> logValue(row, row.habit.unit.quickAddStep)
            RowAction.LOG_ZERO -> run {
                val id = actions.logZero(row.habit.id)
                messageChannel.send(TodayMessage("Logged 0 for ${row.habit.name}", UndoAction.RemoveEntry(id)))
            }
            RowAction.NONE -> Unit
        }
    }

    fun logValue(row: HabitRowState, value: Double, note: String? = null) = run {
        val id = actions.logValue(row.habit.id, value, note)
        messageChannel.send(
            TodayMessage("Added ${ValueFormat.withUnit(value, row.habit.unit)} to ${row.habit.name}", UndoAction.RemoveEntry(id)),
        )
    }

    fun toggleDone(habitId: Long) = run { actions.toggleDone(habitId) }

    fun editEntry(entryId: Long, value: Double, note: String?) = run { actions.editEntry(entryId, value, note) }

    fun deleteEntry(entryId: Long) = run {
        val deleted = actions.deleteEntry(entryId) ?: return@run
        messageChannel.send(TodayMessage("Entry deleted", UndoAction.RestoreEntry(deleted)))
    }

    fun archive(row: HabitRowState) = run {
        actions.archive(row.habit.id)
        messageChannel.send(TodayMessage("Archived ${row.habit.name}", UndoAction.Unarchive(row.habit.id)))
    }

    fun undo(action: UndoAction) = run {
        when (action) {
            is UndoAction.Unarchive -> actions.unarchive(action.habitId)
            is UndoAction.RemoveEntry -> actions.undoLog(action.entryId)
            is UndoAction.RestoreEntry ->
                if (!actions.restoreEntry(action.entry)) messageChannel.send(TodayMessage("That day is locked now"))
        }
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: HistoryLockedException) {
                messageChannel.send(TodayMessage(e.message ?: "That day is locked"))
            } catch (e: IllegalArgumentException) {
                messageChannel.send(TodayMessage(e.message ?: "Invalid value"))
            }
        }
    }
}
