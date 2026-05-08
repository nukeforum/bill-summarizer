package com.informedcitizen.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.informedcitizen.data.ai.AiCapability
import com.informedcitizen.data.work.SummarizationScope
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
    val hasSavedReps by viewModel.hasSavedReps.collectAsStateWithLifecycle()
    val aiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            hasSavedReps = hasSavedReps,
            aiState = aiState,
            innerPadding = innerPadding,
            onPreferenceChange = viewModel::setPreference,
            onCrashReportingEnabledChange = viewModel::setCrashReportingEnabled,
            onForgetSavedReps = viewModel::forgetSavedReps,
            onCalendarClick = onCalendarClick,
            onAiTitlesEnabledChange = viewModel::setAiTitlesEnabled,
            onSummarizationScopeChange = viewModel::setSummarizationScope,
            onStopNow = viewModel::stopSummarizingNow,
            onClearCache = viewModel::clearAiCache,
        )
    }
}

@Composable
internal fun SettingsContent(
    preference: ThemePreference,
    crashReportingEnabled: Boolean,
    hasSavedReps: Boolean,
    aiState: SettingsAiUiState,
    innerPadding: PaddingValues,
    onPreferenceChange: (ThemePreference) -> Unit,
    onCrashReportingEnabledChange: (Boolean) -> Unit,
    onForgetSavedReps: () -> Unit,
    onCalendarClick: () -> Unit,
    onAiTitlesEnabledChange: (Boolean) -> Unit,
    onSummarizationScopeChange: (SummarizationScope) -> Unit,
    onStopNow: () -> Unit,
    onClearCache: () -> Unit,
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
        SectionHeader("AI title summarization")
        AiTitlesSection(
            state = aiState,
            onAiTitlesEnabledChange = onAiTitlesEnabledChange,
            onSummarizationScopeChange = onSummarizationScopeChange,
            onStopNow = onStopNow,
            onClearCache = onClearCache,
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
        SectionHeader("Your representatives")
        Text(
            "We store the IDs of your three representatives on this device so " +
                "we can show you the bills they've sponsored. Your home address " +
                "and ZIP are never saved.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        ForgetSavedRepsRow(
            enabled = hasSavedReps,
            onClick = onForgetSavedReps,
        )
    }
}

@Composable
private fun AiTitlesSection(
    state: SettingsAiUiState,
    onAiTitlesEnabledChange: (Boolean) -> Unit,
    onSummarizationScopeChange: (SummarizationScope) -> Unit,
    onStopNow: () -> Unit,
    onClearCache: () -> Unit,
) {
    var helpOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = state.aiTitlesEnabled,
            enabled = state.aiCapability != AiCapability.Status.NotSupported,
            onCheckedChange = onAiTitlesEnabledChange,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "AI-summarized titles & topics",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = when (state.aiCapability) {
                    AiCapability.Status.Available -> "Available — Gemini Nano on this device"
                    AiCapability.Status.ModelDownloading -> "Available — model downloading"
                    AiCapability.Status.NotSupported -> "Not supported on this device"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { helpOpen = true }) {
            Icon(Icons.Outlined.Info, contentDescription = "About AI summarization")
        }
    }

    if (state.aiTitlesEnabled) {
        ScopePicker(
            value = state.summarizationScope,
            onChange = onSummarizationScopeChange,
        )
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            TextButton(onClick = onStopNow) { Text("Stop summarizing now") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onClearCache) { Text("Clear cache") }
        }
    }

    if (helpOpen) {
        AlertDialog(
            onDismissRequest = { helpOpen = false },
            title = { Text("About AI title summarization") },
            text = {
                Text(
                    "Generated entirely on your device using Gemini Nano. " +
                        "No bill data leaves the device. " +
                        "When the topic filter is on, bills that haven't been summarized yet are hidden. " +
                        "Long-press a bill in the list to re-summarize it; that bypasses the daily cap.",
                )
            },
            confirmButton = {
                TextButton(onClick = { helpOpen = false }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun ScopePicker(
    value: SummarizationScope,
    onChange: (SummarizationScope) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        ScopeRadio("Floor-action only", value is SummarizationScope.FloorActionOnly) {
            onChange(SummarizationScope.FloorActionOnly)
        }
        ScopeRadio("Recent (last 60 days)", value is SummarizationScope.Recent60Days) {
            onChange(SummarizationScope.Recent60Days)
        }
        ScopeRadio("Progressive — cap per day", value is SummarizationScope.Progressive) {
            onChange(SummarizationScope.Progressive(50))
        }
        if (value is SummarizationScope.Progressive) {
            ProgressiveCapRow(
                cap = value.capPerDay,
                onChange = { newCap -> onChange(SummarizationScope.Progressive(newCap)) },
            )
        }
        ScopeRadio("All eligible", value is SummarizationScope.All) {
            onChange(SummarizationScope.All)
        }
    }
}

@Composable
private fun ScopeRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressiveCapRow(cap: Int, onChange: (Int) -> Unit) {
    val presets = listOf(10, 25, 50, 100)
    var custom by remember(cap) { mutableStateOf(if (cap !in presets) cap.toString() else "") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 8.dp),
    ) {
        presets.forEach { preset ->
            FilterChip(
                selected = cap == preset && custom.isEmpty(),
                onClick = { onChange(preset); custom = "" },
                label = { Text(preset.toString()) },
            )
            Spacer(Modifier.width(4.dp))
        }
        OutlinedTextField(
            value = custom,
            onValueChange = { input ->
                custom = input.filter(Char::isDigit).take(3)
                custom.toIntOrNull()?.takeIf { it in 1..500 }?.let(onChange)
            },
            label = { Text("Custom") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp).height(56.dp),
        )
    }
}

@Composable
private fun ForgetSavedRepsRow(enabled: Boolean, onClick: () -> Unit) {
    val color =
        if (enabled) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Forget my representatives",
                style = MaterialTheme.typography.bodyLarge,
                color = color,
            )
            Text(
                text = if (enabled) {
                    "Removes them from this device. The picker will re-prompt next time you open the Reps tab."
                } else {
                    "No representatives are saved on this device yet."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
