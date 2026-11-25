package io.github.carsonfiechtner.keepup.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.carsonfiechtner.keepup.autotrack.AutoTrackingSync
import io.github.carsonfiechtner.keepup.autotrack.SourceStatus
import io.github.carsonfiechtner.keepup.autotrack.StepsSource
import io.github.carsonfiechtner.keepup.autotrack.UsageSource
import io.github.carsonfiechtner.keepup.data.export.BackupSummary
import io.github.carsonfiechtner.keepup.data.export.ExportFile
import io.github.carsonfiechtner.keepup.data.export.ExportService
import io.github.carsonfiechtner.keepup.data.export.ImportService
import io.github.carsonfiechtner.keepup.data.export.InvalidBackupException
import io.github.carsonfiechtner.keepup.data.prefs.SettingsRepository
import io.github.carsonfiechtner.keepup.data.prefs.ThemeMode
import io.github.carsonfiechtner.keepup.reminders.ReminderNotifier
import io.github.carsonfiechtner.keepup.reminders.ReminderScheduler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val exportService: ExportService,
    private val importService: ImportService,
    private val reminderScheduler: ReminderScheduler,
    private val autoSync: AutoTrackingSync,
    private val steps: StepsSource,
    private val usage: UsageSource,
    private val reminderNotifier: ReminderNotifier,
    private val clock: Clock,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val lastExportAt: StateFlow<Instant?> = settingsRepository.lastExportAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val iconFollowsStreak: StateFlow<Boolean> = settingsRepository.iconFollowsStreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setIconFollowsStreak(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setIconFollowsStreak(enabled) }
    }

    val reminderEnabled: StateFlow<Boolean> = settingsRepository.reminderEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val reminderTime: StateFlow<LocalTime> = settingsRepository.reminderTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalTime.of(21, 0))

    val lastAutoSyncAt: StateFlow<Instant?> = settingsRepository.lastAutoSyncAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _stepsStatus = MutableStateFlow<SourceStatus?>(null)
    val stepsStatus: StateFlow<SourceStatus?> = _stepsStatus.asStateFlow()

    private val _usageAccess = MutableStateFlow(false)
    val usageAccess: StateFlow<Boolean> = _usageAccess.asStateFlow()

    fun refreshSources() {
        _usageAccess.value = usage.hasAccess()
        viewModelScope.launch { _stepsStatus.value = steps.status() }
    }

    suspend fun stepsPermissions(): Set<String> = steps.permissionsToRequest()

    fun syncNow() {
        viewModelScope.launch {
            messageChannel.send(if (autoSync.syncAll()) "Synced" else "Some sources couldn't be read")
        }
    }

    private val _notificationsAllowed = MutableStateFlow(true)
    val notificationsAllowed: StateFlow<Boolean> = _notificationsAllowed.asStateFlow()

    /** Call on resume; the user may have changed notification permission in system settings. */
    fun refreshNotificationPermission() {
        _notificationsAllowed.value = reminderNotifier.canNotify()
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReminderEnabled(enabled)
            reminderScheduler.reschedule()
        }
    }

    fun setReminderTime(time: LocalTime) {
        viewModelScope.launch {
            settingsRepository.setReminderTime(time)
            reminderScheduler.reschedule()
        }
    }

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    /** A parsed backup waiting for the user to confirm the restore. */
    private val _pendingRestore = MutableStateFlow<Pair<ExportFile, BackupSummary>?>(null)
    val pendingRestore: StateFlow<Pair<ExportFile, BackupSummary>?> = _pendingRestore.asStateFlow()

    fun today(): LocalDate = LocalDate.now(clock)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            try {
                exportService.writeTo(uri)
                settingsRepository.setLastExportAt(clock.instant())
                messageChannel.send("Export saved")
            } catch (e: Exception) {
                messageChannel.send("Export failed: ${e.message}")
            }
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                exportService.writeCsvTo(uri)
                messageChannel.send("CSV saved")
            } catch (e: Exception) {
                messageChannel.send("Export failed: ${e.message}")
            }
        }
    }

    /** Step 1 of a restore: read and validate the file, then ask for confirmation. */
    fun pickBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val file = importService.read(uri)
                _pendingRestore.value = file to importService.summarize(file)
            } catch (e: InvalidBackupException) {
                messageChannel.send(e.message ?: "Invalid backup")
            }
        }
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }

    /** Step 2: replace all data. The previous data is kept in an automatic backup first. */
    fun confirmRestore() {
        val (file, _) = _pendingRestore.value ?: return
        _pendingRestore.value = null
        viewModelScope.launch {
            try {
                importService.restore(file)
                messageChannel.send("Backup restored")
            } catch (e: Exception) {
                messageChannel.send("Restore failed, nothing was changed: ${e.message}")
            }
        }
    }
}
