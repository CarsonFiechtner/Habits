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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.carsonfiechtner.keepup.BuildConfig
import io.github.carsonfiechtner.keepup.R
import io.github.carsonfiechtner.keepup.data.export.BackupSummary
import io.github.carsonfiechtner.keepup.data.prefs.ThemeMode
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenUnits: () -> Unit,
    onOpenArchived: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val lastExportAt by viewModel.lastExportAt.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
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

    pendingRestore?.let { (_, summary) ->
        RestoreDialog(summary, onConfirm = viewModel::confirmRestore, onDismiss = viewModel::cancelRestore)
    }
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
