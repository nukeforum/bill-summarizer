package com.informedcitizen.ui.billdetail

import com.informedcitizen.data.model.Bill

sealed interface BillDetailUiState {
    data object Loading : BillDetailUiState
    data class Success(val bill: Bill) : BillDetailUiState
    data class Error(val message: String) : BillDetailUiState
}
