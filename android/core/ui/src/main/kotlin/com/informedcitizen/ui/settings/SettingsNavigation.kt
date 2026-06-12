package com.informedcitizen.ui.settings

import androidx.compose.runtime.compositionLocalOf

/**
 * Navigation callbacks the Settings host plumbs through to sections.
 * Sections read what they need via `LocalSettingsNavigation.current`.
 * Add new entries here as new sections need to navigate elsewhere.
 */
class SettingsNavigation(
    val onOpenCalendar: () -> Unit,
    val onOpenDataSources: () -> Unit,
)

val LocalSettingsNavigation = compositionLocalOf<SettingsNavigation> {
    error(
        "LocalSettingsNavigation is not provided. Wrap SettingsSectionHost in " +
            "CompositionLocalProvider(LocalSettingsNavigation provides ...).",
    )
}
