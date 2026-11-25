package io.github.carsonfiechtner.keepup.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.carsonfiechtner.keepup.data.repo.HabitRepository
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.Goal
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.model.MeasureUnit
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.domain.rules.HistoryLock
import io.github.carsonfiechtner.keepup.domainops.HabitActions
import io.github.carsonfiechtner.keepup.domainops.HabitDraft
import io.github.carsonfiechtner.keepup.domainops.HistoryLockedException
import io.github.carsonfiechtner.keepup.ui.common.UnitInput
import io.github.carsonfiechtner.keepup.ui.format.HabitColors
import io.github.carsonfiechtner.keepup.ui.format.ValueFormat
import io.github.carsonfiechtner.keepup.ui.navigation.EditHabitRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class EditHabitState(
    val loading: Boolean = true,
    val habitId: Long? = null,
    val name: String = "",
    val icon: String = "✅",
    val color: Long = HabitColors.first(),
    val binary: Boolean = true,
    val unitId: Long? = null,
    val period: Period = Period.DAILY,
    val direction: Direction = Direction.AT_LEAST,
    val targetText: String = "",
    val units: List<MeasureUnit> = emptyList(),
    /** Anchor of the habit (today for a new one): weekly/monthly periods start on this day. */
    val anchorDate: LocalDate = LocalDate.MIN,
    /** Period and unit can't change after the setup day. */
    val structureLocked: Boolean = false,
    /** For existing habits past their setup day: when a goal change would take effect. */
    val goalEffectiveFrom: LocalDate? = null,
    val pendingGoal: Goal? = null,
    val error: String? = null,
    val finished: Boolean = false,
) {
    val isNew: Boolean get() = habitId == null
    val numberUnits: List<MeasureUnit> get() = units.filter { !it.isBinary }
    val binaryUnit: MeasureUnit? get() = units.firstOrNull { it.isBinary }
    val selectedUnit: MeasureUnit? get() = if (binary) binaryUnit else units.firstOrNull { it.id == unitId }
    val targetFixed: Boolean get() = binary && period == Period.DAILY
    val target: Double? get() = if (targetFixed) 1.0 else ValueFormat.parse(targetText)

    val canSave: Boolean
        get() = name.isNotBlank() && selectedUnit != null && target != null &&
            (direction == Direction.AT_MOST || (target ?: 0.0) > 0)

    /** The goal as currently entered, for the preview sentence. */
    fun previewGoal(): Goal? = target?.let {
        Goal(if (targetFixed) Direction.AT_LEAST else direction, it, anchorDate)
    }
}

@HiltViewModel
class EditHabitViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val habits: HabitRepository,
    private val actions: HabitActions,
    private val clock: Clock,
) : ViewModel() {

    private val habitId: Long? = savedStateHandle.toRoute<EditHabitRoute>().habitId.takeIf { it > 0 }
    private var original: Habit? = null

    private val _state = MutableStateFlow(EditHabitState())
    val state: StateFlow<EditHabitState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            habits.units.collect { units -> _state.update { it.copy(units = units) } }
        }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val today = LocalDate.now(clock)
        val units = habits.units.first()
        val habit = habitId?.let { habits.getHabit(it) }
        original = habit
        _state.value = if (habit == null) {
            EditHabitState(
                loading = false,
                units = units,
                unitId = defaultUnitId(units),
                anchorDate = today,
            )
        } else {
            val goal = habit.pendingGoal(today) ?: habit.goalFor(today)
            EditHabitState(
                loading = false,
                habitId = habit.id,
                name = habit.name,
                icon = habit.icon,
                color = habit.color,
                binary = habit.unit.isBinary,
                unitId = if (habit.unit.isBinary) defaultUnitId(units) else habit.unit.id,
                period = habit.period,
                direction = goal.direction,
                targetText = ValueFormat.number(goal.target, habit.unit.decimals).replace(",", ""),
                units = units,
                anchorDate = habit.anchorDate,
                structureLocked = !HistoryLock.canChangeStructure(habit, today),
                goalEffectiveFrom = if (HistoryLock.isSetupDay(habit, today)) null else HistoryLock.goalEditEffectiveFrom(habit, today),
                pendingGoal = habit.pendingGoal(today),
            )
        }
    }

    private fun defaultUnitId(units: List<MeasureUnit>): Long? =
        (units.firstOrNull { it.isBuiltin && it.name == "Times" } ?: units.firstOrNull { !it.isBinary })?.id

    fun setName(value: String) = _state.update { it.copy(name = value, error = null) }
    fun setIcon(value: String) = _state.update { it.copy(icon = value.trim().take(8).ifEmpty { it.icon }) }
    fun setColor(value: Long) = _state.update { it.copy(color = value) }
    fun setBinary(value: Boolean) = _state.update { if (it.structureLocked) it else it.copy(binary = value) }
    fun setUnit(id: Long) = _state.update { if (it.structureLocked) it else it.copy(unitId = id) }
    fun setPeriod(value: Period) = _state.update { if (it.structureLocked) it else it.copy(period = value) }
    fun setDirection(value: Direction) = _state.update { it.copy(direction = value) }
    fun setTarget(value: String) = _state.update { it.copy(targetText = value, error = null) }

    fun createUnit(input: UnitInput) {
        viewModelScope.launch {
            val id = habits.createUnit(input.name, input.label, input.decimals, input.quickAdd)
            _state.update { it.copy(unitId = id, binary = false) }
        }
    }

    fun save() {
        val s = _state.value
        val unit = s.selectedUnit ?: return
        val target = s.target ?: return
        viewModelScope.launch {
            try {
                actions.saveHabit(
                    HabitDraft(
                        id = s.habitId,
                        name = s.name,
                        description = original?.description,
                        icon = s.icon,
                        color = s.color,
                        period = s.period,
                        unitId = unit.id,
                        direction = if (s.targetFixed) Direction.AT_LEAST else s.direction,
                        target = target,
                    ),
                )
                _state.update { it.copy(finished = true) }
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(error = e.message) }
            } catch (e: HistoryLockedException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun archive() {
        val id = habitId ?: return
        viewModelScope.launch {
            actions.archive(id)
            _state.update { it.copy(finished = true) }
        }
    }

    fun delete() {
        val id = habitId ?: return
        viewModelScope.launch {
            actions.delete(id)
            _state.update { it.copy(finished = true) }
        }
    }
}
