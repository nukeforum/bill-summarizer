package com.informedcitizen.ui.billslist

import androidx.paging.testing.asSnapshot
import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.SessionCalendarRepository
import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillShardIndex
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.CongressEntry
import com.informedcitizen.pipeline.model.CongressesIndex
import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.LifecycleStatus
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.SessionCalendar
import com.informedcitizen.testutil.FakeBillCache
import com.informedcitizen.testutil.FakeSessionCalendarCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Exercises the #41 paging stream ([BillsListViewModel.pagedBills]) end-to-end
 * via `asSnapshot`. The cache is pre-seeded with a *complete* shard set so the
 * mediator's `initialize()` skips the network refresh and the Pager reads the
 * seeded rows straight from the DB — the same complete-shard pattern
 * `BillRepositoryTest` uses (mediator fetch itself is covered there). The
 * bill-repo API's manifest fetch fails so the VM's init load never writes an
 * empty manifest through and clobbers the seed; `getCongressesIndex` still
 * resolves the Congress for `resolveCurrentCongress`.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BillsListViewModelPagingTest {

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `pagedBills serves recency-ordered cached bills across page windows`() = runTest {
        val bills = (1..25).map {
            billFixture("hr$it").copy(
                latestAction = Action(date = "2026-01-%02d".format(it), text = "x"),
            )
        }
        val vm = pagingVm(seed(bills))

        val snapshot = vm.pagedBills.asSnapshot()

        // Newest-first (latest_action_date DESC), all pages stitched together.
        assertEquals(bills.map { it.id }.reversed(), snapshot.map { it.id })
    }

    @Test fun `the free-text search filter narrows the paged stream without a Pager rebuild`() = runTest {
        val vm = pagingVm(
            seed(
                listOf(
                    billFixture("a", title = "Clean Water Act"),
                    billFixture("b", title = "Highway Funding Act"),
                    billFixture("c", title = "Water Quality Act"),
                ),
            ),
        )

        vm.setSearchQuery("water")
        val snapshot = vm.pagedBills.asSnapshot()

        assertEquals(setOf("a", "c"), snapshot.map { it.id }.toSet())
    }

    @Test fun `the outcome chip filter narrows the paged stream`() = runTest {
        val vm = pagingVm(
            seed(
                listOf(
                    billFixture("a", outcome = Outcome.PASSED_HOUSE),
                    billFixture("b", outcome = Outcome.FAILED),
                    billFixture("c", outcome = Outcome.FAILED),
                ),
            ),
        )

        vm.setFilter(BillsListFilter.FAILED)
        val snapshot = vm.pagedBills.asSnapshot()

        assertEquals(setOf("b", "c"), snapshot.map { it.id }.toSet())
    }

    @Test fun `the lifecycle-status filter narrows the paged stream in SQL`() = runTest {
        val vm = pagingVm(
            seed(
                listOf(
                    billFixture("a").copy(lifecycleStatus = LifecycleStatus.INTRODUCED),
                    billFixture("b").copy(lifecycleStatus = LifecycleStatus.IN_COMMITTEE),
                    billFixture("c").copy(lifecycleStatus = LifecycleStatus.INTRODUCED),
                ),
            ),
        )

        vm.setStatusFilter(BillStatusFilter.INTRODUCED)
        val snapshot = vm.pagedBills.asSnapshot()

        assertEquals(setOf("a", "c"), snapshot.map { it.id }.toSet())
    }

    @Test fun `the default status filter serves every bill regardless of lifecycle status`() = runTest {
        val vm = pagingVm(
            seed(
                listOf(
                    billFixture("a").copy(lifecycleStatus = LifecycleStatus.INTRODUCED),
                    billFixture("b"),
                    billFixture("c").copy(lifecycleStatus = LifecycleStatus.REPORTED),
                ),
            ),
        )

        // No setStatusFilter call — the ALL default applies no status predicate.
        val snapshot = vm.pagedBills.asSnapshot()

        assertEquals(setOf("a", "b", "c"), snapshot.map { it.id }.toSet())
    }

    @Test fun `the policy-area filter narrows the paged stream in SQL`() = runTest {
        val vm = pagingVm(
            seed(
                listOf(
                    billFixture("a", policyArea = "Health"),
                    billFixture("b", policyArea = "Taxation"),
                    billFixture("c", policyArea = "Health"),
                ),
            ),
        )

        vm.selectPolicyArea("Health")
        val snapshot = vm.pagedBills.asSnapshot()

        assertEquals(setOf("a", "c"), snapshot.map { it.id }.toSet())
    }

    private fun seed(bills: List<Bill>): FakeBillCache = FakeBillCache().apply {
        kotlinx.coroutines.runBlocking {
            appendShard(
                congress = 119,
                source = BillSource.PUBLISHED,
                shardIndex = 0,
                bills = bills,
                generatedAt = "2026-05-01",
                fetchedAtMillis = 1L,
                totalShards = 1,
                pageSize = 500,
            )
        }
    }

    private fun pagingVm(cache: FakeBillCache): BillsListViewModel {
        val billRepo = BillRepository(
            api = OfflineManifestApi(congress = 119),
            dataStore = StubPreferencesDataStore(),
            crashReporter = FakeCrashReporter(),
            billCache = cache,
        )
        val sessionRepo = SessionCalendarRepository(
            api = StubBillsApi(BillsManifest(generatedAt = "x", congress = 119, bills = emptyList())),
            crashReporter = FakeCrashReporter(),
            persistentCache = FakeSessionCalendarCache(),
        )
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

/**
 * A [BillsApi] whose manifest fetch fails (so the init `getBills` falls back to
 * cache rather than writing an empty manifest through) while `getCongressesIndex`
 * still names the current Congress for `resolveCurrentCongress`.
 */
private class OfflineManifestApi(private val congress: Int) : BillsApi {
    override suspend fun getCongressesIndex(): CongressesIndex = CongressesIndex(
        currentCongress = congress,
        congresses = listOf(
            CongressEntry(
                congress = congress,
                manifestPath = "congress${congress}_bills.json",
                isCurrent = true,
            ),
        ),
    )
    override suspend fun getBillsManifest(url: String): BillsManifest = error("offline")
    override suspend fun getBillShardIndex(url: String): BillShardIndex = error("no shard index")
    override suspend fun getSessionCalendar(): SessionCalendar = error("not used in this test")
    override suspend fun getElectionCalendar(): ElectionCalendar = error("not used in this test")
}
