package com.informedcitizen.ui.billdetail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.informedcitizen.ui.billslist.billFixture
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric-hosted Compose tests for the bill detail full-title header — the
 * fix for long official titles being truncated to two lines in the top app bar
 * with no untruncated copy anywhere on the screen.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BillDetailTitleHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    // A CRA-disapproval-style title long enough to be clipped in the top bar.
    private val longTitle =
        "Providing for congressional disapproval under chapter 8 of title 5, United States Code, " +
            "of the rule submitted by the Department of Education relating to borrower defense to repayment."

    private fun setContent(title: String, shortTitle: String? = null, summaryCrs: String? = null) {
        composeRule.setContent {
            BillDetailContent(
                state = BillDetailUiState.Success(
                    bill = billFixture(id = "sjres198-119", title = title, summaryCrs = summaryCrs)
                        .copy(shortTitle = shortTitle),
                    votesCoverage = false,
                ),
                innerPadding = PaddingValues(0.dp),
                onOpenFullText = {},
                onReadFullText = {},
            )
        }
    }

    @Test
    fun `full official title renders untruncated in the body`() {
        setContent(title = longTitle)

        // The whole string must be present as a single node, not clipped.
        composeRule.onNodeWithText(longTitle).assertExists()
    }

    @Test
    fun `title is readable even when no official summary is present`() {
        setContent(title = longTitle, summaryCrs = null)

        composeRule.onNodeWithText(longTitle).assertExists()
        composeRule.onNodeWithText("No official summary available yet.").assertExists()
    }

    @Test
    fun `short title leads while the full official title still shows beneath it`() {
        setContent(title = longTitle, shortTitle = "Borrower Defense Repeal Act")

        composeRule.onNodeWithText("Borrower Defense Repeal Act").assertExists()
        composeRule.onNodeWithText(longTitle).assertExists()
    }
}
