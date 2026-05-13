package com.informedcitizen.ui.settings.sections

import androidx.compose.runtime.Composable
import com.informedcitizen.ui.settings.LocalSettingsNavigation
import com.informedcitizen.ui.settings.SettingsLinkRow
import com.informedcitizen.ui.settings.SettingsSection
import com.informedcitizen.ui.settings.SettingsSectionHeader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CongressCalendarSettingsSection @Inject constructor() : SettingsSection {
    override val order = 40

    @Composable
    override fun Content() {
        val nav = LocalSettingsNavigation.current
        SettingsSectionHeader("Congress")
        SettingsLinkRow(
            label = "Congress calendar",
            supporting = "When the House and Senate are in session.",
            onClick = nav.onOpenCalendar,
        )
    }
}
