package com.informedcitizen.ui.billslist

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.testutil.FakeBillCache
import com.informedcitizen.testutil.FakeSessionCalendarCache
import com.informedcitizen.data.ai.AiCapability
import com.informedcitizen.data.ai.BillSummary
import com.informedcitizen.data.ai.BillTopic
import com.informedcitizen.data.cache.BillSummaryEntry
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.SessionCalendarRepository
import com.informedcitizen.data.work.SummarizationScope
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BillsListViewModelAiTitlesTest {

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `topic filter restricts visible bills to matching summaries`() = runTest {
        val vm = makeVm(
            bills = listOf(billFixture("a"), billFixture("b"), billFixture("c")),
            summaries = mapOf(
                "a" to entry("a", BillTopic.Tech),
                "b" to entry("b", BillTopic.Healthcare),
            ),
            aiEnabled = true,
            capability = AiCapability.Status.Available,
        )
        vm.selectTopic(BillTopic.Tech)
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(listOf("a"), state.bills.map { it.id })
        assertEquals(2, state.hiddenByTopicCount)
    }

    @Test fun `clearing topic shows all bills`() = runTest {
        val vm = makeVm(
            bills = listOf(billFixture("a"), billFixture("b")),
            summaries = mapOf("a" to entry("a", BillTopic.Tech)),
            aiEnabled = true,
            capability = AiCapability.Status.Available,
        )
        vm.selectTopic(BillTopic.Tech)
        vm.selectTopic(null)
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertEquals(2, state.bills.size)
        assertEquals(0, state.hiddenByTopicCount)
    }

    @Test fun `topic chips and filter row hide when feature is disabled`() = runTest {
        val vm = makeVm(
            bills = listOf(billFixture("a")),
            summaries = mapOf("a" to entry("a", BillTopic.Tech)),
            aiEnabled = false,
            capability = AiCapability.Status.Available,
        )
        val state = vm.uiState.filterIsInstance<BillsListUiState.Success>().first()
        assertFalse(state.aiTitlesEnabled)
        assertEquals(true, state.summaries.isEmpty())
        assertNull(state.selectedTopic)
    }

    private fun makeVm(
        bills: List<Bill>,
        summaries: Map<String, BillSummaryEntry>,
        aiEnabled: Boolean,
        capability: AiCapability.Status,
    ): BillsListViewModel {
        val cache = StubCache(summaries)
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
        val prefs = FakeAiTitlesPrefs()
        val cap = FakeAiCapability(capability)
        kotlinx.coroutines.runBlocking {
            billRepo.getBills(forceRefresh = true)
            prefs.setEnabled(aiEnabled)
            prefs.setScope(SummarizationScope.All)
        }
        return BillsListViewModel(
            billRepository = billRepo,
            sessionCalendarRepository = sessionRepo,
            cache = cache,
            aiPrefs = prefs,
            aiCapability = cap,
            controller = NoOpController,
        )
    }

    private fun entry(id: String, topic: BillTopic) = BillSummaryEntry(
        billId = id,
        summary = BillSummary("Concise", topic),
        errorKind = null,
        generatedAtMillis = 0L,
    )
}
