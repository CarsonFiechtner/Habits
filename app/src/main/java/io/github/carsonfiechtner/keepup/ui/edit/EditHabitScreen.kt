package io.github.carsonfiechtner.keepup.ui.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.health.connect.client.PermissionController
import io.github.carsonfiechtner.keepup.autotrack.SourceStatus
import io.github.carsonfiechtner.keepup.domain.model.Direction
import io.github.carsonfiechtner.keepup.domain.model.SourceType
import kotlinx.coroutines.launch
import io.github.carsonfiechtner.keepup.domain.period.Period
import io.github.carsonfiechtner.keepup.ui.common.DeleteHabitDialog
import io.github.carsonfiechtner.keepup.ui.common.HabitText
import io.github.carsonfiechtner.keepup.ui.common.UnitDialog
import io.github.carsonfiechtner.keepup.ui.format.HabitColors
import io.github.carsonfiechtner.keepup.ui.format.SuggestedIcons
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHabitScreen(
    onDone: () -> Unit,
    onManageAppGroups: () -> Unit,
    viewModel: EditHabitViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newUnit by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) { if (state.finished) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New habit" else "Edit habit") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.canSave) { Text("Save") }
                    if (!state.isNew) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Archive") },
                                    onClick = { menuOpen = false; viewModel.archive() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete…") },
                                    onClick = { menuOpen = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NameAndIcon(state, viewModel)
            ColorPicker(state.color, viewModel::setColor)
            TrackingSection(state, viewModel, onNewUnit = { newUnit = true }, onManageAppGroups = onManageAppGroups)
            StartDateSection(state, viewModel)
            PeriodSection(state, viewModel)
            GoalSection(state, viewModel)
            state.previewGoal()?.let { goal ->
                state.selectedUnit?.let { unit ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Text(
                            "${state.icon}  ${HabitText.goalSentence(goal, unit, state.period)}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        DeleteHabitDialog(
            habitName = state.name,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
        )
    }
    if (newUnit) {
        UnitDialog(
            existing = null,
            onDismiss = { newUnit = false },
            onConfirm = {
                newUnit = false
                viewModel.createUnit(it)
            },
        )
    }
}

@Composable
private fun NameAndIcon(state: EditHabitState, viewModel: EditHabitViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.icon,
            onValueChange = viewModel::setIcon,
            label = { Text("Icon") },
            singleLine = true,
            modifier = Modifier.width(80.dp),
        )
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(SuggestedIcons) { icon ->
            Box(
                Modifier
                    .size(40.dp)
                    .background(
                        if (icon == state.icon) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        CircleShape,
                    )
                    .clickable { viewModel.setIcon(icon) },
                contentAlignment = Alignment.Center,
            ) { Text(icon, style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(selected: Long, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HabitColors.forEach { color ->
            Box(
                Modifier
                    .size(36.dp)
                    .background(Color(color), CircleShape)
                    .then(
                        if (color == selected) {
                            Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (color == selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun TrackingSection(
    state: EditHabitState,
    viewModel: EditHabitViewModel,
    onNewUnit: () -> Unit,
    onManageAppGroups: () -> Unit,
) {
    SectionLabel("What are you tracking?")
    val modes = listOf("Done / not done", "An amount", "Automatic")
    val selectedMode = when {
        state.isAuto -> 2
        state.binary -> 0
        else -> 1
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { i, label ->
            SegmentedButton(
                selected = selectedMode == i,
                onClick = {
                    when (i) {
                        0 -> viewModel.setBinary(true)
                        1 -> viewModel.setBinary(false)
                        else -> viewModel.setAutomatic(SourceType.HEALTH_STEPS)
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                enabled = !state.structureLocked,
            ) { Text(label, maxLines = 1) }
        }
    }
    if (state.isAuto) {
        AutomaticSourceSection(state, viewModel, onManageAppGroups)
        return
    }
    if (!state.binary) {
        var expanded by remember { mutableStateOf(false) }
        val unit = state.selectedUnit
        val locked = state.structureLocked
        Box {
            OutlinedTextField(
                value = unit?.let { if (it.label.isBlank()) it.name else "${it.name} (${it.label})" }.orEmpty(),
                onValueChange = {},
                readOnly = true,
                enabled = !locked,
                label = { Text("Unit") },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            // Transparent layer on top so a tap anywhere on the field opens the menu.
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(enabled = !locked) { expanded = true },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.numberUnits.forEach { u ->
                    DropdownMenuItem(
                        text = { Text(if (u.label.isBlank()) u.name else "${u.name} (${u.label})") },
                        onClick = {
                            viewModel.setUnit(u.id)
                            expanded = false
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("New unit…", color = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        expanded = false
                        onNewUnit()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDateSection(state: EditHabitState, viewModel: EditHabitViewModel) {
    var picking by remember { mutableStateOf(false) }
    SectionLabel("Starts")
    Box {
        OutlinedTextField(
            value = HabitText.relativeDay(state.anchorDate, state.today).let {
                if (it == "Today") "Today (${HabitText.date(state.anchorDate)})" else HabitText.fullDate(state.anchorDate)
            },
            onValueChange = {},
            readOnly = true,
            enabled = !state.structureLocked,
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = !state.structureLocked) { picking = true },
        )
    }
    if (picking) {
        val todayMillis = state.today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.anchorDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMillis
                override fun isSelectableYear(year: Int) = year >= state.today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        viewModel.setStartDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun AutomaticSourceSection(state: EditHabitState, viewModel: EditHabitViewModel, onManageAppGroups: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refreshSourceStatus() }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshSourceStatus()
        onPauseOrDispose { }
    }

    val options = listOf(
        SourceType.HEALTH_STEPS to "Steps (Health Connect)",
        SourceType.USAGE_SCREEN_TIME to "Screen time (all apps)",
        SourceType.USAGE_APP_GROUP to "Time in an app group",
    )
    Column(Modifier.selectableGroup()) {
        options.forEach { (type, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = state.autoSource == type,
                        enabled = !state.structureLocked,
                        role = Role.RadioButton,
                        onClick = { viewModel.setAutomatic(type) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.autoSource == type, onClick = null, enabled = !state.structureLocked)
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
    }

    when (state.autoSource) {
        SourceType.HEALTH_STEPS -> {
            when (state.stepsStatus) {
                null -> Unit
                SourceStatus.READY -> Hint("✓ Connected to Health Connect. Steps sync when you open the app and about once an hour.")
                SourceStatus.NEEDS_PERMISSION -> {
                    Hint("KeepUp needs permission to read your steps.")
                    Button(onClick = { scope.launch { permissionLauncher.launch(viewModel.stepsPermissions()) } }) {
                        Text("Connect Health Connect")
                    }
                }
                SourceStatus.NEEDS_UPDATE -> Hint("Health Connect needs an update from the Play Store.")
                SourceStatus.UNAVAILABLE -> Hint("Health Connect isn't available on this phone.")
            }
            Hint(
                "Make sure your step app (e.g. Google Fit) shares steps with Health Connect: " +
                    "Settings → Health Connect → Data and access → Steps.",
            )
        }
        SourceType.USAGE_SCREEN_TIME, SourceType.USAGE_APP_GROUP -> {
            if (state.autoSource == SourceType.USAGE_APP_GROUP) {
                if (state.appGroups.isEmpty()) {
                    Hint("You don't have any app groups yet.")
                } else {
                    AppGroupPicker(state, viewModel)
                }
                TextButton(onClick = onManageAppGroups) { Text("Manage app groups") }
            } else {
                Hint("Total time apps are open on screen, not counting the home screen. Measured in minutes.")
            }
            if (state.usageAccess) {
                Hint("✓ Usage access allowed.")
            } else {
                Hint("KeepUp needs Usage access to measure screen time. Find KeepUp in the list and turn it on.")
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                    Text("Allow usage access")
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun AppGroupPicker(state: EditHabitState, viewModel: EditHabitViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.appGroups.firstOrNull { it.id == state.appGroupId }
    Box {
        OutlinedTextField(
            value = selected?.let { "${it.name} (${it.packages.size} apps)" }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = !state.structureLocked,
            label = { Text("App group") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = !state.structureLocked) { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.appGroups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.name) },
                    onClick = {
                        viewModel.setAppGroup(group.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PeriodSection(state: EditHabitState, viewModel: EditHabitViewModel) {
    SectionLabel("How often?")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Period.entries.forEachIndexed { i, period ->
            SegmentedButton(
                selected = state.period == period,
                onClick = { viewModel.setPeriod(period) },
                shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
                enabled = !state.structureLocked,
            ) {
                Text(
                    when (period) {
                        Period.DAILY -> "Daily"
                        Period.WEEKLY -> "Weekly"
                        Period.MONTHLY -> "Monthly"
                    },
                )
            }
        }
    }
    val anchor = state.anchorDate
    val locale = LocalLocale.current.platformLocale
    val hint = when (state.period) {
        Period.DAILY -> null
        Period.WEEKLY -> {
            val start = anchor.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            val end = anchor.minusDays(1).dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            "Weeks run $start → $end, starting on the start date."
        }
        Period.MONTHLY -> "Months run from day ${anchor.dayOfMonth} to the day before it in the next month."
    }
    hint?.let { Hint(it) }
    if (state.structureLocked) {
        Hint("Start date, tracking type, unit and period can't change after the first day. Archive this habit and create a new one instead.")
    }
}

@Composable
private fun GoalSection(state: EditHabitState, viewModel: EditHabitViewModel) {
    SectionLabel("Goal")
    if (state.targetFixed) {
        Hint("Mark it done once each day.")
    } else {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(Direction.AT_LEAST to "At least", Direction.AT_MOST to "At most").forEachIndexed { i, (dir, label) ->
                SegmentedButton(
                    selected = state.direction == dir,
                    onClick = { viewModel.setDirection(dir) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label) }
            }
        }
        val unitLabel = if (state.binary && !state.isAuto) "days" else state.selectedUnit?.label.orEmpty()
        OutlinedTextField(
            value = state.targetText,
            onValueChange = viewModel::setTarget,
            label = { Text(if (state.binary && !state.isAuto) "Days per ${periodNoun(state.period)}" else "Target per ${periodNoun(state.period)}") },
            suffix = if (unitLabel.isNotBlank()) ({ Text(unitLabel) }) else null,
            singleLine = true,
            isError = state.targetText.isNotBlank() && state.target == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.direction == Direction.AT_MOST && !state.isAuto) {
            Hint("Log what you do each day, or tap \"Log 0\". A day with nothing logged counts as missed.")
        }
    }
    state.goalEffectiveFrom?.let {
        Hint("Goal changes take effect on ${HabitText.date(it)}. Current and past periods keep their goal.")
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun periodNoun(period: Period) = when (period) {
    Period.DAILY -> "day"
    Period.WEEKLY -> "week"
    Period.MONTHLY -> "month"
}
