package io.github.carsonfiechtner.keepup.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.carsonfiechtner.keepup.ui.common.HabitText
import io.github.carsonfiechtner.keepup.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onNewHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
    onOpenHabit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = message.undo?.let { "Undo" },
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) message.undo?.let(viewModel::undo)
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(HabitText.relativeDay(state.date, state.today))
                        Text(
                            HabitText.fullDate(state.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::previousDay) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                    }
                    IconButton(onClick = viewModel::nextDay, enabled = !state.isToday) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            if (state.isToday && !state.loading) {
                ExtendedFloatingActionButton(
                    onClick = onNewHabit,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New habit") },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> Unit
            !state.hasHabits -> EmptyState(
                icon = Icons.Filled.CheckCircle,
                title = "No habits yet",
                body = "Tap New habit to start tracking something.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp),
            ) {
                if (!state.isToday) {
                    item(key = "past") { PastDayBanner(onToday = viewModel::goToToday) }
                }
                section("Daily", state.daily, viewModel, onEditHabit, onOpenHabit)
                section("Weekly & monthly", state.periodic, viewModel, onEditHabit, onOpenHabit)
                if (state.upcoming.isNotEmpty()) {
                    item(key = "header-upcoming") { SectionHeader("Upcoming") }
                    items(state.upcoming, key = { "upcoming-${it.id}" }) { habit ->
                        ListItem(
                            modifier = Modifier.clickable { onEditHabit(habit.id) },
                            leadingContent = { Text(habit.icon, style = MaterialTheme.typography.titleLarge) },
                            supportingContent = { Text("Starts ${HabitText.date(habit.anchorDate)}") },
                        ) { Text(habit.name) }
                    }
                }
                if (state.daily.isEmpty() && state.periodic.isEmpty() && state.upcoming.isEmpty()) {
                    item(key = "none") {
                        Text(
                            "No habits had started yet on this day.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    sheet?.let { sheetState ->
        LogSheet(
            state = sheetState,
            onDismiss = viewModel::closeSheet,
            onLog = { value, note -> viewModel.logValue(sheetState.row, value, note) },
            onToggleDone = { viewModel.toggleDone(sheetState.row.habit.id) },
            onEditEntry = viewModel::editEntry,
            onDeleteEntry = viewModel::deleteEntry,
            onEditHabit = {
                viewModel.closeSheet()
                onEditHabit(sheetState.row.habit.id)
            },
            onDetails = {
                viewModel.closeSheet()
                onOpenHabit(sheetState.row.habit.id)
            },
        )
    }
}

private fun LazyListScope.section(
    title: String,
    rows: List<HabitRowState>,
    viewModel: TodayViewModel,
    onEditHabit: (Long) -> Unit,
    onOpenHabit: (Long) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "header-$title") { SectionHeader(title) }
    items(rows, key = { it.habit.id }) { row ->
        HabitRow(
            row = row,
            onClick = { viewModel.openSheet(row.habit.id) },
            onAction = { viewModel.onRowAction(row) },
            onEdit = { onEditHabit(row.habit.id) },
            onDetails = { onOpenHabit(row.habit.id) },
            onArchive = { viewModel.archive(row) },
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun PastDayBanner(onToday: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                "Past days are read-only",
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onToday) { Text("Today") }
        }
    }
}
