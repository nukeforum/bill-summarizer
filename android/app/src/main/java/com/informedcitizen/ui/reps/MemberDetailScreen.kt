package com.informedcitizen.ui.reps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.informedcitizen.data.model.MemberLegislationItem
import com.informedcitizen.data.util.congressGovUrlFor
import com.informedcitizen.ui.components.MemberCard
import com.informedcitizen.ui.components.MemberLegislationRow
import com.informedcitizen.ui.util.openInCustomTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    bioguideId: String,
    onBack: () -> Unit,
    onBillClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemberDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(bioguideId) { viewModel.load(bioguideId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.member?.name ?: "Member") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        MemberDetailContent(
            state = state,
            innerPadding = padding,
            onLegislationClick = { item ->
                if (viewModel.isInLocalCache(item.id)) {
                    onBillClick(item.id)
                } else {
                    val url = congressGovUrlFor(item.type, item.number, item.congress)
                    openInCustomTab(context, url)
                }
            },
        )
    }
}

@Composable
internal fun MemberDetailContent(
    state: MemberDetailUiState,
    innerPadding: PaddingValues,
    onLegislationClick: (MemberLegislationItem) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator()
            state.member != null -> {
                MemberCard(member = state.member, onClick = {})
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text("Sponsored (${state.sponsored.size})") },
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text("Cosponsored (${state.cosponsored.size})") },
                    )
                }
                val items = if (tab == 0) state.sponsored else state.cosponsored
                if (items.isEmpty()) {
                    Text(
                        "No data yet for this representative.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn {
                        items(items, key = { it.id }) { item ->
                            MemberLegislationRow(item = item, onClick = { onLegislationClick(item) })
                        }
                    }
                }
            }
            state.errorMessage != null -> Text("Error: ${state.errorMessage}")
        }
    }
}
