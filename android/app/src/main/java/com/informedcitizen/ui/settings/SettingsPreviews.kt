package com.informedcitizen.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.informedcitizen.theme.ThemePreference
import com.informedcitizen.ui.preview.PreviewWrap

@PreviewLightDark
@Composable
private fun PreviewSettingsWithSavedReps() = PreviewWrap {
    SettingsContent(
        preference = ThemePreference.MATERIAL_LIGHT,
        crashReportingEnabled = true,
        hasSavedReps = true,
        innerPadding = PaddingValues(0.dp),
        onPreferenceChange = {},
        onCrashReportingEnabledChange = {},
        onForgetSavedReps = {},
        onCalendarClick = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewSettingsWithoutSavedReps() = PreviewWrap {
    SettingsContent(
        preference = ThemePreference.MATERIAL_SYSTEM,
        crashReportingEnabled = false,
        hasSavedReps = false,
        innerPadding = PaddingValues(0.dp),
        onPreferenceChange = {},
        onCrashReportingEnabledChange = {},
        onForgetSavedReps = {},
        onCalendarClick = {},
    )
}

