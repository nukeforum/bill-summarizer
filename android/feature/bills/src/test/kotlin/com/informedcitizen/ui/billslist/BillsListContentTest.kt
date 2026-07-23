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
        statusFilterAvailable: Boolean = false,
        availableSubjects: List<String> = emptyList(),
        selectedSubject: String? = null,
    ) = BillsListUiState.Success(
        bills = emptyList(),
        filter = BillsListFilter.ALL,
        isRefreshing = false,
        sessionStatusLine = "House in session today.",
        searchQuery = searchQuery,
        selectedPolicyArea = selectedPolicyArea,
        statusFilterAvailable = statusFilterAvailable,
        availableSubjects = availableSubjects,
        selectedSubject = selectedSubject,
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
    fun `renders the lifecycle-status filter row only when it is available`() {
        val data = PagingData.from(
            listOf(billFixture("hr-1", title = "First paged bill")),
            sourceLoadStates = loadStates(LoadState.NotLoading(endOfPaginationReached = true)),
        )
        composeRule.setContent { Subject(success(statusFilterAvailable = true), data) }

        composeRule.onNodeWithText("Introduced").assertIsDisplayed()
        composeRule.onNodeWithText("In committee").assertIsDisplayed()
        composeRule.onNodeWithText("Reported").assertIsDisplayed()
    }

    @Test
    fun `hides the lifecycle-status filter row when unavailable`() {
        val data = PagingData.from(
            listOf(billFixture("hr-1", title = "First paged bill")),
            sourceLoadStates = loadStates(LoadState.NotLoading(endOfPaginationReached = true)),
        )
        composeRule.setContent { Subject(success(statusFilterAvailable = false), data) }

        composeRule.onNodeWithText("In committee").assertDoesNotExist()
    }

    @Test
    fun `renders the legislative-subject filter row only when subjects are present`() {
        val data = PagingData.from(
            listOf(billFixture("hr-1", title = "First paged bill")),
            sourceLoadStates = loadStates(LoadState.NotLoading(endOfPaginationReached = true)),
        )
        composeRule.setContent {
            Subject(success(availableSubjects = listOf("Immigration", "Voting rights")), data)
        }

        composeRule.onNodeWithText("All topics").assertIsDisplayed()
        composeRule.onNodeWithText("Immigration").assertIsDisplayed()
        composeRule.onNodeWithText("Voting rights").assertIsDisplayed()
    }

    @Test
    fun `hides the legislative-subject filter row when no subjects are present`() {
        val data = PagingData.from(
            listOf(billFixture("hr-1", title = "First paged bill")),
            sourceLoadStates = loadStates(LoadState.NotLoading(endOfPaginationReached = true)),
        )
        composeRule.setContent { Subject(success(availableSubjects = emptyList()), data) }

        composeRule.onNodeWithText("All topics").assertDoesNotExist()
    }

    @Test
    fun `shows a subject-specific empty message keyed off the paged item count`() {
        val data = PagingData.from(
            emptyList<Bill>(),
            sourceLoadStates = loadStates(LoadState.NotLoading(endOfPaginationReached = true)),
        )
        composeRule.setContent {
            Subject(
                success(availableSubjects = listOf("Immigration"), selectedSubject = "Immigration"),
                data,
            )
        }

        composeRule.onNodeWithText("No bills about \"Immigration\" match this filter").assertIsDisplayed()
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
