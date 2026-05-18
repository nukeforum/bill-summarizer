package com.informedcitizen.data.work

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.testutil.FakeBillCache
import com.informedcitizen.data.ai.BillSummarizer
import com.informedcitizen.data.ai.BillSummary
import com.informedcitizen.data.ai.BillTopic
import com.informedcitizen.data.ai.FakeBillSummarizer
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.cache.BillSummaryCache
import com.informedcitizen.data.cache.BillSummaryEntry
import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.CongressEntry
import com.informedcitizen.pipeline.model.CongressesIndex
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.SessionCalendar
import com.informedcitizen.pipeline.model.Sponsor
import com.informedcitizen.data.repository.AiTitlesPreferenceRepository
import com.informedcitizen.data.repository.AiTitlesPreferenceRepositoryImpl
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.feature.aititles.AiTitlesHost
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BillSummarizationWorkerTest {

    private lateinit var context: Context

    @Before fun setUp() { context = ApplicationProvider.getApplicationContext() }

    @Test fun `worker drains queue and writes successes`() = runBlocking {
        val cache = FakeCache().apply {
            enqueue("a", priority = 0, bypassCap = false, enqueuedAtMillis = 1L)
            enqueue("b", priority = 0, bypassCap = false, enqueuedAtMillis = 2L)
        }
        val repo = fakeBillRepo(listOf(billFixture("a"), billFixture("b")))
        val summarizer = FakeBillSummarizer(defaultTopic = BillTopic.Tech)
        val prefs = fakePrefs(enabled = true, scope = SummarizationScope.All)

        val worker = build(cache, repo, summarizer, prefs)
        val result = worker.doWork()
        assertEquals(Result.success(), result)
        assertNotNull(cache.get("a")?.summary)
        assertNotNull(cache.get("b")?.summary)
        assertEquals(0L, cache.queueDepth())
        assertEquals(2L, cache.attemptsToday(TODAY))
    }

    @Test fun `worker exits when toggle is off`() = runBlocking {
        val cache = FakeCache().apply { enqueue("a", 0, false, 1L) }
        val repo = fakeBillRepo(listOf(billFixture("a")))
        val summarizer = FakeBillSummarizer()
        val prefs = fakePrefs(enabled = false, scope = SummarizationScope.All)

        val worker = build(cache, repo, summarizer, prefs)
        worker.doWork()
        assertEquals(1L, cache.queueDepth())
        assertEquals(0L, cache.attemptsToday(TODAY))
    }

    @Test fun `attempts are counted on failure too`() = runBlocking {
        val cache = FakeCache().apply { enqueue("a", 0, false, 1L) }
        val repo = fakeBillRepo(listOf(billFixture("a")))
        val summarizer = FakeBillSummarizer(forcedFailures = mapOf("a" to "TIMEOUT"))
        val prefs = fakePrefs(enabled = true, scope = SummarizationScope.All)

        val worker = build(cache, repo, summarizer, prefs)
        worker.doWork()
        assertEquals("TIMEOUT", cache.get("a")?.errorKind)
        assertEquals(1L, cache.attemptsToday(TODAY))
    }

    @Test fun `progressive cap stops the worker before extra work`() = runBlocking {
        val cache = FakeCache().apply {
            repeat(5) { enqueue("b$it", 0, false, it.toLong()) }
        }
        val repo = fakeBillRepo(List(5) { billFixture("b$it") })
        val summarizer = FakeBillSummarizer()
        val prefs = fakePrefs(enabled = true, scope = SummarizationScope.Progressive(2))

        val worker = build(cache, repo, summarizer, prefs)
        worker.doWork()
        assertEquals(3L, cache.queueDepth())
        assertEquals(2L, cache.attemptsToday(TODAY))
    }

    @Test fun `bypass-cap rows count attempts but skip the cap check`() = runBlocking {
        val cache = FakeCache().apply {
            enqueue("first", 0, false, 1L)
            enqueue("forced", 100, true, 2L)
        }
        val repo = fakeBillRepo(listOf(billFixture("first"), billFixture("forced")))
        val summarizer = FakeBillSummarizer()
        val prefs = fakePrefs(enabled = true, scope = SummarizationScope.Progressive(1))

        val worker = build(cache, repo, summarizer, prefs)
        worker.doWork()
        assertNotNull(cache.get("forced"))
        assertEquals(1L, cache.queueDepth())
    }

    private fun build(
        cache: BillSummaryCache,
        repo: BillRepository,
        summarizer: BillSummarizer,
        prefs: AiTitlesPreferenceRepository,
    ): BillSummarizationWorker = TestListenableWorkerBuilder<BillSummarizationWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ) = BillSummarizationWorker(
                appContext, workerParameters,
                cache = cache,
                billRepository = repo,
                summarizer = summarizer,
                prefs = prefs,
                clock = FIXED_CLOCK,
                host = NoOpAiTitlesHost,
            )
        })
        .build()

    companion object {
        private const val TODAY = "2026-05-08"
        private val FIXED_CLOCK: Clock =
            Clock.fixed(Instant.parse("2026-05-08T12:00:00Z"), ZoneOffset.UTC)
    }
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

private fun fakeBillRepo(bills: List<Bill>): BillRepository {
    val repo = BillRepository(
        api = StubBillsApi(BillsManifest(generatedAt = "x", congress = 119, bills = bills)),
        dataStore = StubPreferencesDataStore(),
        crashReporter = FakeCrashReporter(),
        billCache = FakeBillCache(),
    )
    runBlocking { repo.getBills(forceRefresh = true) }
    return repo
}

private fun fakePrefs(
    enabled: Boolean,
    scope: SummarizationScope,
): AiTitlesPreferenceRepository {
    val store = StubPreferencesDataStore()
    val prefs = AiTitlesPreferenceRepositoryImpl(store)
    runBlocking {
        prefs.setEnabled(enabled)
        prefs.setScope(scope)
    }
    return prefs
}

private class StubBillsApi(private val manifest: BillsManifest) : BillsApi {
    override suspend fun getCongressesIndex(): CongressesIndex = CongressesIndex(
        currentCongress = manifest.congress,
        congresses = listOf(CongressEntry(congress = manifest.congress, manifestPath = "congress${manifest.congress}_bills.json", isCurrent = true)),
    )
    override suspend fun getBillsManifest(url: String): BillsManifest = manifest
    override suspend fun getSessionCalendar(): SessionCalendar = error("unused")
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

private class FakeCache : BillSummaryCache {
    private val rows = LinkedHashMap<String, BillSummaryEntry>()
    private val pending = mutableListOf<BillSummaryCache.PendingItem>()
    private val attempts = HashMap<String, Long>()
    private val emissions = MutableStateFlow<Map<String, BillSummaryEntry>>(emptyMap())

    override fun observeAll() = emissions

    override suspend fun get(billId: String): BillSummaryEntry? = rows[billId]

    override suspend fun putSuccess(
        billId: String,
        summary: BillSummary,
        generatedAtMillis: Long,
    ) {
        rows[billId] = BillSummaryEntry(billId, summary, null, generatedAtMillis)
        emissions.value = rows.toMap()
    }

    override suspend fun putError(
        billId: String,
        errorKind: String,
        generatedAtMillis: Long,
    ) {
        rows[billId] = BillSummaryEntry(billId, null, errorKind, generatedAtMillis)
        emissions.value = rows.toMap()
    }

    override suspend fun delete(billId: String) {
        rows.remove(billId)
        emissions.value = rows.toMap()
    }

    override suspend fun clearAll() {
        rows.clear()
        emissions.value = emptyMap()
    }

    override suspend fun enqueue(
        billId: String,
        priority: Int,
        bypassCap: Boolean,
        enqueuedAtMillis: Long,
    ) {
        pending.removeAll { it.billId == billId }
        pending += BillSummaryCache.PendingItem(billId, priority, bypassCap, enqueuedAtMillis)
    }

    override suspend fun nextPending(): BillSummaryCache.PendingItem? =
        pending.sortedWith(compareByDescending<BillSummaryCache.PendingItem> { it.priority }
            .thenBy { it.enqueuedAtMillis })
            .firstOrNull()

    override suspend fun dequeue(billId: String) {
        pending.removeAll { it.billId == billId }
    }

    override suspend fun queueDepth(): Long = pending.size.toLong()

    override suspend fun clearPending() { pending.clear() }

    override suspend fun incrementAttemptsToday(localDateIso: String) {
        attempts[localDateIso] = (attempts[localDateIso] ?: 0L) + 1
    }

    override suspend fun attemptsToday(localDateIso: String): Long =
        attempts[localDateIso] ?: 0L
}

private object NoOpAiTitlesHost : AiTitlesHost {
    override fun openAiSettingsIntent(context: Context): Intent = Intent()
    override val notificationSmallIconResId: Int = 0
}
