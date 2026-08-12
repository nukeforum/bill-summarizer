package com.informedcitizen.ui.billdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
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
 * Robolectric-hosted Compose tests for issue #85: the floating "Summarize
 * with AI" button must never occlude the tail of the scrollable bill-detail
 * content — including at the largest accessibility font scale, where the
 * button itself grows taller.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BillDetailFabClearanceTest {

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

    // Small, fixed viewport so the content is guaranteed to exceed it and
    // require scrolling — independent of Robolectric's default device size.
    private fun setContent(fontScale: Float, hasShareableBody: Boolean = true) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale),
            ) {
                Box(modifier = Modifier.width(360.dp).height(400.dp)) {
                    BillDetailScaffold(
                        uiState = BillDetailUiState.Success(
                            bill = billFixture(id = "hr30-119", summaryCrs = "<p>A short official summary.</p>")
                                .copy(votes = manyVotes(6)),
                            votesCoverage = true,
                        ),
                        repVotes = RepVotesUiState.Empty,
                        hasShareableBody = hasShareableBody,
                        onBack = {},
                        onSummarizeClick = {},
                        onOpenFullText = {},
                    )
                }
            }
        }
    }

    @Test
    fun `last content line clears the FAB at normal font scale`() {
        setContent(fontScale = 1.0f)
        assertLastLineDoesNotOccludeFab()
    }

    @Test
    fun `last content line clears the FAB at the largest accessibility font scale`() {
        setContent(fontScale = 2.0f)
        assertLastLineDoesNotOccludeFab()
    }

    @Test
    fun `no extra clearance is reserved when the FAB is absent`() {
        setContent(fontScale = 1.0f, hasShareableBody = false)

        composeRule.onNodeWithText("Summarize with AI").assertDoesNotExist()
        // The screen still renders to the end without crashing/hiding content.
        composeRule.onNodeWithText("Open full text").performScrollTo().assertExists()
    }

    private fun assertLastLineDoesNotOccludeFab() {
        // `performScrollTo()` only scrolls the minimal amount needed to bring a
        // node into the viewport — it stops as soon as the node's bounds are
        // fully visible, landing its bottom flush with the viewport edge and
        // never exercising the reserved clearance below it. Driving the
        // scrollable's own ScrollBy action past its max instead lands at the
        // true end of the scroll range, clearance included.
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1_000_000f) }

        val contentBottom = composeRule.onNodeWithText("Open full text").fetchSemanticsNode().boundsInRoot.bottom
        // ExtendedFloatingActionButton merges its descendants' semantics (it is a
        // Button under the hood), so the "Summarize with AI" text node is only
        // present in the unmerged tree.
        val fabTop = composeRule.onNodeWithText("Summarize with AI", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue(
            "expected the last content line (bottom=$contentBottom) to clear the FAB (top=$fabTop)",
            contentBottom <= fabTop,
        )
    }
}
