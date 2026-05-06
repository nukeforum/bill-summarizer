package com.informedcitizen.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.informedcitizen.theme.ThemeFamily
import com.informedcitizen.theme.ThemeMode
import com.informedcitizen.theme.ThemePreference
import com.informedcitizen.theme.family
import com.informedcitizen.theme.mode
import com.informedcitizen.theme.withFamily
import com.informedcitizen.theme.withMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onCalendarClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preference by viewModel.preference.collectAsStateWithLifecycle()
    val crashReportingEnabled by viewModel.crashReportingEnabled.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        SettingsContent(
            preference = preference,
            crashReportingEnabled = crashReportingEnabled,
            innerPadding = innerPadding,
            onPreferenceChange = viewModel::setPreference,
            onCrashReportingEnabledChange = viewModel::setCrashReportingEnabled,
            onForgetLocation = viewModel::forgetLocation,
            onCalendarClick = onCalendarClick,
        )
    }
}

@Composable
private fun SettingsContent(
    preference: ThemePreference,
    crashReportingEnabled: Boolean,
    innerPadding: PaddingValues,
    onPreferenceChange: (ThemePreference) -> Unit,
    onCrashReportingEnabledChange: (Boolean) -> Unit,
    onForgetLocation: () -> Unit,
    onCalendarClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(innerPadding)) {
        SectionHeader("Theme")
        FamilySegmentedRow(
            family = preference.family,
            onFamilyChange = { newFamily ->
                onPreferenceChange(preference.withFamily(newFamily))
            },
        )
        ModeRadioGroup(
            mode = preference.mode,
            onModeChange = { newMode ->
                onPreferenceChange(preference.withMode(newMode))
            },
        )
        SectionHeader("Crash reporting")
        CrashReportingRow(
            enabled = crashReportingEnabled,
            onEnabledChange = onCrashReportingEnabledChange,
        )
        SectionHeader("Congress")
        SettingsLinkRow(
            label = "Congress calendar",
            supporting = "When the House and Senate are in session.",
            onClick = onCalendarClick,
        )
        HorizontalDivider()
        TextButton(onClick = onForgetLocation) {
            Text("Forget my saved location")
        }
        Text(
            "Clears the state and district stored on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun SettingsLinkRow(
    label: String,
    supporting: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FamilySegmentedRow(
    family: ThemeFamily,
    onFamilyChange: (ThemeFamily) -> Unit,
) {
    val families = ThemeFamily.entries
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        families.forEachIndexed { index, candidate ->
            SegmentedButton(
                selected = candidate == family,
                onClick = { onFamilyChange(candidate) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = families.size),
                label = { Text(candidate.displayName) },
            )
        }
    }
}

@Composable
private fun ModeRadioGroup(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().selectableGroup().padding(top = 8.dp)) {
        ThemeMode.entries.forEach { candidate ->
            ModeRow(
                label = candidate.displayName,
                selected = candidate == mode,
                onSelect = { onModeChange(candidate) },
            )
        }
    }
}

@Composable
private fun ModeRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CrashReportingRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = enabled,
                role = Role.Switch,
                onClick = { onEnabledChange(!enabled) },
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

private val ThemeFamily.displayName: String
    get() = when (this) {
        ThemeFamily.MATERIAL -> "Material"
        ThemeFamily.SOLARIZED -> "Solarized"
    }

private val ThemeMode.displayName: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "Follow system"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }
