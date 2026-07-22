package com.informedcitizen.ui.billdetail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.informedcitizen.pipeline.model.Chamber
import com.informedcitizen.pipeline.model.VoteRef
import com.informedcitizen.pipeline.model.VoteTotals
import com.informedcitizen.ui.preview.PreviewWrap
import com.informedcitizen.ui.preview.sampleBill

// Wire order is newest-first; the screen renders them oldest-first.
private val sampleVotes = listOf(
    VoteRef(
        id = "senate-119-2-618",
        chamber = Chamber.SENATE,
        session = 2,
        rollNumber = 618,
        date = "2026-07-10",
        question = "On Passage of the Bill",
        result = "Passed",
        billId = "hr1-119",
        totals = VoteTotals(yea = 60, nay = 40, present = 0, notVoting = 0),
        partySplit = mapOf(
            "yea" to mapOf("D" to 7, "I" to 1, "R" to 52),
            "nay" to mapOf("D" to 38, "I" to 2),
        ),
        path = "votes/congress119/senate-2-618.json",
    ),
    VoteRef(
        id = "house-119-2-317",
        chamber = Chamber.HOUSE,
        session = 2,
        rollNumber = 317,
        date = "2026-07-03",
        question = "On Passage",
        result = "Passed",
        billId = "hr1-119",
        totals = VoteTotals(yea = 218, nay = 214, present = 0, notVoting = 3),
        partySplit = mapOf(
            "yea" to mapOf("D" to 210, "I" to 2, "R" to 6),
            "nay" to mapOf("D" to 3, "I" to 2, "R" to 209),
        ),
        path = "votes/congress119/house-2-317.json",
    ),
)

@Preview(showBackground = true)
@Composable
private fun PreviewBillDetailLoading() = PreviewWrap {
    BillDetailContent(
        state = BillDetailUiState.Loading,
        innerPadding = PaddingValues(0.dp),
        onOpenFullText = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewBillDetailSuccess() = PreviewWrap {
    BillDetailContent(
        state = BillDetailUiState.Success(
            bill = sampleBill().copy(votes = sampleVotes),
            votesCoverage = true,
        ),
        innerPadding = PaddingValues(0.dp),
        onOpenFullText = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewBillDetailVoiceVote() = PreviewWrap {
    BillDetailContent(
        state = BillDetailUiState.Success(
            bill = sampleBill(),
            votesCoverage = true,
        ),
        innerPadding = PaddingValues(0.dp),
        onOpenFullText = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewBillDetailSuccessNoSummary() = PreviewWrap {
    BillDetailContent(
        state = BillDetailUiState.Success(
            bill = sampleBill(summaryCrs = null),
            votesCoverage = false,
        ),
        innerPadding = PaddingValues(0.dp),
        onOpenFullText = {},
    )
}

@PreviewLightDark
@Composable
private fun PreviewBillDetailError() = PreviewWrap {
    BillDetailContent(
        state = BillDetailUiState.Error(message = "Bill not found in cache."),
        innerPadding = PaddingValues(0.dp),
        onOpenFullText = {},
    )
}
