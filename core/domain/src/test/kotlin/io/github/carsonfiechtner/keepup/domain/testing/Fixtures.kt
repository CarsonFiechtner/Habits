package io.github.carsonfiechtner.keepup.domain.testing

import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.Goal
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.model.MeasureUnit
import io.github.carsonfiechtner.keepup.domain.model.UnitKind
import io.github.carsonfiechtner.keepup.domain.period.Period
import java.time.Instant
import java.time.LocalDate

fun d(s: String): LocalDate = LocalDate.parse(s)

val DONE = MeasureUnit(1, "Done", "", UnitKind.BINARY, 0, 1.0, isBuiltin = true)
val LITERS = MeasureUnit(2, "Liters", "L", UnitKind.NUMBER, 2, 0.25, isBuiltin = true)
val STEPS = MeasureUnit(3, "Steps", "steps", UnitKind.NUMBER, 0, 1000.0, isBuiltin = true)

fun habit(
    period: Period = Period.DAILY,
    anchor: LocalDate = d("2026-11-25"),
    unit: MeasureUnit = LITERS,
    direction: Direction = Direction.AT_LEAST,
    target: Double = 2.0,
    goals: List<Goal> = listOf(Goal(direction, target, anchor)),
) = Habit(
    id = 1,
    name = "Test",
    description = null,
    icon = "💧",
    color = 0xFF2E7D32,
    period = period,
    anchorDate = anchor,
    unit = unit,
    goals = goals,
    archivedAt = null,
    sortOrder = 0,
    createdAt = Instant.parse("2026-11-25T08:00:00Z"),
)

/** Builds daily totals from "yyyy-mm-dd" to value pairs. */
fun totals(vararg pairs: Pair<String, Double>): Map<LocalDate, Double> = pairs.associate { d(it.first) to it.second }
