package com.billsummarizer.ui.billdetail

import com.billsummarizer.data.model.Bill

sealed interface BillDetailUiState {
    data object Loading : BillDetailUiState
    data class Success(val bill: Bill) : BillDetailUiState
    data class Error(val message: String) : BillDetailUiState
}
