package com.informedcitizen.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.data.repository.CrashReportingPreferenceRepository
import com.informedcitizen.ui.settings.SettingsSection
import com.informedcitizen.ui.settings.SettingsSectionHeader
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashReportingSettingsSection @Inject constructor(
    private val crashPrefs: CrashReportingPreferenceRepository,
    private val crashReporter: CrashReporter,
) : SettingsSection {
    override val order = 30

    @Composable
    override fun Content() {
        val enabled by crashPrefs.enabled.collectAsStateWithLifecycle(initialValue = false)
        val scope = rememberCoroutineScope()

        SettingsSectionHeader("Crash reporting")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = enabled,
                    role = Role.Switch,
                    onClick = {
                        scope.launch {
                            val next = !enabled
                            crashPrefs.set(next)
                            crashReporter.setCollectionEnabled(next)
                            if (!next) crashReporter.deleteUnsentReports()
                        }
                    },
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Send crash reports",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Off by default. When on, anonymous crash data " +
                        "(stack traces, device model, OS, app version) is sent " +
                        "to Google Firebase to help fix bugs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}
