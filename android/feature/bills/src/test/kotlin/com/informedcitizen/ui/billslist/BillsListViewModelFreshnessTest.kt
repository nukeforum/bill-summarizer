package com.informedcitizen.ui.billslist

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.SessionCalendarRepository
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.testutil.FakeBillCache
import com.informedcitizen.testutil.FakeSessionCalendarCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BillsListViewModelFreshnessTest {

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `manifest generated_at surfaces as parsed instant`() = runTest {
        val vm = makeVm(generatedAt = "2026-07-22T07:55:09Z")
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(Instant.parse("2026-07-22T07:55:09Z"), state.dataGeneratedAt)
    }

    @Test fun `unparseable generated_at hides the freshness line`() = runTest {
        val vm = makeVm(generatedAt = "not-a-timestamp")
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertNull(state.dataGeneratedAt)
    }

    private fun makeVm(generatedAt: String): BillsListViewModel {
        val billRepo = BillRepository(
            api = StubBillsApi(
                BillsManifest(
                    generatedAt = generatedAt,
                    congress = 119,
                    bills = listOf(billFixture("a")),
                ),
            ),
            dataStore = StubPreferencesDataStore(),
            crashReporter = FakeCrashReporter(),
            billCache = FakeBillCache(),
        )
        val sessionRepo = SessionCalendarRepository(
            api = StubBillsApi(BillsManifest(generatedAt = "x", congress = 119, bills = emptyList())),
            crashReporter = FakeCrashReporter(),
            persistentCache = FakeSessionCalendarCache(),
        )
        runBlocking { billRepo.getBills(forceRefresh = true) }
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
