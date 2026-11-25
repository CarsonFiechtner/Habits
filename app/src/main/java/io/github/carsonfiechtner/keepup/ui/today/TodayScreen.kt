package io.github.carsonfiechtner.keepup.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.carsonfiechtner.keepup.ui.common.HabitText
import io.github.carsonfiechtner.keepup.ui.components.EmptyState
import io.github.carsonfiechtner.keepup.ui.stage.StageArt
import io.github.carsonfiechtner.keepup.ui.stage.isDark
import io.github.carsonfiechtner.keepup.ui.stage.streakDescription
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle

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
    val lastAutoSyncAt by viewModel.lastAutoSyncAt.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pickingDate by remember { mutableStateOf(false) }

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
        if (state.loading) return@Scaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "header") { Header(state, onPickDate = { pickingDate = true }) }
            if (!state.hasHabits) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.CheckCircle,
                        title = "No habits yet",
                        body = "Tap New habit to start tracking something.",
                        modifier = Modifier.padding(top = 48.dp),
                    )
                }
                return@LazyColumn
            }
            if (state.summary.total > 0) item(key = "summary") { SummaryCard(state) }
            item(key = "week") { WeekStrip(state.week, onSelect = viewModel::goToDate) }
            if (!state.isToday) item(key = "past") { PastDayBanner(onToday = viewModel::goToToday) }

            section(
                title = "Daily",
                trailing = state.daily.takeIf { it.isNotEmpty() }?.let { "${state.openDaily.size} open" },
                rows = state.openDaily,
                viewModel = viewModel,
                onEditHabit = onEditHabit,
                onOpenHabit = onOpenHabit,
            )
            if (state.done.isNotEmpty()) {
                item(key = "done") { DoneStrip(state.done, onOpen = { viewModel.openSheet(it) }) }
            }
            section(
                title = "This week & month",
                trailing = null,
                rows = state.openPeriodic,
                viewModel = viewModel,
                onEditHabit = onEditHabit,
                onOpenHabit = onOpenHabit,
            )
            if (state.upcoming.isNotEmpty()) {
                item(key = "header-upcoming") { SectionHeader("Upcoming", null) }
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

    if (pickingDate) {
        PastDatePicker(
            today = state.today,
            selected = state.date,
            onDismiss = { pickingDate = false },
            onPick = {
                pickingDate = false
                viewModel.goToDate(it)
            },
        )
    }

    sheet?.let { sheetState ->
        LogSheet(
            state = sheetState,
            lastAutoSyncAt = lastAutoSyncAt,
            onSyncNow = { viewModel.syncNow() },
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

@Composable
private fun Header(state: TodayUiState, onPickDate: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                HabitText.fullDate(state.date),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                HabitText.relativeDay(state.date, state.today),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        FilledTonalIconButton(onClick = onPickDate) {
            Icon(Icons.Filled.DateRange, contentDescription = "Pick a day")
        }
    }
}

/** Hero card: progress ring for the day, what's left, and the best running streak. */
@Composable
private fun SummaryCard(state: TodayUiState) {
    val summary = state.summary
    val dark = MaterialTheme.colorScheme.isDark()
    val bg = if (dark) Color(0xFF173A1B) else Color(0xFF1F5A24)
    val fg = if (dark) Color(0xFFE8F5E9) else Color.White
    val muted = if (dark) Color(0xFFB9D7BB) else Color(0xFFCFE6D0)
    val chip = if (dark) Color(0xFF22502A) else Color(0xFF2C6E32)
    val ringFill = if (dark) Color(0xFF8FD88A) else Color(0xFFA5D6A7)
    val ringTrack = if (dark) Color(0xFF2A522F) else Color(0xFF2F6B35)
    val left = summary.total - summary.done

    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                Modifier
                    .size(92.dp)
                    .semantics { contentDescription = "${summary.done} of ${summary.total} done" },
                contentAlignment = Alignment.Center,
            ) {
                Ring(
                    progress = if (summary.total == 0) 0f else summary.done.toFloat() / summary.total,
                    fill = ringFill,
                    track = ringTrack,
                    stroke = 10.dp,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${summary.done}/${summary.total}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    Text("done", style = MaterialTheme.typography.labelMedium, color = muted)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when {
                        left == 0 -> if (state.isToday) "All done today" else "All done"
                        state.isToday -> "$left left today"
                        else -> "$left not done"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                if (summary.openNames.isNotEmpty()) {
                    Text(
                        naturalList(summary.openNames),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                summary.best?.let { best ->
                    Row(
                        Modifier
                            .padding(top = 4.dp)
                            .background(chip, RoundedCornerShape(14.dp))
                            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StageArt(best.stage, Modifier.size(22.dp), RoundedCornerShape(7.dp), contentDescription = null)
                        Text(
                            "Best going: ${best.habit.name}, ${streakDescription(best.streak, best.habit.period)}",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun naturalList(names: List<String>): String = when {
    names.size == 1 -> names[0]
    names.size <= 3 -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    else -> names.take(2).joinToString(", ") + " and ${names.size - 2} more"
}

@Composable
private fun Ring(progress: Float, fill: Color, track: Color, stroke: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = stroke.toPx()
        val inset = w / 2
        val arcSize = Size(size.width - w, size.height - w)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
        if (progress > 0f) {
            drawArc(fill, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

/** The last 7 days as completion rings; tap one to view it. */
@Composable
private fun WeekStrip(days: List<WeekDay>, onSelect: (LocalDate) -> Unit) {
    val locale = LocalLocale.current.platformLocale
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        days.forEach { day ->
            val selectedBg = MaterialTheme.colorScheme.primary
            val bg = if (day.selected) selectedBg else Color.Transparent
            val fg = if (day.selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            val ringFill = if (day.selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
            val ringTrack = if (day.selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant
            val percent = day.ratio?.let { "${(it * 100).toInt()}% done" } ?: "no habits"
            Column(
                Modifier
                    .weight(1f)
                    .background(bg, RoundedCornerShape(18.dp))
                    .clickable { onSelect(day.date) }
                    .semantics { contentDescription = "${HabitText.date(day.date)}, $percent" }
                    .padding(top = 8.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = fg,
                )
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Ring(day.ratio ?: 0f, ringFill, ringTrack, 3.5.dp, Modifier.fillMaxSize())
                    Text("${day.date.dayOfMonth}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = fg)
                }
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    trailing: String?,
    rows: List<HabitRowState>,
    viewModel: TodayViewModel,
    onEditHabit: (Long) -> Unit,
    onOpenHabit: (Long) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "header-$title") { SectionHeader(title, trailing) }
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
private fun SectionHeader(title: String, trailing: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Finished habits collapse into chips, so open ones get the space. Tap one to open its log. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoneStrip(rows: List<HabitRowState>, onOpen: (Long) -> Unit) {
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Done",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        rows.forEach { row ->
            Row(
                Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp))
                    .clickable { onOpen(row.habit.id) }
                    .padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(row.habit.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun PastDayBanner(onToday: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                "Past days are read-only",
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onToday) { Text("Back to today") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastDatePicker(today: LocalDate, selected: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text("Show") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = pickerState) }
}

