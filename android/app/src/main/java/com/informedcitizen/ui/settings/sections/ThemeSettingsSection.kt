package com.informedcitizen.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.informedcitizen.data.repository.ThemePreferenceRepository
import com.informedcitizen.theme.ThemeFamily
import com.informedcitizen.theme.ThemeMode
import com.informedcitizen.theme.ThemePreference
import com.informedcitizen.theme.family
import com.informedcitizen.theme.mode
import com.informedcitizen.theme.withFamily
import com.informedcitizen.theme.withMode
import com.informedcitizen.ui.settings.SettingsSection
import com.informedcitizen.ui.settings.SettingsSectionHeader
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeSettingsSection @Inject constructor(
    private val themePrefs: ThemePreferenceRepository,
) : SettingsSection {
    override val order = 10

    @Composable
    override fun Content() {
        val preference by themePrefs.preference.collectAsStateWithLifecycle(
            initialValue = ThemePreference.DEFAULT,
        )
        val scope = rememberCoroutineScope()
        SettingsSectionHeader("Theme")
        FamilySegmentedRow(
            family = preference.family,
            onFamilyChange = { newFamily ->
                scope.launch { themePrefs.set(preference.withFamily(newFamily)) }
            },
        )
        ModeRadioGroup(
            mode = preference.mode,
            onModeChange = { newMode ->
                scope.launch { themePrefs.set(preference.withMode(newMode)) }
            },
        )
    }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = candidate == mode,
                        role = Role.RadioButton,
                        onClick = { onModeChange(candidate) },
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RadioButton(selected = candidate == mode, onClick = null)
                Text(text = candidate.displayName, style = MaterialTheme.typography.bodyLarge)
            }
        }
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
