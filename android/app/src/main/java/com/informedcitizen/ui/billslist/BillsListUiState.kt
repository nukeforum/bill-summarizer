package com.informedcitizen.ui.billslist

import com.informedcitizen.data.model.Bill

sealed interface BillsListUiState {
    data object Loading : BillsListUiState

    data class Success(
        val bills: List<Bill>,
        val filter: BillsListFilter,
        val isRefreshing: Boolean,
        val sessionStatusLine: String? = null,
    ) : BillsListUiState

    data class Error(val message: String) : BillsListUiState
}
