package com.informedcitizen.ui.reps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.informedcitizen.ui.util.openInCustomTab

private const val HOUSE_GOV_LOOKUP = "https://www.house.gov/representatives/find-your-representative"

private val ALL_STATES = listOf(
    "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "ID", "IL", "IN", "IA", "KS", "KY",
    "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND",
    "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
    "DC", "AS", "GU", "MP", "PR", "VI",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var stateExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Where do you live?", style = MaterialTheme.typography.headlineSmall)
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

        if (state.isAtLargeOrDelegate) {
            Text(
                "${state.selectedState} has a single representative.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (state.districtsForState.isNotEmpty()) {
            Text("Pick your district:", style = MaterialTheme.typography.titleSmall)
            LazyVerticalGrid(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
                columns = GridCells.Adaptive(minSize = 64.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.districtsForState) { d ->
                    Button(onClick = { viewModel.selectDistrict(d) }) { Text(d.toString()) }
                }
            }
        }

        if (state.isZipLookupAvailable) {
            Text("Or look up by ZIP:", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = state.zipInput,
                onValueChange = viewModel::onZipChanged,
                label = { Text("ZIP code") },
            )
            TextButton(onClick = viewModel::lookupZip) { Text("Look up") }

            when (val hint = state.districtHint) {
                DistrictHint.Miss -> Text(
                    "We couldn't match that ZIP. Pick your district from the list, or use House.gov.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                is DistrictHint.Multiple -> Text(
                    "This ZIP spans districts ${hint.districts.joinToString(", ")}. Please pick one above.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                is DistrictHint.Single -> Text(
                    "Detected district ${hint.district}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                DistrictHint.None -> {}
            }
        } else {
            Text(
                "ZIP lookup is unavailable in this build. Pick your district above, or use House.gov.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        TextButton(onClick = { openInCustomTab(context, HOUSE_GOV_LOOKUP) }) {
            Text("Look up on House.gov")
        }

        Button(
            onClick = {
                viewModel.save()
                onSaved()
            },
            enabled = state.canSave,
        ) { Text("Save") }
    }
}
