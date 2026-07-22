package com.informedcitizen.ui.billdetail

import com.informedcitizen.pipeline.model.Bill

sealed interface BillDetailUiState {
    data object Loading : BillDetailUiState

    /**
     * [votesCoverage] gates the Votes section: with lenient parsing an
     * empty [Bill.votes] is ambiguous ("no roll call" vs "votes not
     * published yet"), so the section only renders once the manifest
     * says the votes pipeline covers this Congress.
     */
    data class Success(val bill: Bill, val votesCoverage: Boolean) : BillDetailUiState

    data class Error(val message: String) : BillDetailUiState
}
