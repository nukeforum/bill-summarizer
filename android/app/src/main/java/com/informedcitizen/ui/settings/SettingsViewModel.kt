package com.informedcitizen.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.data.ai.AiCapability
import com.informedcitizen.data.repository.AiTitlesPreferenceRepository
import com.informedcitizen.data.repository.CrashReportingPreferenceRepository
import com.informedcitizen.data.repository.SavedRepsRepository
import com.informedcitizen.data.repository.ThemePreferenceRepository
import com.informedcitizen.data.work.BillSummarizationController
import com.informedcitizen.data.work.SummarizationScope
import com.informedcitizen.theme.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsAiUiState(
    val aiTitlesEnabled: Boolean = false,
    val aiCapability: AiCapability.Status = AiCapability.Status.NotSupported,
    val summarizationScope: SummarizationScope = SummarizationScope.DEFAULT,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePrefs: ThemePreferenceRepository,
    private val crashPrefs: CrashReportingPreferenceRepository,
    private val crashReporter: CrashReporter,
    private val savedReps: SavedRepsRepository,
    private val aiPrefs: AiTitlesPreferenceRepository,
    private val aiCapability: AiCapability,
    private val controller: BillSummarizationController,
) : ViewModel() {

    val preference: StateFlow<ThemePreference> = themePrefs.preference
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreference.DEFAULT)

    val crashReportingEnabled: StateFlow<Boolean> = crashPrefs.enabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasSavedReps: StateFlow<Boolean> = savedReps.savedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val uiState: StateFlow<SettingsAiUiState> = combine(
        aiPrefs.enabled,
        aiPrefs.scope,
        aiCapability.status,
    ) { enabled, scope, capStatus ->
        SettingsAiUiState(
            aiTitlesEnabled = enabled,
            summarizationScope = scope,
            aiCapability = capStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsAiUiState())

    fun setPreference(pref: ThemePreference) {
        viewModelScope.launch { themePrefs.set(pref) }
    }

    fun setCrashReportingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            crashPrefs.set(enabled)
            crashReporter.setCollectionEnabled(enabled)
            if (!enabled) {
                crashReporter.deleteUnsentReports()
            }
        }
    }

    fun forgetSavedReps() {
        viewModelScope.launch { savedReps.forget() }
    }

    fun setAiTitlesEnabled(enabled: Boolean) {
        viewModelScope.launch { aiPrefs.setEnabled(enabled) }
    }

    fun setSummarizationScope(scope: SummarizationScope) {
        viewModelScope.launch { aiPrefs.setScope(scope) }
    }

    fun stopSummarizingNow() = controller.stopNow()
    fun clearAiCache() = controller.clearCache()

    fun requestModelDownload() = aiCapability.requestDownload()
}
