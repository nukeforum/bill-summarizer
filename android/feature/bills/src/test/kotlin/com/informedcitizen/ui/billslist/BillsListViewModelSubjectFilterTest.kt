package com.informedcitizen.ui.billslist

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.testutil.FakeBillCache
import com.informedcitizen.testutil.FakeSessionCalendarCache
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillsManifest
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
class BillsListViewModelSubjectFilterTest {

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `selecting a subject narrows the list to bills tagged with it`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a", subjects = listOf("Firearms and explosives", "Crime and law enforcement")),
                billFixture("b", subjects = listOf("Appropriations")),
                billFixture("c", subjects = emptyList()),
            ),
        )
        vm.selectSubject("Firearms and explosives")
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(listOf("a"), state.bills.map { it.id })
        assertEquals("Firearms and explosives", state.selectedSubject)
    }

    @Test fun `available subjects are flattened, distinct and sorted`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a", subjects = listOf("Taxation", "Firearms and explosives")),
                billFixture("b", subjects = listOf("Firearms and explosives")),
                billFixture("c", subjects = emptyList()),
            ),
        )
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(
            listOf("Firearms and explosives", "Taxation"),
            state.availableSubjects,
        )
    }

    @Test fun `subject composes with keyword search`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a", title = "Background Check Act", subjects = listOf("Firearms and explosives")),
                billFixture("b", title = "Border Wall Act", subjects = listOf("Firearms and explosives")),
                billFixture("c", title = "Background Check Funding", subjects = listOf("Appropriations")),
            ),
        )
        vm.selectSubject("Firearms and explosives")
        vm.setSearchQuery("background")
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(listOf("a"), state.bills.map { it.id })
    }

    @Test fun `a subject absent from the loaded bills deselects instead of pinning empty`() = runTest {
        val vm = makeVm(
            listOf(
                billFixture("a", subjects = listOf("Firearms and explosives")),
                billFixture("b", subjects = emptyList()),
            ),
        )
        vm.selectSubject("Public lands and natural resources")
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(listOf("a", "b"), state.bills.map { it.id })
        assertNull(state.selectedSubject)
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
