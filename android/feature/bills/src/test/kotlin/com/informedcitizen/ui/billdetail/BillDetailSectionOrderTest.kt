package com.informedcitizen.ui.billdetail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.informedcitizen.pipeline.model.Chamber
import com.informedcitizen.pipeline.model.VoteRef
import com.informedcitizen.pipeline.model.VoteTotals
import com.informedcitizen.ui.billslist.billFixture
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric-hosted Compose tests for issue #102: on a bill with many
 * roll-call votes, the plain-language/official summary must be reachable
 * without scrolling past the whole vote list. The fix leads with the
 * "Official summary" section, ahead of "Votes".
 *
 * Section order is asserted via semantics-tree traversal order rather than
 * [androidx.compose.ui.semantics.SemanticsNode.boundsInRoot]: content below
 * the fold of Robolectric's (small, fixed) default viewport is clipped by
 * `verticalScroll`'s own clip, which zeroes out its reported bounds even
 * though it is a real, reachable-by-scrolling node — tree order is a
 * layout-order proxy that isn't affected by that clipping.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BillDetailSectionOrderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun manyVotes(count: Int): List<VoteRef> = (1..count).map { i ->
        VoteRef(
            id = "house-119-1-$i",
            chamber = Chamber.HOUSE,
            session = 1,
            rollNumber = i,
            date = "2025-01-${(i % 28) + 1}",
            question = "On Passage",
            result = "Passed",
            billId = "hr30-119",
            totals = VoteTotals(yea = 200 + i, nay = 100, present = 0, notVoting = 0),
            partySplit = emptyMap(),
            path = "votes/congress119/house-1-$i.json",
        )
    }

    private fun setContent(voteCount: Int, summaryCrs: String? = "<p>A short official summary.</p>") {
        composeRule.setContent {
            BillDetailContent(
                state = BillDetailUiState.Success(
                    bill = billFixture(id = "hr30-119", summaryCrs = summaryCrs).copy(votes = manyVotes(voteCount)),
                    votesCoverage = true,
                ),
                innerPadding = PaddingValues(0.dp),
                onOpenFullText = {},
            )
        }
    }

    /** Index of the first node in the tree whose merged text exactly equals [heading]. */
    private fun headingIndex(heading: String): Int {
        var index = -1
        var cursor = 0
        fun walk(node: SemanticsNode) {
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            if (text == heading && index == -1) index = cursor
            cursor++
            node.children.forEach(::walk)
        }
        walk(composeRule.onRoot().fetchSemanticsNode())
        check(index != -1) { "no node found with exact text \"$heading\"" }
        return index
    }

    @Test
    fun `official summary section renders above the votes section on a vote-heavy bill`() {
        setContent(voteCount = 12)

        val summaryIndex = headingIndex("Official summary")
        val votesIndex = headingIndex("Votes")

        assertTrue(
            "expected the Official summary heading (index=$summaryIndex) to render above Votes (index=$votesIndex)",
            summaryIndex < votesIndex,
        )
    }

    @Test
    fun `official summary section renders above sponsor and full text regardless of vote count`() {
        setContent(voteCount = 20)

        val summaryIndex = headingIndex("Official summary")
        val sponsorIndex = headingIndex("Sponsor")
        val fullTextIndex = headingIndex("Full text")

        assertTrue(
            "expected Official summary (index=$summaryIndex) above Sponsor (index=$sponsorIndex)",
            summaryIndex < sponsorIndex,
        )
        assertTrue(
            "expected Official summary (index=$summaryIndex) above Full text (index=$fullTextIndex)",
            summaryIndex < fullTextIndex,
        )
    }
}
