package com.informedcitizen.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.informedcitizen.data.ai.AiCapability
import com.informedcitizen.data.work.SummarizationScope
import com.informedcitizen.theme.ThemePreference
import com.informedcitizen.ui.preview.PreviewWrap

private val DefaultAi = SettingsAiUiState()

@PreviewLightDark
@Composable
private fun PreviewSettingsWithSavedReps() = PreviewWrap {
    SettingsContent(
        preference = ThemePreference.MATERIAL_LIGHT,
        crashReportingEnabled = true,
        hasSavedReps = true,
        aiState = DefaultAi,
        innerPadding = PaddingValues(0.dp),
        onPreferenceChange = {},
        onCrashReportingEnabledChange = {},
        onForgetSavedReps = {},
        onCalendarClick = {},
        onAiTitlesEnabledChange = {},
        onSummarizationScopeChange = {},
        onStopNow = {},
        onClearCache = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewSettingsWithoutSavedReps() = PreviewWrap {
    SettingsContent(
        preference = ThemePreference.MATERIAL_SYSTEM,
        crashReportingEnabled = false,
        hasSavedReps = false,
        aiState = DefaultAi,
        innerPadding = PaddingValues(0.dp),
        onPreferenceChange = {},
        onCrashReportingEnabledChange = {},
        onForgetSavedReps = {},
        onCalendarClick = {},
        onAiTitlesEnabledChange = {},
        onSummarizationScopeChange = {},
        onStopNow = {},
        onClearCache = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewSettingsAiOnProgressive50() = PreviewWrap {
    SettingsContent(
        preference = ThemePreference.MATERIAL_LIGHT,
        crashReportingEnabled = false,
        hasSavedReps = false,
        aiState = SettingsAiUiState(
            aiTitlesEnabled = true,
            aiCapability = AiCapability.Status.Available,
            summarizationScope = SummarizationScope.Progressive(50),
        ),
        innerPadding = PaddingValues(0.dp),
        onPreferenceChange = {},
        onCrashReportingEnabledChange = {},
        onForgetSavedReps = {},
        onCalendarClick = {},
        onAiTitlesEnabledChange = {},
        onSummarizationScopeChange = {},
        onStopNow = {},
        onClearCache = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewSettingsAiOnProgressive123() = PreviewWrap {
    SettingsContent(
        preference = ThemePreference.MATERIAL_LIGHT,
        crashReportingEnabled = false,
        hasSavedReps = false,
        aiState = SettingsAiUiState(
            aiTitlesEnabled = true,
            aiCapability = AiCapability.Status.ModelDownloading,
            summarizationScope = SummarizationScope.Progressive(123),
        ),
        innerPadding = PaddingValues(0.dp),
        onPreferenceChange = {},
        onCrashReportingEnabledChange = {},
        onForgetSavedReps = {},
        onCalendarClick = {},
        onAiTitlesEnabledChange = {},
        onSummarizationScopeChange = {},
        onStopNow = {},
        onClearCache = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewSettingsAiNotSupported() = PreviewWrap {
    SettingsContent(
        preference = ThemePreference.MATERIAL_LIGHT,
        crashReportingEnabled = false,
        hasSavedReps = false,
        aiState = SettingsAiUiState(
            aiTitlesEnabled = false,
            aiCapability = AiCapability.Status.NotSupported,
            summarizationScope = SummarizationScope.DEFAULT,
        ),
        innerPadding = PaddingValues(0.dp),
        onPreferenceChange = {},
        onCrashReportingEnabledChange = {},
        onForgetSavedReps = {},
        onCalendarClick = {},
        onAiTitlesEnabledChange = {},
        onSummarizationScopeChange = {},
        onStopNow = {},
        onClearCache = {},
    )
}
