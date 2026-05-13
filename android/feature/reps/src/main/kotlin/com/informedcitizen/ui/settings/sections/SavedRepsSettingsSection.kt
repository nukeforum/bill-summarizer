package com.informedcitizen.ui.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.informedcitizen.data.repository.SavedRepsRepository
import com.informedcitizen.ui.settings.SettingsSection
import com.informedcitizen.ui.settings.SettingsSectionHeader
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedRepsSettingsSection @Inject constructor(
    private val savedReps: SavedRepsRepository,
) : SettingsSection {
    override val order = 50

    @Composable
    override fun Content() {
        val hasSavedReps by savedReps.savedIds
            .map { it.isNotEmpty() }
            .collectAsStateWithLifecycle(initialValue = false)
        val scope = rememberCoroutineScope()

        SettingsSectionHeader("Your representatives")
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
            onClick = { scope.launch { savedReps.forget() } },
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
