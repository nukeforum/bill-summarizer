package com.informedcitizen.ui.reps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.informedcitizen.ui.util.openInCustomTab

private const val HOUSE_GOV_LOOKUP =
    "https://www.house.gov/representatives/find-your-representative"

private val ALL_STATES = listOf(
    "AL",
    "AK",
    "AZ",
    "AR",
    "CA",
    "CO",
    "CT",
    "DE",
    "FL",
    "GA",
    "HI",
    "ID",
    "IL",
    "IN",
    "IA",
    "KS",
    "KY",
    "LA",
    "ME",
    "MD",
    "MA",
    "MI",
    "MN",
    "MS",
    "MO",
    "MT",
    "NE",
    "NV",
    "NH",
    "NJ",
    "NM",
    "NY",
    "NC",
    "ND",
    "OH",
    "OK",
    "OR",
    "PA",
    "RI",
    "SC",
    "SD",
    "TN",
    "TX",
    "UT",
    "VT",
    "VA",
    "WA",
    "WV",
    "WI",
    "WY",
    "DC",
    "AS",
    "GU",
    "MP",
    "PR",
    "VI",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LocationPickerEvent.Saved -> onSaved()
            }
        }
    }

    val context = LocalContext.current
    var stateExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                paddingValues = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp
                )
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Where do you live?",
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            "We'll show you your House representative and senators, and the bills they've sponsored or cosponsored.",
            style = MaterialTheme.typography.bodyMedium,
        )

        ExposedDropdownMenuBox(
            expanded = stateExpanded,
            onExpandedChange = { stateExpanded = it },
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                value = state.selectedState ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("State or territory") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(stateExpanded) },
            )

            ExposedDropdownMenu(
                expanded = stateExpanded,
                onDismissRequest = { stateExpanded = false },
            ) {
                ALL_STATES.forEach { code ->
                    DropdownMenuItem(
                        text = { Text(code) },
                        onClick = {
                            viewModel.selectState(code)
                            stateExpanded = false
                        },
                    )
                }
            }
        }

        ModeSegmentedRow(
            mode = state.mode,
            zipLookupAvailable = state.isZipLookupAvailable,
            onSelectMode = { it: LocationPickerMode -> viewModel.selectMode(it) },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (state.mode) {
                LocationPickerMode.Pick -> PickModeContent(
                    state = state,
                    onSelectDistrict = { it: Int -> viewModel.selectDistrict(it) },
                )

                LocationPickerMode.Lookup -> LookupModeContent(
                    state = state,
                    onZipChanged = { it: String -> viewModel.onZipChanged(it) },
                    onLookupZip = { viewModel.lookupZip() },
                    onOpenHouseGov = { openInCustomTab(context, HOUSE_GOV_LOOKUP) },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.districtHint == DistrictHint.SaveFailed) {
                Text(
                    "Couldn't load representatives for that location. Check your connection and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Button(onClick = { viewModel.save() },
                    // Reserve space at the bottom so scrollable content doesn't sit
                    // beneath the pinned save bar.
                    enabled = state.canSave) { Text("Save") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSegmentedRow(
    mode: LocationPickerMode,
    zipLookupAvailable: Boolean,
    onSelectMode: (LocationPickerMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == LocationPickerMode.Pick,
            onClick = { onSelectMode(LocationPickerMode.Pick) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text("Pick district") }
        SegmentedButton(
            selected = mode == LocationPickerMode.Lookup,
            onClick = { onSelectMode(LocationPickerMode.Lookup) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            enabled = zipLookupAvailable,
        ) { Text("Look up") }
    }
}

@Composable
private fun PickModeContent(
    state: LocationPickerUiState,
    onSelectDistrict: (Int) -> Unit,
) {
    when {
        state.selectedState == null -> Text(
            "Choose a state above to see its districts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.isAtLargeOrDelegate -> Text(
            "${state.selectedState} has a single representative.",
            style = MaterialTheme.typography.bodyMedium,
        )

        else -> {
            // Render the multi-district hint here too — when ZIP→Multiple
            // auto-flips us to Pick mode, the user lands on the narrowed grid
            // and needs context for why these specific districts are showing.
            val hint = state.districtHint
            if (hint is DistrictHint.Multiple) {
                Text(
                    "This ZIP spans districts ${hint.districts.joinToString(", ")}. Pick one to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text("Pick your district:", style = MaterialTheme.typography.titleSmall)
            }
            LazyVerticalGrid(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                columns = GridCells.Adaptive(minSize = 64.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.districtsForState) { d ->
                    Button(onClick = { onSelectDistrict(d) }) { Text(d.toString()) }
                }
            }
        }
    }
}

@Composable
private fun LookupModeContent(
    state: LocationPickerUiState,
    onZipChanged: (String) -> Unit,
    onLookupZip: () -> Unit,
    onOpenHouseGov: () -> Unit,
) {
    if (!state.isZipLookupAvailable) {
        Text(
            "ZIP lookup is unavailable in this build. Switch to Pick district, or use House.gov below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text("Look up by ZIP:", style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = state.zipInput,
                onValueChange = onZipChanged,
                label = { Text("ZIP code") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(onClick = onLookupZip) {
                Icon(Icons.Filled.Search, contentDescription = "Look up ZIP")
            }
        }

        when (val hint = state.districtHint) {
            DistrictHint.Miss -> Text(
                "We couldn't match that ZIP. Try another, switch to Pick district, or use House.gov below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            is DistrictHint.Single -> Text(
                "Detected ${hint.district}${state.selectedState?.let { " ($it-${hint.district})" } ?: ""}.",
                style = MaterialTheme.typography.bodyMedium,
            )
            // Multiple flips us to Pick mode in the VM, so it shouldn't surface here.
            is DistrictHint.Multiple,
            DistrictHint.SaveFailed,
            DistrictHint.None,
                -> {
            }
        }
    }

    Text(
        "or look up on House.gov",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = onOpenHouseGov,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Go to House.gov") }
}

@Composable
private fun SaveBar(
    canSave: Boolean,
    saveFailed: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (saveFailed) {
                Text(
                    "Couldn't load representatives for that location. Check your connection and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Button(onClick = onSave, enabled = canSave) { Text("Save") }
            }
        }
    }
}
