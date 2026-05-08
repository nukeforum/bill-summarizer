package com.informedcitizen.ui.billslist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.ai.AiCapability
import com.informedcitizen.data.ai.BillSummary
import com.informedcitizen.data.ai.BillTopic
import com.informedcitizen.data.ai.FakeAiCapability
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.cache.BillSummaryCache
import com.informedcitizen.data.cache.BillSummaryEntry
import com.informedcitizen.data.model.Action
import com.informedcitizen.data.model.Bill
import com.informedcitizen.data.model.BillsManifest
import com.informedcitizen.data.model.Outcome
import com.informedcitizen.data.model.SessionCalendar
import com.informedcitizen.data.model.SessionCalendarSource
import com.informedcitizen.data.model.Sponsor
import com.informedcitizen.data.repository.AiTitlesPreferenceRepository
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.SessionCalendarRepository
import com.informedcitizen.data.work.BillSummarizationController
import com.informedcitizen.data.work.SummarizationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
        )
        val sessionRepo = SessionCalendarRepository(
            api = StubBillsApi(BillsManifest(generatedAt = "x", congress = 119, bills = emptyList())),
            crashReporter = FakeCrashReporter(),
        )
        val prefs = AiTitlesPreferenceRepository(StubPreferencesDataStore())
        val cap = FakeAiCapability(capability)
        // Controller is unused by the assertions; provide a no-op via a real instance.
        val controller = BillSummarizationController(
            context = StubApplicationContext(),
            prefs = prefs,
            cache = cache,
            billRepository = billRepo,
        )
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
            controller = controller,
        )
    }

    private fun entry(id: String, topic: BillTopic) = BillSummaryEntry(
        billId = id,
        summary = BillSummary("Concise", topic),
        errorKind = null,
        generatedAtMillis = 0L,
    )
}

private fun billFixture(id: String): Bill = Bill(
    id = id,
    congress = 119,
    type = "hr",
    number = "1",
    title = "Title for $id",
    shortTitle = null,
    sponsor = Sponsor(name = "Sponsor", party = "D", state = "CA"),
    introducedDate = "2026-01-01",
    latestAction = Action(date = "2026-05-01", text = "Action"),
    outcome = Outcome.PASSED_HOUSE,
    summaryCrs = null,
    textUrlHtml = null,
    textUrlXml = null,
    textUrlPdf = null,
    congressGovUrl = "https://congress.gov/$id",
)

private class StubBillsApi(private val manifest: BillsManifest) : BillsApi {
    override suspend fun getBills(): BillsManifest = manifest
    override suspend fun getSessionCalendar(): SessionCalendar = SessionCalendar(
        generatedAt = "2026-01-01",
        source = SessionCalendarSource(house = "stub", senate = "stub"),
        chambers = emptyMap(),
    )
}

private class StubPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

private class StubCache(initial: Map<String, BillSummaryEntry> = emptyMap()) : BillSummaryCache {
    private val flow = MutableStateFlow(initial)
    override fun observeAll() = flow
    override suspend fun get(billId: String) = flow.value[billId]
    override suspend fun putSuccess(billId: String, summary: BillSummary, generatedAtMillis: Long) {
        flow.value = flow.value + (billId to BillSummaryEntry(billId, summary, null, generatedAtMillis))
    }
    override suspend fun putError(billId: String, errorKind: String, generatedAtMillis: Long) {
        flow.value = flow.value + (billId to BillSummaryEntry(billId, null, errorKind, generatedAtMillis))
    }
    override suspend fun delete(billId: String) { flow.value = flow.value - billId }
    override suspend fun clearAll() { flow.value = emptyMap() }
    override suspend fun enqueue(billId: String, priority: Int, bypassCap: Boolean, enqueuedAtMillis: Long) {}
    override suspend fun nextPending() = null
    override suspend fun dequeue(billId: String) {}
    override suspend fun queueDepth() = 0L
    override suspend fun clearPending() {}
    override suspend fun incrementAttemptsToday(localDateIso: String) {}
    override suspend fun attemptsToday(localDateIso: String) = 0L
}

// Minimal Context stand-in for the controller; the VM tests never call any
// method on it, so an empty one is enough.
private class StubApplicationContext : android.content.ContextWrapper(null)
