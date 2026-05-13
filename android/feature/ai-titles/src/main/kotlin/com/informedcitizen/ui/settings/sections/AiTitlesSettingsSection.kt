package com.informedcitizen.ui.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.informedcitizen.data.ai.AiCapability
import com.informedcitizen.data.repository.AiTitlesPreferenceRepository
import com.informedcitizen.data.work.BillSummarizationController
import com.informedcitizen.data.work.SummarizationScope
import com.informedcitizen.featureflags.FeatureFlags
import com.informedcitizen.ui.settings.SettingsSection
import com.informedcitizen.ui.settings.SettingsSectionHeader
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiTitlesSettingsSection @Inject constructor(
    private val capability: AiCapability,
    private val prefs: AiTitlesPreferenceRepository,
    private val controller: BillSummarizationController,
) : SettingsSection {
    override val order = 20

    @Composable
    override fun Content() {
        if (!FeatureFlags.AI_TITLES) return

        val capStatus by capability.status.collectAsStateWithLifecycle(
            initialValue = AiCapability.Status.NotSupported,
        )
        val aiEnabled by prefs.enabled.collectAsStateWithLifecycle(initialValue = false)
        val scope by prefs.scope.collectAsStateWithLifecycle(initialValue = SummarizationScope.DEFAULT)
        val coScope = rememberCoroutineScope()
        var helpOpen by remember { mutableStateOf(false) }

        SettingsSectionHeader("AI title summarization")

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = aiEnabled,
                enabled = capStatus is AiCapability.Status.Available,
                onCheckedChange = { newValue ->
                    coScope.launch { prefs.setEnabled(newValue) }
                },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "AI-summarized titles & topics",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = describeCapability(capStatus),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { helpOpen = true }) {
                Icon(Icons.Outlined.Info, contentDescription = "About AI summarization")
            }
        }

        DownloadAffordances(capStatus, onRequestDownload = capability::requestDownload)

        if (aiEnabled) {
            ScopePicker(
                value = scope,
                onChange = { newScope -> coScope.launch { prefs.setScope(newScope) } },
            )
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextButton(onClick = controller::stopNow) { Text("Stop summarizing now") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = controller::clearCache) { Text("Clear cache") }
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
}

private fun describeCapability(cap: AiCapability.Status): String = when (cap) {
    AiCapability.Status.Available -> "Available — Gemini Nano on this device"
    AiCapability.Status.DownloadAvailable ->
        "Download Gemini Nano to enable AI titles. ~1 GB, one-time, stays on device."
    is AiCapability.Status.ModelDownloading ->
        if (cap.progress >= 0f) "Downloading Gemini Nano… ${(cap.progress * 100).toInt()}%"
        else "Downloading Gemini Nano…"
    is AiCapability.Status.DownloadFailed ->
        "Couldn't finish downloading Gemini Nano. ${cap.reason}"
    AiCapability.Status.NotSupported -> "Not supported on this device"
}

@Composable
private fun DownloadAffordances(cap: AiCapability.Status, onRequestDownload: () -> Unit) {
    when (cap) {
        AiCapability.Status.DownloadAvailable -> {
            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Button(onClick = onRequestDownload) { Text("Download Gemini Nano") }
            }
        }
        is AiCapability.Status.ModelDownloading -> {
            if (cap.progress >= 0f) {
                LinearProgressIndicator(
                    progress = { cap.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        is AiCapability.Status.DownloadFailed -> {
            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Button(onClick = onRequestDownload) { Text("Try again") }
            }
        }
        AiCapability.Status.Available,
        AiCapability.Status.NotSupported -> Unit
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
