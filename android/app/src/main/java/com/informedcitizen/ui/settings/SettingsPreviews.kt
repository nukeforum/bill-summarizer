package com.informedcitizen.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.informedcitizen.theme.ThemePreference
import com.informedcitizen.ui.preview.MaterialPreviewTheme

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

@Composable
private fun PreviewWrap(content: @Composable () -> Unit) {
    MaterialPreviewTheme {
        Surface(modifier = Modifier.fillMaxSize()) { content() }
    }
}
