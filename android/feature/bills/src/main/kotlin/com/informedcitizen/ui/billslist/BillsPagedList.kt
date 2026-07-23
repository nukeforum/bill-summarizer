package com.informedcitizen.ui.billslist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.ui.components.BillCard
import com.informedcitizen.ui.components.BillCardSummary

/**
 * The sharded bills-list body (issue #41, epic #38): renders a page-at-a-time
 * [LazyPagingItems] of [Bill] so the list loads lazily over the #40 shard
 * manifests instead of holding every bill in memory.
 *
 * The append load state is surfaced as a footer so a partial list is never
 * silently presented as complete — a spinner while the next shard page loads,
 * and a "Couldn't load more · Retry" affordance when a shard fetch fails (the
 * mediator returns [androidx.paging.RemoteMediator.MediatorResult.Error]). This
 * is the UI counterpart to [BillsListViewModel.pagedBills]; the surrounding
 * chrome (session line, freshness, filter chips) stays on the non-paged
 * `uiState` path.
 */
@Composable
internal fun BillsPagedList(
    bills: LazyPagingItems<Bill>,
    summaries: Map<String, BillCardSummary>,
    aiTitlesEnabled: Boolean,
    deviceCapable: Boolean,
    onBillClick: (Bill) -> Unit,
    onResummarize: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(count = bills.itemCount, key = bills.itemKey { it.id }) { index ->
            // A placeholder slot is null; this list disables placeholders, so a
            // null only occurs transiently between an invalidation and reload.
            val bill = bills[index] ?: return@items
            BillCard(
                bill = bill,
                summary = summaries[bill.id],
                onClick = { onBillClick(bill) },
                onResummarize = if (aiTitlesEnabled && deviceCapable) {
                    { onResummarize(bill.id) }
                } else {
                    null
                },
                aiTitlesEnabled = aiTitlesEnabled,
                deviceCapable = deviceCapable,
            )
        }
        when (bills.loadState.append) {
            is LoadState.Loading -> item(key = "append-loading") { AppendLoadingFooter() }
            is LoadState.Error -> item(key = "append-error") {
                AppendErrorFooter(onRetry = bills::retry)
            }
            is LoadState.NotLoading -> Unit
        }
    }
}

@Composable
private fun AppendLoadingFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics { contentDescription = "Loading more bills" },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AppendErrorFooter(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Couldn't load more",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.CenterEnd)) {
            Text("Retry")
        }
    }
}
