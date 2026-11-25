package io.github.carsonfiechtner.keepup.data.db

import io.github.carsonfiechtner.keepup.domain.model.Entry
import io.github.carsonfiechtner.keepup.domain.model.Goal
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.model.MeasureUnit

fun UnitEntity.toDomain() = MeasureUnit(
    id = id,
    name = name,
    label = label,
    kind = kind,
    decimals = decimals,
    quickAddStep = quickAdd,
    isBuiltin = isBuiltin,
)

fun MeasureUnit.toEntity() = UnitEntity(
    id = id,
    name = name,
    label = label,
    kind = kind,
    decimals = decimals,
    quickAdd = quickAddStep,
    isBuiltin = isBuiltin,
)

fun GoalEntity.toDomain() = Goal(direction = direction, target = target, effectiveFrom = effectiveFrom)

fun Goal.toEntity(habitId: Long) =
    GoalEntity(habitId = habitId, effectiveFrom = effectiveFrom, direction = direction, target = target)

fun HabitWithDetails.toDomain() = Habit(
    id = habit.id,
    name = habit.name,
    description = habit.description,
    icon = habit.icon,
    color = habit.color,
    period = habit.period,
    anchorDate = habit.anchorDate,
    unit = unit.toDomain(),
    goals = goals.map { it.toDomain() },
    archivedAt = habit.archivedAt,
    sortOrder = habit.sortOrder,
    createdAt = habit.createdAt,
)

fun Habit.toEntity() = HabitEntity(
    id = id,
    name = name,
    description = description,
    icon = icon,
    color = color,
    period = period,
    anchorDate = anchorDate,
    unitId = unit.id,
    sortOrder = sortOrder,
    createdAt = createdAt,
    archivedAt = archivedAt,
)

fun EntryEntity.toDomain() = Entry(
    id = id,
    habitId = habitId,
    date = date,
    loggedAt = loggedAt,
    value = value,
    note = note,
    origin = origin,
)
