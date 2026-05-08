package com.informedcitizen.ui.billslist

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.informedcitizen.data.ai.BillTopic
import com.informedcitizen.ui.components.BillCardSummary
import com.informedcitizen.ui.preview.PreviewWrap
import com.informedcitizen.ui.preview.sampleBills

@Preview(showBackground = true)
@Composable
private fun PreviewBillsListLoading() = PreviewWrap {
    BillsListContent(
        state = BillsListUiState.Loading,
        innerPadding = PaddingValues(0.dp),
        onFilterChange = {},
        onRefresh = {},
        onBillClick = {},
        onCalendarClick = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewBillsListSuccess() = PreviewWrap {
    BillsListContent(
        state = BillsListUiState.Success(
            bills = sampleBills,
            filter = BillsListFilter.ALL,
            isRefreshing = false,
            sessionStatusLine = "House in session today; Senate on recess until May 12.",
        ),
        innerPadding = PaddingValues(0.dp),
        onFilterChange = {},
        onRefresh = {},
        onBillClick = {},
        onCalendarClick = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewBillsListSuccessEmpty() = PreviewWrap {
    BillsListContent(
        state = BillsListUiState.Success(
            bills = emptyList(),
            filter = BillsListFilter.ENACTED,
            isRefreshing = false,
            sessionStatusLine = null,
        ),
        innerPadding = PaddingValues(0.dp),
        onFilterChange = {},
        onRefresh = {},
        onBillClick = {},
        onCalendarClick = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewBillsListError() = PreviewWrap {
    BillsListContent(
        state = BillsListUiState.Error(message = "Network unreachable. Check your connection and try again."),
        innerPadding = PaddingValues(0.dp),
        onFilterChange = {},
        onRefresh = {},
        onBillClick = {},
        onCalendarClick = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewBillsListAiOnNoSummaries() = PreviewWrap {
    BillsListContent(
        state = BillsListUiState.Success(
            bills = sampleBills,
            filter = BillsListFilter.ALL,
            isRefreshing = false,
            sessionStatusLine = null,
            aiTitlesEnabled = true,
            deviceCapable = true,
            summaries = emptyMap(),
        ),
        innerPadding = PaddingValues(0.dp),
        onFilterChange = {},
        onRefresh = {},
        onBillClick = {},
        onCalendarClick = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewBillsListAiOnMixedSummaries() = PreviewWrap {
    val summaries = sampleBills.take(3).mapIndexed { i, bill ->
        bill.id to BillCardSummary(
            generatedTitle = "Concise: ${bill.title.take(30)}",
            topic = listOf(BillTopic.Tech, BillTopic.Healthcare, BillTopic.Defense)[i],
        )
    }.toMap()
    BillsListContent(
        state = BillsListUiState.Success(
            bills = sampleBills,
            filter = BillsListFilter.ALL,
            isRefreshing = false,
            sessionStatusLine = null,
            aiTitlesEnabled = true,
            deviceCapable = true,
            summaries = summaries,
        ),
        innerPadding = PaddingValues(0.dp),
        onFilterChange = {},
        onRefresh = {},
        onBillClick = {},
        onCalendarClick = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewBillsListAiOnTopicFilter() = PreviewWrap {
    val summaries = mapOf(
        sampleBills[0].id to BillCardSummary("Concise tech bill", BillTopic.Tech),
    )
    BillsListContent(
        state = BillsListUiState.Success(
            bills = listOf(sampleBills[0]),
            filter = BillsListFilter.ALL,
            isRefreshing = false,
            sessionStatusLine = null,
            aiTitlesEnabled = true,
            deviceCapable = true,
            summaries = summaries,
            selectedTopic = BillTopic.Tech,
            hiddenByTopicCount = 4,
        ),
        innerPadding = PaddingValues(0.dp),
        onFilterChange = {},
        onRefresh = {},
        onBillClick = {},
        onCalendarClick = {},
    )
}
