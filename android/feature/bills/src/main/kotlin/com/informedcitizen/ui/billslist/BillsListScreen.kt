package com.informedcitizen.ui.billslist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.informedcitizen.data.ai.BillTopic
import com.informedcitizen.pipeline.model.Bill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsListScreen(
    onBillClick: (Bill) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCalendarClick: () -> Unit = {},
    viewModel: BillsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        // Hosted inside CongressShell.Scaffold which already consumes system bar insets.
        // Without these zero-overrides, the nested Scaffold + TopAppBar reserve status-bar
        // inset again, leaving a tall dead band above the title.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Recently Voted On") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { innerPadding ->
        BillsListContent(
            state = uiState,
            innerPadding = innerPadding,
            onFilterChange = viewModel::setFilter,
            onRefresh = viewModel::refresh,
            onBillClick = onBillClick,
            onCalendarClick = onCalendarClick,
            onTopicSelected = viewModel::selectTopic,
            onResummarize = viewModel::resummarize,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BillsListContent(
    state: BillsListUiState,
    innerPadding: PaddingValues,
    onFilterChange: (BillsListFilter) -> Unit,
    onRefresh: () -> Unit,
    onBillClick: (Bill) -> Unit,
    onCalendarClick: () -> Unit,
    onTopicSelected: (BillTopic?) -> Unit = {},
    onResummarize: (String) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
        (state as? BillsListUiState.Success)?.sessionStatusLine?.let { line ->
            SessionStatusLine(text = line, onClick = onCalendarClick)
        }
        FilterChipsRow(
            selected = (state as? BillsListUiState.Success)?.filter ?: BillsListFilter.ALL,
            onFilterChange = onFilterChange,
        )

        val success = state as? BillsListUiState.Success
        if (success != null && success.aiTitlesEnabled) {
            TopicFilterRow(
                selected = success.selectedTopic,
                onTopicSelected = onTopicSelected,
            )
            if (success.selectedTopic != null && success.hiddenByTopicCount > 0) {
                Text(
                    text = "${success.hiddenByTopicCount} bills hidden — not yet summarized",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        when (state) {
            BillsListUiState.Loading -> CenteredMessage("Loading bills…", showSpinner = true)
            is BillsListUiState.Error -> CenteredMessage("Couldn't load bills:\n${state.message}")
            is BillsListUiState.Success -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.bills.isEmpty()) {
                        CenteredMessage("No bills match this filter")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 8.dp + innerPadding.calculateBottomPadding(),
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(items = state.bills, key = { it.id }) { bill ->
                                com.informedcitizen.ui.components.BillCard(
                                    bill = bill,
                                    summary = state.summaries[bill.id],
                                    onClick = { onBillClick(bill) },
                                    onResummarize = if (state.aiTitlesEnabled && state.deviceCapable) {
                                        { onResummarize(bill.id) }
                                    } else null,
                                    aiTitlesEnabled = state.aiTitlesEnabled,
                                    deviceCapable = state.deviceCapable,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selected: BillsListFilter,
    onFilterChange: (BillsListFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BillsListFilter.entries.forEach { entry ->
            FilterChip(
                selected = entry == selected,
                onClick = { onFilterChange(entry) },
                label = { Text(entry.displayName) },
            )
        }
    }
}

@Composable
private fun TopicFilterRow(
    selected: BillTopic?,
    onTopicSelected: (BillTopic?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onTopicSelected(null) },
                label = { Text("All") },
            )
        }
        items(BillTopic.values().toList()) { topic ->
            FilterChip(
                selected = selected == topic,
                onClick = { onTopicSelected(topic) },
                label = { Text(topic.displayName) },
            )
        }
    }
}

@Composable
private fun CenteredMessage(text: String, showSpinner: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showSpinner) {
                CircularProgressIndicator()
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
