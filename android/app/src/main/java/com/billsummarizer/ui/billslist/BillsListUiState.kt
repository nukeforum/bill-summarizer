package com.billsummarizer.ui.billslist

import com.billsummarizer.data.model.Bill

sealed interface BillsListUiState {
    data object Loading : BillsListUiState

    data class Success(
        val bills: List<Bill>,
        val filter: BillsListFilter,
        val isRefreshing: Boolean,
    ) : BillsListUiState

    data class Error(val message: String) : BillsListUiState
}
