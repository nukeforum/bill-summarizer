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
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.informedcitizen.data.ai.BillTopic
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.ui.components.BillSearchField
import java.time.Instant

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
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val pagedBills = viewModel.pagedBills.collectAsLazyPagingItems()

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
            bills = pagedBills,
            searchQuery = searchQuery,
            innerPadding = innerPadding,
            onFilterChange = viewModel::setFilter,
            onRefresh = viewModel::refresh,
            onBillClick = onBillClick,
            onCalendarClick = onCalendarClick,
            onTopicSelected = viewModel::selectTopic,
            onResummarize = viewModel::resummarize,
            onSearchQueryChange = viewModel::setSearchQuery,
            onPolicyAreaSelected = viewModel::selectPolicyArea,
            onSubjectSelected = viewModel::selectSubject,
            onStatusSelected = viewModel::setStatusFilter,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BillsListContent(
    state: BillsListUiState,
    bills: LazyPagingItems<Bill>,
    innerPadding: PaddingValues,
    onFilterChange: (BillsListFilter) -> Unit,
    onRefresh: () -> Unit,
    onBillClick: (Bill) -> Unit,
    onCalendarClick: () -> Unit,
    onTopicSelected: (BillTopic?) -> Unit = {},
    onResummarize: (String) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onPolicyAreaSelected: (String?) -> Unit = {},
    onSubjectSelected: (String?) -> Unit = {},
    onStatusSelected: (BillStatusFilter) -> Unit = {},
    searchQuery: String = (state as? BillsListUiState.Success)?.searchQuery.orEmpty(),
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
        (state as? BillsListUiState.Success)?.sessionStatusLine?.let { line ->
            SessionStatusLine(text = line, onClick = onCalendarClick)
        }
        (state as? BillsListUiState.Success)?.dataGeneratedAt?.let { generatedAt ->
            DataFreshnessLine(generatedAt = generatedAt, now = Instant.now())
        }
        if (state is BillsListUiState.Success) {
            BillSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        FilterChipsRow(
            selected = (state as? BillsListUiState.Success)?.filter ?: BillsListFilter.ALL,
            onFilterChange = onFilterChange,
        )

        val success = state as? BillsListUiState.Success
        if (success != null && success.availablePolicyAreas.isNotEmpty()) {
            PolicyAreaFilterRow(
                policyAreas = success.availablePolicyAreas,
                selected = success.selectedPolicyArea,
                onPolicyAreaSelected = onPolicyAreaSelected,
            )
        }
        if (success != null && success.availableSubjects.isNotEmpty()) {
            SubjectFilterRow(
                subjects = success.availableSubjects,
                selected = success.selectedSubject,
                onSubjectSelected = onSubjectSelected,
            )
        }
        if (success != null && success.statusFilterAvailable) {
            StatusFilterRow(
                selected = success.selectedStatus,
                onStatusSelected = onStatusSelected,
            )
        }
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
                    // Refresh both channels: the paged stream repopulates the list
                    // body while viewModel.refresh() re-pulls the session line and
                    // the in-memory chrome path (policy areas, freshness).
                    onRefresh = {
                        bills.refresh()
                        onRefresh()
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val refresh = bills.loadState.refresh
                    when {
                        bills.itemCount == 0 && refresh is LoadState.Loading ->
                            CenteredMessage("Loading bills…", showSpinner = true)
                        bills.itemCount == 0 && refresh is LoadState.Error ->
                            CenteredMessage(
                                "Couldn't load bills:\n${refresh.error.localizedMessage.orEmpty()}",
                            )
                        bills.itemCount == 0 ->
                            CenteredMessage(
                                when {
                                    state.searchQuery.isNotBlank() ->
                                        "No bills match \"${state.searchQuery.trim()}\""
                                    state.selectedPolicyArea != null ->
                                        "No bills with subject \"${state.selectedPolicyArea}\" match this filter"
                                    state.selectedSubject != null ->
                                        "No bills about \"${state.selectedSubject}\" match this filter"
                                    else -> "No bills match this filter"
                                },
                            )
                        else -> BillsPagedList(
                            bills = bills,
                            summaries = state.summaries,
                            aiTitlesEnabled = state.aiTitlesEnabled,
                            deviceCapable = state.deviceCapable,
                            onBillClick = onBillClick,
                            onResummarize = onResummarize,
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 8.dp + innerPadding.calculateBottomPadding(),
                            ),
                            modifier = Modifier.fillMaxSize(),
                        )
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

/**
 * Chips over the Congress.gov policy-area taxonomy of the loaded bills —
 * topical narrowing without relying on keyword matches. Only rendered when
 * the feed carries subject data, so older manifests degrade gracefully.
 */
@Composable
private fun PolicyAreaFilterRow(
    policyAreas: List<String>,
    selected: String?,
    onPolicyAreaSelected: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onPolicyAreaSelected(null) },
                label = { Text("All subjects") },
            )
        }
        items(items = policyAreas, key = { it }) { area ->
            FilterChip(
                selected = selected == area,
                onClick = { onPolicyAreaSelected(if (selected == area) null else area) },
                label = { Text(area) },
            )
        }
    }
}

/**
 * Chips over the finer-grained legislative subjects (#10/#28) present across the
 * loaded bills — first-class topical narrowing over the multi-value `subjects`
 * tags, distinct from the single coarse [PolicyAreaFilterRow]. Only rendered
 * once the feed carries subject terms (see
 * [BillsListUiState.Success.availableSubjects]), so older manifests degrade
 * gracefully to no row rather than an empty one.
 */
@Composable
private fun SubjectFilterRow(
    subjects: List<String>,
    selected: String?,
    onSubjectSelected: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSubjectSelected(null) },
                label = { Text("All topics") },
            )
        }
        items(items = subjects, key = { it }) { subject ->
            FilterChip(
                selected = selected == subject,
                onClick = { onSubjectSelected(if (selected == subject) null else subject) },
                label = { Text(subject) },
            )
        }
    }
}

/**
 * Chips over the #39 pre-floor lifecycle statuses (introduced / in committee /
 * reported) — a distinct axis from the outcome chips ([FilterChipsRow]), which
 * slice terminal floor outcomes. Only rendered once the broadened bill set
 * carries lifecycle statuses (see [BillsListUiState.Success.statusFilterAvailable]),
 * so it stays invisible on today's floor-outcome-only feed.
 */
@Composable
private fun StatusFilterRow(
    selected: BillStatusFilter,
    onStatusSelected: (BillStatusFilter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = BillStatusFilter.entries, key = { it.name }) { entry ->
            FilterChip(
                selected = entry == selected,
                onClick = { onStatusSelected(entry) },
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
