package io.github.carsonfiechtner.keepup.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.carsonfiechtner.keepup.domain.evaluation.PeriodStatus
import io.github.carsonfiechtner.keepup.ui.charts.Bar
import io.github.carsonfiechtner.keepup.ui.charts.BarChart
import io.github.carsonfiechtner.keepup.ui.charts.statusColor
import io.github.carsonfiechtner.keepup.ui.charts.statusLabel
import io.github.carsonfiechtner.keepup.ui.common.HabitText
import io.github.carsonfiechtner.keepup.ui.components.EmptyState
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenHabit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("History") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        if (state.daily.isEmpty() && state.periodic.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.DateRange,
                title = "Nothing to show yet",
                body = "Streaks, calendars and charts appear here once your habits have started.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.trend.any { it.ratio != null }) {
                Text("Daily habits completed, last ${state.trend.size} days", style = MaterialTheme.typography.titleMedium)
                val primary = MaterialTheme.colorScheme.primary
                BarChart(
                    bars = state.trend.map { Bar((it.ratio ?: 0f) * 100f, primary) },
                    reference = 100f,
                    startLabel = HabitText.date(state.trend.first().date),
                    endLabel = HabitText.date(state.trend.last().date),
                    description = "Share of daily habits completed each day over the last ${state.trend.size} days",
                    height = 100.dp,
                )
            }
            if (state.daily.isNotEmpty()) {
                Text("Last ${state.days.size} days", style = MaterialTheme.typography.titleMedium)
                DayHeader(state)
                state.daily.forEach { OverviewRowView(it, onOpenHabit) }
            }
            if (state.periodic.isNotEmpty()) {
                Text("Weekly & monthly (recent periods)", style = MaterialTheme.typography.titleMedium)
                state.periodic.forEach { OverviewRowView(it, onOpenHabit) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private val NAME_WIDTH = 132.dp

@Composable
private fun DayHeader(state: HistoryState) {
    val locale = LocalLocale.current.platformLocale
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(NAME_WIDTH))
        state.days.forEach { day ->
            Text(
                day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OverviewRowView(row: OverviewRow, onOpenHabit: (Long) -> Unit) {
    val color = Color(row.habit.color)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenHabit(row.habit.id) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(NAME_WIDTH)) {
            Text(
                "${row.habit.icon} ${row.habit.name}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (row.streak > 0) "🔥${row.streak}" else " ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.cells.forEach { status -> StatusDot(status, color, Modifier.weight(1f)) }
    }
}

@Composable
private fun StatusDot(status: PeriodStatus?, color: Color, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val fill = status?.let { statusColor(it, color) } ?: Color.Transparent
        Box(
            Modifier
                .size(20.dp)
                .background(fill, CircleShape)
                .semantics { contentDescription = status?.let(::statusLabel) ?: "not started" },
        )
    }
}
