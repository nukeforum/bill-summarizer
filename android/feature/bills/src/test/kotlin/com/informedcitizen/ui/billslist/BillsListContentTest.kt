package com.informedcitizen.ui.billslist

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
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
 * Robolectric-hosted Compose tests for the #41 wiring of [BillsPagedList] into
 * [BillsListContent]: the paged body renders under the uiState chrome, and the
 * empty/loading/error branches key off the paging refresh load state rather than
 * the in-memory `state.bills` list.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BillsListContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun loadStates(refresh: LoadState) = LoadStates(
        refresh = refresh,
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = LoadState.NotLoading(endOfPaginationReached = true),
    )

    @Composable
    private fun Subject(state: BillsListUiState, data: PagingData<Bill>) {
        val items = flowOf(data).collectAsLazyPagingItems()
        MaterialTheme {
            BillsListContent(
                state = state,
                bills = items,
                innerPadding = PaddingValues(0.dp),
                onFilterChange = {},
                onRefresh = {},
                onBillClick = {},
                onCalendarClick = {},
            )
        }
    }

    private fun success(
        searchQuery: String = "",
        selectedPolicyArea: String? = null,
    ) = BillsListUiState.Success(
        bills = emptyList(),
        filter = BillsListFilter.ALL,
        isRefreshing = false,
        sessionStatusLine = "House in session today.",
        searchQuery = searchQuery,
        selectedPolicyArea = selectedPolicyArea,
    )

    @Test
    fun `renders the paged body under the chrome`() {
        val data = PagingData.from(
            listOf(billFixture("hr-1", title = "First paged bill")),
            sourceLoadStates = loadStates(LoadState.NotLoading(endOfPaginationReached = true)),
        )
        composeRule.setContent { Subject(success(), data) }

        composeRule.onNodeWithText("House in session today.").assertIsDisplayed()
        composeRule.onNodeWithText("First paged bill").assertIsDisplayed()
    }

    @Test
    fun `shows the initial refresh spinner when the paged list is empty and loading`() {
        val data = PagingData.from(emptyList<Bill>(), sourceLoadStates = loadStates(LoadState.Loading))
        composeRule.setContent { Subject(success(), data) }

        composeRule.onNodeWithText("Loading bills…").assertIsDisplayed()
    }

    @Test
    fun `shows a search-specific empty message keyed off the paged item count`() {
        val data = PagingData.from(
            emptyList<Bill>(),
            sourceLoadStates = loadStates(LoadState.NotLoading(endOfPaginationReached = true)),
        )
        composeRule.setContent { Subject(success(searchQuery = "climate"), data) }

        composeRule.onNodeWithText("No bills match \"climate\"").assertIsDisplayed()
    }
}
