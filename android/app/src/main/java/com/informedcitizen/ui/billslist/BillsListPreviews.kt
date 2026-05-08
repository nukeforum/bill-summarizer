package com.informedcitizen.ui.billslist

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.informedcitizen.ui.preview.MaterialPreviewTheme
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

@Composable
private fun PreviewWrap(content: @Composable () -> Unit) {
    MaterialPreviewTheme {
        Surface(modifier = Modifier.fillMaxSize()) { content() }
    }
}
