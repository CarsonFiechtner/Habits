package io.github.carsonfiechtner.keepup.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.carsonfiechtner.keepup.BuildConfig
import androidx.health.connect.client.PermissionController
import io.github.carsonfiechtner.keepup.R
import io.github.carsonfiechtner.keepup.autotrack.SourceStatus
import kotlinx.coroutines.launch
import io.github.carsonfiechtner.keepup.data.export.BackupSummary
import io.github.carsonfiechtner.keepup.data.prefs.ThemeMode
import android.content.Intent
import android.provider.Settings
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenUnits: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenAppGroups: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val lastExportAt by viewModel.lastExportAt.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val reminderEnabled by viewModel.reminderEnabled.collectAsStateWithLifecycle()
    val reminderTime by viewModel.reminderTime.collectAsStateWithLifecycle()
    val notificationsAllowed by viewModel.notificationsAllowed.collectAsStateWithLifecycle()
    var pickingTime by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val stepsStatus by viewModel.stepsStatus.collectAsStateWithLifecycle()
    val lastAutoSyncAt by viewModel.lastAutoSyncAt.collectAsStateWithLifecycle()
    val usageAccess by viewModel.usageAccess.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refreshSources() }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshNotificationPermission()
        viewModel.refreshSources()
        onPauseOrDispose { }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::pickBackup)
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Habits")
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenUnits),
                supportingContent = { Text("Create and edit units like liters or pages") },
            ) { Text("Units") }
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenArchived),
                supportingContent = { Text("Restore or delete archived habits") },
            ) { Text("Archived habits") }
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenAppGroups),
                supportingContent = { Text("Named sets of apps for screen-time habits") },
            ) { Text("App groups") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Reminders")
            ListItem(
                modifier = Modifier.toggleable(
                    value = reminderEnabled,
                    role = Role.Switch,
                    onValueChange = viewModel::setReminderEnabled,
                ),
                supportingContent = { Text("One notification listing goals you haven't met yet") },
                trailingContent = { Switch(checked = reminderEnabled, onCheckedChange = null) },
            ) { Text("Nightly reminder") }
            ListItem(
                modifier = Modifier.clickable(enabled = reminderEnabled) { pickingTime = true },
                supportingContent = { Text(reminderTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))) },
            ) { Text("Reminder time") }
            if (reminderEnabled && !notificationsAllowed) {
                ListItem(
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                    supportingContent = { Text("Tap to allow notifications so the reminder can appear") },
                ) { Text("Notifications are off", color = MaterialTheme.colorScheme.error) }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Automatic tracking")
            ListItem(
                modifier = Modifier.clickable(enabled = stepsStatus == SourceStatus.NEEDS_PERMISSION) {
                    scope.launch { healthPermissionLauncher.launch(viewModel.stepsPermissions()) }
                },
                supportingContent = {
                    Text(
                        when (stepsStatus) {
                            SourceStatus.READY -> "Connected · steps"
                            SourceStatus.NEEDS_PERMISSION -> "Not connected · tap to allow reading steps"
                            SourceStatus.NEEDS_UPDATE -> "Needs an update from the Play Store"
                            SourceStatus.UNAVAILABLE -> "Not available on this phone"
                            null -> ""
                        },
                    )
                },
            ) { Text("Health Connect") }
            ListItem(
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                supportingContent = {
                    Text(if (usageAccess) "Allowed · screen time and app groups" else "Not allowed · tap to open Usage access settings")
                },
            ) { Text("Usage access") }
            ListItem(
                modifier = Modifier.clickable { viewModel.syncNow() },
                supportingContent = {
                    Text(
                        lastAutoSyncAt?.let {
                            "Last synced " + it.atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
                        } ?: "Not synced yet",
                    )
                },
            ) { Text("Sync now") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_appearance))
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    ListItem(
                        leadingContent = { RadioButton(selected = mode == themeMode, onClick = null) },
                        modifier = Modifier.selectable(
                            selected = mode == themeMode,
                            role = Role.RadioButton,
                            onClick = { viewModel.setThemeMode(mode) },
                        ),
                    ) {
                        Text(stringResource(mode.label))
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("Data")
            ListItem(
                modifier = Modifier.clickable { exportLauncher.launch("keepup-export-${viewModel.today()}.json") },
                supportingContent = {
                    val last = lastExportAt
                    Text(
                        if (last == null) {
                            "Save a full backup file. Never exported."
                        } else {
                            val days = ChronoUnit.DAYS.between(last.atZone(ZoneId.systemDefault()).toLocalDate(), viewModel.today())
                            "Save a full backup file. Last export: " + when (days) {
                                0L -> "today"
                                1L -> "yesterday"
                                else -> "$days days ago"
                            }
                        },
                    )
                },
            ) { Text("Export data (JSON)") }
            ListItem(
                modifier = Modifier.clickable { csvLauncher.launch("keepup-entries-${viewModel.today()}.csv") },
                supportingContent = { Text("All entries as a spreadsheet file") },
            ) { Text("Export entries (CSV)") }
            ListItem(
                modifier = Modifier.clickable { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                supportingContent = { Text("Replace all data with a JSON backup") },
            ) { Text("Restore from backup") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_about))
            ListItem(supportingContent = { Text(BuildConfig.VERSION_NAME) }) {
                Text(stringResource(R.string.settings_version))
            }
        }
    }

    if (pickingTime) {
        ReminderTimeDialog(
            initial = reminderTime,
            onDismiss = { pickingTime = false },
            onConfirm = {
                pickingTime = false
                viewModel.setReminderTime(it)
            },
        )
    }

    pendingRestore?.let { (_, summary) ->
        RestoreDialog(summary, onConfirm = viewModel::confirmRestore, onDismiss = viewModel::cancelRestore)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder time") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RestoreDialog(summary: BackupSummary, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace all data?") },
        text = {
            Text(
                "This backup has ${summary.habits} habits and ${summary.entries} entries " +
                    "(exported ${summary.exportedAt.take(10)}). Everything currently in KeepUp will be replaced. " +
                    "A copy of your current data is saved inside the app first.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Replace", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

private val ThemeMode.label: Int
    @StringRes get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
