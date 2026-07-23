package com.informedcitizen.ui.billslist

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.testutil.FakeBillCache
import com.informedcitizen.testutil.FakeSessionCalendarCache
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.LifecycleStatus
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The #42 lifecycle-status filter's UI-state surface: the row's visibility gate
 * ([BillsListUiState.Success.statusFilterAvailable]) and the current selection
 * pass-through ([BillsListUiState.Success.selectedStatus]). The SQL narrowing
 * itself is proven in [BillsListViewModelPagingTest].
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BillsListViewModelStatusFilterTest {

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `status filter is unavailable when no bill carries a lifecycle status`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a"),
                billFixture("b"),
            ),
        )
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertFalse(state.statusFilterAvailable)
        assertEquals(BillStatusFilter.ALL, state.selectedStatus)
    }

    @Test fun `status filter becomes available once a pre-floor bill is present`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a"),
                billFixture("b", lifecycleStatus = LifecycleStatus.IN_COMMITTEE),
            ),
        )
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertTrue(state.statusFilterAvailable)
    }

    @Test fun `selecting a status surfaces it on the success state`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a", lifecycleStatus = LifecycleStatus.INTRODUCED),
            ),
        )
        vm.setStatusFilter(BillStatusFilter.INTRODUCED)
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(BillStatusFilter.INTRODUCED, state.selectedStatus)
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
