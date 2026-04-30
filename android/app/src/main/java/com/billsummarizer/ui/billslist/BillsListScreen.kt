package com.billsummarizer.ui.billslist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.billsummarizer.data.model.Bill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsListScreen(
    onBillClick: (Bill) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BillsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Recently Voted On") })
        },
    ) { innerPadding ->
        BillsListContent(
            state = uiState,
            innerPadding = innerPadding,
            onFilterChange = viewModel::setFilter,
            onRefresh = viewModel::refresh,
            onBillClick = onBillClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillsListContent(
    state: BillsListUiState,
    innerPadding: PaddingValues,
    onFilterChange: (BillsListFilter) -> Unit,
    onRefresh: () -> Unit,
    onBillClick: (Bill) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        FilterChipsRow(
            selected = (state as? BillsListUiState.Success)?.filter ?: BillsListFilter.ALL,
            onFilterChange = onFilterChange,
        )

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
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(items = state.bills, key = { it.id }) { bill ->
                                com.billsummarizer.ui.components.BillCard(
                                    bill = bill,
                                    onClick = { onBillClick(bill) },
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
