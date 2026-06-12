package com.informedcitizen.ui.settings.sections

import androidx.compose.runtime.Composable
import com.informedcitizen.ui.settings.LocalSettingsNavigation
import com.informedcitizen.ui.settings.SettingsLinkRow
import com.informedcitizen.ui.settings.SettingsSection
import com.informedcitizen.ui.settings.SettingsSectionHeader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSourcesSettingsSection @Inject constructor() : SettingsSection {
    // Between Saved reps (50) and About (100).
    override val order = 60

    @Composable
    override fun Content() {
        val nav = LocalSettingsNavigation.current
        SettingsSectionHeader("Data")
        SettingsLinkRow(
            label = "Data sources",
            supporting = "Fetch directly from Congress.gov with your own API key.",
            onClick = nav.onOpenDataSources,
        )
    }
}
