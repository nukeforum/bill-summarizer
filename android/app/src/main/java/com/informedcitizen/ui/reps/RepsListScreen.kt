package com.informedcitizen.ui.reps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.informedcitizen.ui.components.MemberCard

@Composable
fun RepsListScreen(
    onMemberClick: (String) -> Unit,
    onChangeLocation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RepsListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Your representatives", style = MaterialTheme.typography.headlineSmall)
        when (val s = state) {
            RepsListUiState.Loading -> CircularProgressIndicator()
            RepsListUiState.NoLocation ->
                Text("Set your location to see your representatives.")
            is RepsListUiState.Loaded -> {
                s.house.forEach { m ->
                    MemberCard(member = m, onClick = { onMemberClick(m.bioguideId) })
                }
                s.senators.forEach { m ->
                    MemberCard(member = m, onClick = { onMemberClick(m.bioguideId) })
                }
                if (s.house.isEmpty() && s.senators.isEmpty()) {
                    Text(
                        "We couldn't find members for your saved location. Try changing it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            is RepsListUiState.Error ->
                Text("Couldn't load: ${s.message}", color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onChangeLocation) { Text("Change my location") }
    }
}
