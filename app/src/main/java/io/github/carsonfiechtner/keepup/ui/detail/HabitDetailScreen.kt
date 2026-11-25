package io.github.carsonfiechtner.keepup.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.carsonfiechtner.keepup.domain.evaluation.CompletionRate
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodResult
import io.github.carsonfiechtner.keepup.domain.evaluation.StreakStage
import io.github.carsonfiechtner.keepup.domain.model.Entry
import io.github.carsonfiechtner.keepup.domain.model.Habit
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.ui.charts.Bar
import io.github.carsonfiechtner.keepup.ui.charts.BarChart
import io.github.carsonfiechtner.keepup.ui.charts.statusColor
import io.github.carsonfiechtner.keepup.ui.charts.statusLabel
import io.github.carsonfiechtner.keepup.ui.common.HabitText
import io.github.carsonfiechtner.keepup.ui.format.ValueFormat
import io.github.carsonfiechtner.keepup.ui.stage.StageArt
import io.github.carsonfiechtner.keepup.ui.stage.isDark
import io.github.carsonfiechtner.keepup.ui.stage.stageColor
import io.github.carsonfiechtner.keepup.ui.stage.streakDescription
import java.time.LocalDate
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
        val streak = state.stats?.streaks?.current ?: 0
        val stage = StreakStage.of(streak, habit.period)
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Summary(state, habit)
            StreakHero(habit, streak, stage)
            StatsRow(state, habit)
            if (habit.period == Period.DAILY && state.heatmap.isNotEmpty()) Heatmap(state.heatmap)
            ValuesChart(state, habit, stageColor(stage))
            if (habit.period != Period.DAILY) PeriodList(state, habit, stageColor(stage))
            GrowthRow(habit.period, stage)
            EntryList(state.entries, habit, state.today)
            GoalHistory(habit)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
}

@Composable
private fun Summary(state: HabitDetailState, habit: Habit) {
    val goal = habit.goalFor(state.asOf)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val started = if (habit.anchorDate.isAfter(state.today)) "starts" else "started"
        val archived = habit.archivedAt?.let {
            " · archived ${HabitText.date(it.atZone(ZoneId.systemDefault()).toLocalDate())}"
        }.orEmpty()
        Text(
            "${HabitText.goalSentence(goal, habit.unit, habit.period)} · $started ${HabitText.date(habit.anchorDate)}$archived",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        habit.pendingGoal(state.today)?.let {
            Text(
                "From ${HabitText.date(it.effectiveFrom)}: ${HabitText.goalSentence(it, habit.unit, habit.period)}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The current stage's art, the streak, and progress toward the next stage. */
@Composable
private fun StreakHero(habit: Habit, streak: Int, stage: StreakStage) {
    val color = stageColor(stage)
    val next = StreakStage.next(streak, habit.period)
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StageArt(stage, Modifier.size(88.dp), CircleShape)
                Column {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "%,d".format(streak),
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                            color = color,
                        )
                        Text(
                            unitNoun(habit.period) + " streak",
                            style = MaterialTheme.typography.titleMedium,
                            color = color,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Text(
                        when {
                            next == null -> "${stage.displayName}: the highest level"
                            streak == 0 -> "Complete a ${unitNoun(habit.period)} to plant your seed"
                            else -> "Your ${stage.displayName.lowercase()} becomes ${article(next.first)} ${next.first.displayName.let { if (it == "Silverbough" || it == "World Tree") it else it.lowercase() }} in ${streakDescription(next.second, habit.period)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (next != null) {
                val start = StreakStage.startOf(stage, habit.period)
                val end = start + (streak - start) + next.second
                val fraction = ((streak - start).toFloat() / (end - start)).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(8.dp)
                                .background(color, RoundedCornerShape(4.dp)),
                        )
                    }
                    Row {
                        Text(stage.displayName, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Text("${next.first.displayName} · ${streakDescription(end, habit.period)}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun unitNoun(period: Period) = when (period) {
    Period.DAILY -> "day"
    Period.WEEKLY -> "week"
    Period.MONTHLY -> "month"
}

private fun article(stage: StreakStage) = if (stage == StreakStage.OAK) "an" else "a"

@Composable
private fun StatsRow(state: HabitDetailState, habit: Habit) {
    val stats = state.stats ?: return
    val unit = when (habit.period) {
        Period.DAILY -> "days"
        Period.WEEKLY -> "weeks"
        Period.MONTHLY -> "months"
    }
    val recentLabel = "Last ${stats.recentWindow} $unit"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard("Best", "%,d".format(stats.streaks.best), unit, Modifier.weight(1f))
        StatCard(recentLabel, percent(stats.recent), "${stats.recent.completed} of ${stats.recent.decided}", Modifier.weight(1f))
        StatCard("All time", percent(stats.allTime), "since " + habit.anchorDate.format(DateTimeFormatter.ofPattern("MMM d")), Modifier.weight(1f))
    }
}

private fun percent(rate: CompletionRate): String = rate.ratio?.let { "${(it * 100).toInt()}%" } ?: "–"

@Composable
private fun StatCard(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun heatColors(): Pair<List<Color>, Color> {
    val dark = MaterialTheme.colorScheme.isDark()
    val levels = if (dark) {
        listOf(Color(0xFF263026), Color(0xFF1F4A32), Color(0xFF2E7D57), Color(0xFF43A877), Color(0xFF7FE0AA))
    } else {
        listOf(Color(0xFFDCE5DA), Color(0xFFBFE3CC), Color(0xFF7DCBA0), Color(0xFF2E9E6B), Color(0xFF0B5D3B))
    }
    val before = if (dark) Color(0xFF1B211B) else Color(0xFFE8EEE7)
    return levels to before
}

/** Contribution-graph style heatmap for daily habits: 6 months by default, or the past year. */
@Composable
private fun Heatmap(allWeeks: List<List<HeatCell>>) {
    var year by rememberSaveable { mutableStateOf(false) }
    val weeks = if (year) allWeeks else allWeeks.takeLast(26)
    val (levels, before) = heatColors()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val met = weeks.flatten().count { it is HeatCell.Level && it.level >= 3 }
    val counted = weeks.flatten().count { it is HeatCell.Level }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(if (year) "Last 12 months" else "Last 6 months")
                Spacer(Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow {
                    listOf(false to "6M", true to "1Y").forEachIndexed { i, (isYear, label) ->
                        SegmentedButton(
                            selected = year == isYear,
                            onClick = { year = isYear },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            icon = {},
                        ) { Text(label) }
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = if (year) 1.5.dp else 3.dp
                val cell = (maxWidth - gap * (weeks.size - 1)) / weeks.size
                val monthFormat = remember { DateTimeFormatter.ofPattern("MMM") }
                Column {
                    Box(Modifier.fillMaxWidth().height(16.dp)) {
                        var lastMonth = -1
                        weeks.forEachIndexed { i, week ->
                            val month = week.first().date.monthValue
                            if (month != lastMonth && i < weeks.size - 2) {
                                Text(
                                    week.first().date.format(monthFormat),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = labelColor,
                                    maxLines = 1,
                                    modifier = Modifier.offset(x = (cell + gap) * i),
                                )
                            }
                            lastMonth = month
                        }
                    }
                    val density = LocalDensity.current
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(cell * 7 + gap * 6)
                            .semantics { contentDescription = "Heatmap: goal met on $met of $counted days shown" },
                    ) {
                        val c = with(density) { cell.toPx() }
                        val g = with(density) { gap.toPx() }
                        val r = CornerRadius(c * 0.22f)
                        weeks.forEachIndexed { w, week ->
                            week.forEachIndexed { d, heat ->
                                val color = when (heat) {
                                    is HeatCell.Future -> null
                                    is HeatCell.Before -> before
                                    is HeatCell.Level -> levels[heat.level]
                                }
                                if (color != null) {
                                    drawRoundRect(color, Offset(w * (c + g), d * (c + g)), Size(c, c), r)
                                }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.weight(1f))
                Text("Less", style = MaterialTheme.typography.labelSmall, color = labelColor)
                levels.forEach { Box(Modifier.size(11.dp).background(it, RoundedCornerShape(3.dp))) }
                Text("More", style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
        }
    }
}

@Composable
private fun ValuesChart(state: HabitDetailState, habit: Habit, color: Color) {
    val count = when (habit.period) {
        Period.DAILY -> 14
        Period.WEEKLY, Period.MONTHLY -> 12
    }
    val recent = state.periods.takeLast(count)
    if (recent.size < 2) return
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
private fun PeriodList(state: HabitDetailState, habit: Habit, color: Color) {
    var showAll by remember { mutableStateOf(false) }
    val periods = state.periods.asReversed()
    if (periods.isEmpty()) return
    SectionTitle(if (habit.period == Period.WEEKLY) "Weeks" else "Months")
    val shown = if (showAll) periods else periods.take(8)
    shown.forEach { PeriodRow(it, habit, color) }
    if (periods.size > shown.size) {
        TextButton(onClick = { showAll = true }) { Text("Show all ${periods.size}") }
    }
}

@Composable
private fun PeriodRow(result: PeriodResult, habit: Habit, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(14.dp).background(statusColor(result.status, color), CircleShape))
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

/** All six levels with the current one highlighted. */
@Composable
private fun GrowthRow(period: Period, current: StreakStage) {
    SectionTitle("How streaks grow")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        StreakStage.entries.forEach { stage ->
            val isCurrent = stage == current
            val start = StreakStage.startOf(stage, period)
            Column(
                Modifier
                    .weight(1f)
                    .background(
                        if (isCurrent) stageColor(stage).copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
                        RoundedCornerShape(14.dp),
                    )
                    .border(1.5.dp, if (isCurrent) stageColor(stage) else Color.Transparent, RoundedCornerShape(14.dp))
                    .padding(horizontal = 2.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StageArt(stage, Modifier.fillMaxWidth(0.86f).aspectRatio(1f), RoundedCornerShape(10.dp))
                Text(
                    if (stage == StreakStage.SILVERBOUGH) "Silver" else if (stage == StreakStage.WORLD_TREE) "World" else stage.displayName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    if (start == 0) "start" else shortSpan(start, period),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun shortSpan(n: Int, period: Period): String = when (period) {
    Period.DAILY -> when (n) {
        7 -> "1w"
        30 -> "1m"
        90 -> "3m"
        365 -> "1y"
        1095 -> "3y"
        else -> "${n}d"
    }
    Period.WEEKLY -> "${n}w"
    Period.MONTHLY -> "${n}mo"
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
private fun EntryList(entries: List<Entry>, habit: Habit, today: LocalDate) {
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
