package com.informedcitizen.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.informedcitizen.crash.BuildEnvironment
import com.informedcitizen.data.repository.CrashReportingPreferenceRepository
import com.informedcitizen.data.repository.ThemePreferenceRepository
import com.informedcitizen.theme.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePrefs: ThemePreferenceRepository,
    private val crashPrefs: CrashReportingPreferenceRepository,
    private val buildEnvironment: BuildEnvironment,
) : ViewModel() {

    val preference: StateFlow<ThemePreference> = themePrefs.preference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemePreference.DEFAULT)

    val crashReportingEnabled: StateFlow<Boolean> = crashPrefs.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setPreference(pref: ThemePreference) {
        viewModelScope.launch { themePrefs.set(pref) }
    }

    fun setCrashReportingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            crashPrefs.set(enabled)
            if (!buildEnvironment.isDebuggable) {
                val crashlytics = FirebaseCrashlytics.getInstance()
                crashlytics.isCrashlyticsCollectionEnabled = enabled
                if (!enabled) {
                    crashlytics.deleteUnsentReports()
                }
            }
        }
    }
}
