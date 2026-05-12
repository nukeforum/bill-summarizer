package com.informedcitizen.ui.reps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RepsTab(
    onMemberClick: (String) -> Unit,
    onSettingsClick: () -> Unit,  // accepted for parity, currently unused (Settings is reachable from Bills tab)
    modifier: Modifier = Modifier,
    viewModel: RepsListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    val effectivePicker = showPicker || state == RepsListUiState.NoLocation
    if (effectivePicker) {
        LocationPickerScreen(
            onSaved = { showPicker = false },
            modifier = modifier,
        )
    } else {
        RepsListScreen(
            onMemberClick = onMemberClick,
            onChangeLocation = { showPicker = true },
            modifier = modifier,
        )
    }
}
