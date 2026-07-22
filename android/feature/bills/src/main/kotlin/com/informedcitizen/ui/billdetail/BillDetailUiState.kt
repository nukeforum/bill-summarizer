package com.informedcitizen.ui.billdetail

import com.informedcitizen.data.repository.RepPosition
import com.informedcitizen.pipeline.model.Bill

/**
 * The saved reps' recorded positions on the bill on screen, keyed by
 * roll-call vote id so each [RollCallCard] picks up exactly the reps
 * who voted in it. Loaded independently of the bill itself: totals
 * render immediately and rep rows attach when the member shards
 * arrive. [fetchFailed] drives the quiet partial-data line — totals
 * stay honest even when the shards couldn't be loaded.
 */
data class RepVotesUiState(
    val positionsByVoteId: Map<String, List<RepPosition>>,
    val fetchFailed: Boolean,
) {
    companion object {
        val Empty = RepVotesUiState(emptyMap(), fetchFailed = false)
    }
}

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
