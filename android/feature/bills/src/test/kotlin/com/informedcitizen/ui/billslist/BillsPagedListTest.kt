package com.informedcitizen.ui.billslist

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.informedcitizen.pipeline.model.Bill
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric-hosted Compose tests for the #41 paged bills list body — that
 * items render and that the append load state drives the footer (spinner while
 * a shard page loads, retry affordance on fetch failure so a partial list is
 * never presented as complete).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BillsPagedListTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun loadStates(append: LoadState) = LoadStates(
        refresh = LoadState.NotLoading(endOfPaginationReached = false),
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = append,
    )

    @Composable
    private fun Subject(data: PagingData<Bill>) {
        val items = flowOf(data).collectAsLazyPagingItems()
        MaterialTheme {
            BillsPagedList(
                bills = items,
                summaries = emptyMap(),
                aiTitlesEnabled = false,
                deviceCapable = false,
                onBillClick = {},
                onResummarize = {},
                contentPadding = PaddingValues(0.dp),
            )
        }
    }

    @Test
    fun `renders each paged bill`() {
        val data = PagingData.from(
            listOf(billFixture("hr-1", title = "First bill"), billFixture("hr-2", title = "Second bill")),
            sourceLoadStates = loadStates(LoadState.NotLoading(endOfPaginationReached = true)),
        )
        composeRule.setContent { Subject(data) }

        composeRule.onNodeWithText("First bill").assertIsDisplayed()
        composeRule.onNodeWithText("Second bill").assertIsDisplayed()
    }

    @Test
    fun `shows a loading footer while the next shard page loads`() {
        val data = PagingData.from(
            listOf(billFixture("hr-1", title = "First bill")),
            sourceLoadStates = loadStates(LoadState.Loading),
        )
        composeRule.setContent { Subject(data) }

        composeRule.onNodeWithContentDescription("Loading more bills").assertIsDisplayed()
    }

    @Test
    fun `shows a retry footer when a shard fetch fails`() {
        val data = PagingData.from(
            listOf(billFixture("hr-1", title = "First bill")),
            sourceLoadStates = loadStates(LoadState.Error(IllegalStateException("boom"))),
        )
        composeRule.setContent { Subject(data) }

        composeRule.onNodeWithText("Couldn't load more").assertIsDisplayed()
        // The retry affordance is present and clickable (retry() is a no-op on a
        // static PagingData, but the wiring must exist on a real fetch failure).
        composeRule.onNodeWithText("Retry").assertIsDisplayed().performClick()
    }
}
