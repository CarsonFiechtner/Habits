package io.github.carsonfiechtner.keepup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.carsonfiechtner.keepup.data.prefs.SettingsRepository
import io.github.carsonfiechtner.keepup.data.prefs.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /** Ask for the notification permission once, when the nightly reminder is on. */
    val shouldAskNotifications: StateFlow<Boolean> = combine(
        settingsRepository.reminderEnabled,
        settingsRepository.notificationPermissionAsked,
    ) { enabled, asked -> enabled && !asked }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onNotificationPermissionAsked() {
        viewModelScope.launch { settingsRepository.setNotificationPermissionAsked() }
    }
}
