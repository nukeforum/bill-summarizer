package com.informedcitizen.ui.billdetail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.informedcitizen.pipeline.model.Chamber
import com.informedcitizen.pipeline.model.VoteRef
import com.informedcitizen.pipeline.model.VoteTotals
import com.informedcitizen.ui.billslist.billFixture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Robolectric-hosted Compose tests for the bill detail "Votes" section. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BillDetailVotesSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(votes: List<VoteRef>, votesCoverage: Boolean) {
        composeRule.setContent {
            BillDetailContent(
                state = BillDetailUiState.Success(
                    bill = billFixture(id = "hr30-119").copy(votes = votes),
                    votesCoverage = votesCoverage,
                ),
                innerPadding = PaddingValues(0.dp),
                onOpenFullText = {},
            )
        }
    }

    @Test
    fun `house and senate roll calls render with totals and party split`() {
        // Wire order is newest-first: Senate vote first, House second.
        setContent(votes = listOf(senateVote(), houseVote()), votesCoverage = true)

        composeRule.onNodeWithText("Votes").assertExists()
        composeRule.onNodeWithText("House · Jan 16, 2025").assertExists()
        composeRule.onNodeWithText("Senate · Nov 10, 2025").assertExists()
        composeRule.onNodeWithText("Yea 274 · Nay 145").assertExists()
        composeRule.onNodeWithText("Yea 60 · Nay 40").assertExists()
        composeRule.onNodeWithText("Yea — D 61 · R 213").assertExists()
        composeRule.onNodeWithText("Nay — D 38 · I 2").assertExists()
    }

    @Test
    fun `roll calls render chronologically oldest first`() {
        setContent(votes = listOf(senateVote(), houseVote()), votesCoverage = true)

        // Semantics-tree order follows layout order in the column, so the
        // older House vote must come before the newer Senate vote.
        val headerLines = composeRule.onAllNodesWithText(" · ", substring = true)
        headerLines.assertCountEquals(2)
        headerLines[0].assert(hasText("House · Jan 16, 2025"))
        headerLines[1].assert(hasText("Senate · Nov 10, 2025"))
    }

    @Test
    fun `voice-vote bill shows explanatory line instead of cards`() {
        setContent(votes = emptyList(), votesCoverage = true)

        composeRule.onNodeWithText("Votes").assertExists()
        composeRule.onNodeWithText(VOICE_VOTE_LINE).assertExists()
    }

    @Test
    fun `votes section is hidden entirely when coverage is off`() {
        setContent(votes = listOf(houseVote()), votesCoverage = false)

        composeRule.onNodeWithText("Votes").assertDoesNotExist()
        composeRule.onNodeWithText(VOICE_VOTE_LINE).assertDoesNotExist()
        composeRule.onNodeWithText("Yea 274 · Nay 145").assertDoesNotExist()
    }

    @Test
    fun `zero present and not-voting rows are suppressed`() {
        setContent(votes = listOf(houseVote(present = 0, notVoting = 15)), votesCoverage = true)

        composeRule.onNodeWithText("Not voting 15").assertExists()
        composeRule.onNodeWithText("Present 0").assertDoesNotExist()
    }

    @Test
    fun `non-zero present row is shown`() {
        setContent(votes = listOf(houseVote(present = 2, notVoting = 0)), votesCoverage = true)

        composeRule.onNodeWithText("Present 2").assertExists()
        composeRule.onNodeWithText("Not voting 0").assertDoesNotExist()
    }

    private fun houseVote(present: Int = 0, notVoting: Int = 15) = VoteRef(
        id = "house-119-1-17",
        chamber = Chamber.HOUSE,
        session = 1,
        rollNumber = 17,
        date = "2025-01-16",
        question = "On Passage",
        result = "Passed",
        billId = "hr30-119",
        totals = VoteTotals(yea = 274, nay = 145, present = present, notVoting = notVoting),
        partySplit = mapOf(
            "yea" to mapOf("D" to 61, "R" to 213),
            "nay" to mapOf("D" to 145),
        ),
        path = "votes/congress119/house-1-17.json",
    )

    private fun senateVote() = VoteRef(
        id = "senate-119-1-618",
        chamber = Chamber.SENATE,
        session = 1,
        rollNumber = 618,
        date = "2025-11-10",
        question = "On Passage of the Bill",
        result = "Passed",
        billId = "hr30-119",
        totals = VoteTotals(yea = 60, nay = 40, present = 0, notVoting = 0),
        partySplit = mapOf(
            "yea" to mapOf("D" to 7, "I" to 1, "R" to 52),
            "nay" to mapOf("D" to 38, "I" to 2),
        ),
        path = "votes/congress119/senate-1-618.json",
    )

    private companion object {
        const val VOICE_VOTE_LINE =
            "No recorded roll call — passed by voice vote or unanimous consent."
    }
}
