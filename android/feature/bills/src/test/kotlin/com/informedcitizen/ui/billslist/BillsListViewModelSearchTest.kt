package com.informedcitizen.ui.billslist

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.testutil.FakeBillCache
import com.informedcitizen.testutil.FakeSessionCalendarCache
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.SessionCalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BillsListViewModelSearchTest {

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `search query filters bills by title`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a", title = "Education Funding Act"),
                billFixture("b", title = "Wildfire Response Resolution"),
            ),
        )
        vm.setSearchQuery("education")
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(listOf("a"), state.bills.map { it.id })
        assertEquals("education", state.searchQuery)
    }

    @Test fun `search matches committee referral in latest action`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a", title = "Gold Medal Award"),
                billFixture(
                    "b",
                    title = "A bill to amend title 20",
                    actionText = "Referred to the Committee on Education and the Workforce.",
                ),
            ),
        )
        vm.setSearchQuery("education")
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(listOf("b"), state.bills.map { it.id })
    }

    @Test fun `clearing the query restores the full list`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a", title = "Education Funding Act"),
                billFixture("b", title = "Wildfire Response Resolution"),
            ),
        )
        vm.setSearchQuery("education")
        vm.setSearchQuery("")
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(listOf("a", "b"), state.bills.map { it.id })
        assertEquals("", state.searchQuery)
    }

    @Test fun `search composes with the outcome filter`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a", title = "Education Funding Act", outcome = Outcome.PASSED_HOUSE),
                billFixture("b", title = "Education Savings Act", outcome = Outcome.FAILED),
                billFixture("c", title = "Wildfire Response Resolution", outcome = Outcome.FAILED),
            ),
        )
        vm.setFilter(BillsListFilter.FAILED)
        vm.setSearchQuery("education")
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(listOf("b"), state.bills.map { it.id })
    }

    @Test fun `no matches yields empty list with query preserved`() = runTest {
        val vm = makeVm(listOf(billFixture("a", title = "Gold Medal Award")))
        vm.setSearchQuery("education")
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(emptyList<Bill>(), state.bills)
        assertEquals("education", state.searchQuery)
    }

    private fun makeVm(bills: List<Bill>): BillsListViewModel {
        val billRepo = BillRepository(
            api = StubBillsApi(BillsManifest(generatedAt = "x", congress = 119, bills = bills)),
            dataStore = StubPreferencesDataStore(),
            crashReporter = FakeCrashReporter(),
            billCache = FakeBillCache(),
        )
        val sessionRepo = SessionCalendarRepository(
            api = StubBillsApi(BillsManifest(generatedAt = "x", congress = 119, bills = emptyList())),
            crashReporter = FakeCrashReporter(),
            persistentCache = FakeSessionCalendarCache(),
        )
        kotlinx.coroutines.runBlocking {
            billRepo.getBills(forceRefresh = true)
        }
        return BillsListViewModel(
            billRepository = billRepo,
            sessionCalendarRepository = sessionRepo,
            cache = StubCache(),
            aiPrefs = FakeAiTitlesPrefs(),
            aiCapability = FakeAiCapability(),
            controller = NoOpController,
        )
    }
}
