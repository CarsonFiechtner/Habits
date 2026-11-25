package io.github.carsonfiechtner.keepup.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.carsonfiechtner.keepup.domain.evaluation.CompletionRate
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodResult
import io.github.carsonfiechtner.keepup.domain.model.Entry
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.ui.charts.Bar
import io.github.carsonfiechtner.keepup.ui.charts.BarChart
import io.github.carsonfiechtner.keepup.ui.charts.MonthCalendar
import io.github.carsonfiechtner.keepup.ui.charts.statusColor
import io.github.carsonfiechtner.keepup.ui.charts.statusLabel
import io.github.carsonfiechtner.keepup.ui.common.HabitText
import io.github.carsonfiechtner.keepup.ui.format.ValueFormat
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: HabitDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val habit = state.habit

    LaunchedEffect(state.loading, habit) {
        if (!state.loading && habit == null) onBack() // deleted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        habit?.let { "${it.icon}  ${it.name}" }.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (habit != null && !habit.isArchived) {
                        IconButton(onClick = { onEdit(habit.id) }) { Icon(Icons.Filled.Edit, contentDescription = "Edit habit") }
                    }
                },
            )
        },
    ) { padding ->
        if (habit == null) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Summary(state, habit)
            StatsRow(state, habit)
            ValuesChart(state, habit)
            if (habit.period == Period.DAILY) DailyCalendar(state, habit) else PeriodList(state, habit)
            EntryList(state.entries, habit, state.today)
            GoalHistory(habit)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Summary(state: HabitDetailState, habit: Habit) {
    val goal = habit.goalFor(state.asOf)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(HabitText.goalSentence(goal, habit.unit, habit.period), style = MaterialTheme.typography.titleLarge)
        habit.pendingGoal(state.today)?.let {
            Text(
                "From ${HabitText.date(it.effectiveFrom)}: ${HabitText.goalSentence(it, habit.unit, habit.period)}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val started = if (habit.anchorDate.isAfter(state.today)) "Starts" else "Started"
        val archived = habit.archivedAt?.let {
            " · Archived ${HabitText.date(it.atZone(ZoneId.systemDefault()).toLocalDate())}"
        }.orEmpty()
        Text(
            "$started ${HabitText.date(habit.anchorDate)}$archived",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsRow(state: HabitDetailState, habit: Habit) {
    val stats = state.stats ?: return
    val unit = when (habit.period) {
        Period.DAILY -> "days"
        Period.WEEKLY -> "weeks"
        Period.MONTHLY -> "months"
    }
    val recentLabel = when (habit.period) {
        Period.DAILY -> "Last ${stats.recentWindow} days"
        Period.WEEKLY -> "Last ${stats.recentWindow} weeks"
        Period.MONTHLY -> "Last ${stats.recentWindow} months"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard("Streak", "${stats.streaks.current}", unit, Modifier.weight(1f))
        StatCard("Best", "${stats.streaks.best}", unit, Modifier.weight(1f))
        StatCard("Completion", percent(stats.recent), recentLabel, Modifier.weight(1f))
        StatCard("All time", percent(stats.allTime), "${stats.allTime.completed} of ${stats.allTime.decided}", Modifier.weight(1f))
    }
}

private fun percent(rate: CompletionRate): String = rate.ratio?.let { "${(it * 100).toInt()}%" } ?: "–"

@Composable
private fun StatCard(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun ValuesChart(state: HabitDetailState, habit: Habit) {
    val count = when (habit.period) {
        Period.DAILY -> 14
        Period.WEEKLY, Period.MONTHLY -> 12
    }
    val recent = state.periods.takeLast(count)
    if (recent.size < 2) return // a chart needs some history
    val color = Color(habit.color)
    val noun = when (habit.period) {
        Period.DAILY -> "days"
        Period.WEEKLY -> "weeks"
        Period.MONTHLY -> "months"
    }
    val unitLabel = if (habit.unit.isBinary) "days done" else habit.unit.label.ifBlank { habit.unit.name }
    SectionTitle("Last ${recent.size} $noun ($unitLabel)")
    BarChart(
        bars = recent.map { Bar(it.value.toFloat(), statusColor(it.status, color)) },
        reference = if (habit.unit.isBinary && habit.period == Period.DAILY) null else habit.goalFor(state.asOf).target.toFloat(),
        startLabel = HabitText.date(recent.first().range.start),
        endLabel = HabitText.date(recent.last().range.start),
        description = "Chart of the last ${recent.size} $noun. Dashed line marks the goal.",
    )
}

@Composable
private fun DailyCalendar(state: HabitDetailState, habit: Habit) {
    val byDate = remember(state.periods) { state.periods.associateBy { it.range.start } }
    val first = YearMonth.from(habit.anchorDate)
    val last = YearMonth.from(state.asOf).let { if (it.isBefore(first)) first else it }
    var monthIndex by rememberSaveable { mutableIntStateOf(Int.MAX_VALUE) }
    val totalMonths = (last.year - first.year) * 12 + (last.monthValue - first.monthValue)
    val index = monthIndex.coerceIn(0, totalMonths)
    val month = first.plusMonths(index.toLong())

    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { monthIndex = index - 1 }, enabled = index > 0) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        IconButton(onClick = { monthIndex = index + 1 }, enabled = index < totalMonths) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
    MonthCalendar(
        month = month,
        today = state.today,
        habitColor = Color(habit.color),
        statusOf = { date -> byDate[date]?.status },
    )
}

@Composable
private fun PeriodList(state: HabitDetailState, habit: Habit) {
    var showAll by remember { mutableStateOf(false) }
    val periods = state.periods.asReversed()
    if (periods.isEmpty()) return
    SectionTitle(if (habit.period == Period.WEEKLY) "Weeks" else "Months")
    val shown = if (showAll) periods else periods.take(8)
    shown.forEach { PeriodRow(it, habit) }
    if (periods.size > shown.size) {
        TextButton(onClick = { showAll = true }) { Text("Show all ${periods.size}") }
    }
}

@Composable
private fun PeriodRow(result: PeriodResult, habit: Habit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = statusColor(result.status, Color(habit.color)),
            modifier = Modifier.size(14.dp),
        ) {}
        Text(
            "${HabitText.date(result.range.start)} – ${HabitText.date(result.range.endInclusive)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            HabitText.progress(result.value, result.goal, habit.unit) + " · " + statusLabel(result.status),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
private fun EntryList(entries: List<Entry>, habit: Habit, today: java.time.LocalDate) {
    var limit by remember { mutableIntStateOf(20) }
    SectionTitle("Entries")
    if (entries.isEmpty()) {
        Text("Nothing logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    entries.take(limit).forEach { entry ->
        Row(Modifier.padding(vertical = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${HabitText.relativeDay(entry.date, today)} · " +
                        entry.loggedAt.atZone(ZoneId.systemDefault()).format(timeFormat),
                    style = MaterialTheme.typography.bodyMedium,
                )
                entry.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                if (habit.unit.isBinary) "Done" else ValueFormat.withUnit(entry.value, habit.unit),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (entries.size > limit) {
        TextButton(onClick = { limit += 50 }) { Text("Show more") }
    }
}

@Composable
private fun GoalHistory(habit: Habit) {
    if (habit.goals.size < 2) return
    HorizontalDivider()
    SectionTitle("Goal history")
    habit.goals.sortedByDescending { it.effectiveFrom }.forEach { goal ->
        Text(
            "From ${HabitText.date(goal.effectiveFrom)}: ${HabitText.goalSentence(goal, habit.unit, habit.period)}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

