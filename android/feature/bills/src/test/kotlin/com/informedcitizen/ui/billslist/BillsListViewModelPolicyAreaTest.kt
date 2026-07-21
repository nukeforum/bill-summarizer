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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BillsListViewModelPolicyAreaTest {

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `selecting a policy area narrows the list to that subject`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a", policyArea = "Armed Forces and National Security"),
                billFixture("b", policyArea = "Education"),
                billFixture("c", policyArea = null),
            ),
        )
        vm.selectPolicyArea("Education")
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(listOf("b"), state.bills.map { it.id })
        assertEquals("Education", state.selectedPolicyArea)
    }

    @Test fun `available policy areas are distinct sorted and skip untagged bills`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a", policyArea = "Education"),
                billFixture("b", policyArea = "Armed Forces and National Security"),
                billFixture("c", policyArea = "Education"),
                billFixture("d", policyArea = null),
            ),
        )
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(
            listOf("Armed Forces and National Security", "Education"),
            state.availablePolicyAreas,
        )
    }

    @Test fun `policy area composes with keyword search`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a", title = "Funding Act", policyArea = "Education"),
                billFixture("b", title = "Savings Act", policyArea = "Education"),
                billFixture("c", title = "Funding Act Two", policyArea = "Health"),
            ),
        )
        vm.selectPolicyArea("Education")
        vm.setSearchQuery("funding")
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(listOf("a"), state.bills.map { it.id })
    }

    @Test fun `policy area composes with the outcome filter`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a", outcome = Outcome.PASSED_HOUSE, policyArea = "Education"),
                billFixture("b", outcome = Outcome.FAILED, policyArea = "Education"),
                billFixture("c", outcome = Outcome.FAILED, policyArea = "Health"),
            ),
        )
        vm.setFilter(BillsListFilter.FAILED)
        vm.selectPolicyArea("Education")
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(listOf("b"), state.bills.map { it.id })
    }

    @Test fun `clearing the policy area restores the full list`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a", policyArea = "Education"),
                billFixture("b", policyArea = "Health"),
            ),
        )
        vm.selectPolicyArea("Education")
        vm.selectPolicyArea(null)
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(listOf("a", "b"), state.bills.map { it.id })
        assertNull(state.selectedPolicyArea)
    }

    @Test fun `a selection absent from the loaded bills deselects instead of pinning empty`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a", policyArea = "Education"),
                billFixture("b", policyArea = null),
            ),
        )
        vm.selectPolicyArea("Agriculture and Food")
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(listOf("a", "b"), state.bills.map { it.id })
        assertNull(state.selectedPolicyArea)
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
