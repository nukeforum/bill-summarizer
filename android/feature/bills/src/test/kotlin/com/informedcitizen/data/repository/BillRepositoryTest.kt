package com.informedcitizen.data.repository

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillShard
import com.informedcitizen.pipeline.model.BillShardIndex
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.CongressEntry
import com.informedcitizen.pipeline.model.CongressesIndex
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.SessionCalendar
import com.informedcitizen.pipeline.model.Sponsor
import com.informedcitizen.testutil.FakeBillCache
import com.informedcitizen.testutil.InMemoryPreferencesDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BillRepositoryTest {

    @Test
    fun `success path does not call CrashReporter`() = runTest {
        val reporter = FakeCrashReporter()
        val repo = BillRepository(
            api = StubApi(BillsManifest(generatedAt = "2026-01-01", congress = 119, bills = emptyList())),
            dataStore = InMemoryPreferencesDataStore(),
            crashReporter = reporter,
            billCache = FakeBillCache(),
        )

        val result = repo.getBills(forceRefresh = true)

        assertTrue(result.isSuccess)
        assertTrue("no non-fatal recorded on success", reporter.recorded.isEmpty())
    }

    @Test
    fun `failure path records non-fatal and surfaces failure`() = runTest {
        val reporter = FakeCrashReporter()
        val boom = IOException("simulated network failure")
        val repo = BillRepository(
            api = ThrowingApi(boom),
            dataStore = InMemoryPreferencesDataStore(),
            crashReporter = reporter,
            billCache = FakeBillCache(),
        )

        val result = repo.getBills(forceRefresh = true)

        assertTrue("getBills returns failure", result.isFailure)
        assertEquals(1, reporter.recorded.size)
        assertSame(boom, reporter.recorded.single().throwable)
        assertEquals("manifest fetch failed", reporter.recorded.single().message)
    }

    @Test
    fun `containsBillId returns true for cached bills after load`() = runTest {
        val bills = listOf(
            sampleBill(id = "hr1234-119"),
            sampleBill(id = "s5-119"),
        )
        val repo = BillRepository(
            api = StubApi(BillsManifest(generatedAt = "2026-01-01", congress = 119, bills = bills)),
            dataStore = InMemoryPreferencesDataStore(),
            crashReporter = FakeCrashReporter(),
            billCache = FakeBillCache(),
        )
        repo.getBills(forceRefresh = true)
        assertTrue(repo.containsBillId("hr1234-119"))
        assertTrue(repo.containsBillId("s5-119"))
        assertFalse(repo.containsBillId("hr9999-119"))
    }

    @Test
    fun `getBills resolves manifest URL from congresses index for current congress`() = runTest {
        val api = StubApi(
            manifest = BillsManifest(generatedAt = "x", congress = 119, bills = emptyList()),
            index = CongressesIndex(
                currentCongress = 119,
                congresses = listOf(
                    CongressEntry(congress = 118, manifestPath = "congress118_bills.json"),
                    CongressEntry(congress = 119, manifestPath = "congress119_bills.json", isCurrent = true),
                ),
            ),
        )
        val repo = BillRepository(api, InMemoryPreferencesDataStore(), FakeCrashReporter(), FakeBillCache())

        val result = repo.getBills(forceRefresh = true)

        assertTrue(result.isSuccess)
        assertEquals(listOf("data/congress119_bills.json"), api.manifestUrls)
    }

    @Test
    fun `fetchShardIndex returns null when current congress has no shard index`() = runTest {
        // Dual-publish transition: unsharded Congress publishes only manifest_path.
        val api = StubApi(BillsManifest(generatedAt = "x", congress = 119, bills = emptyList()))
        val repo = BillRepository(api, InMemoryPreferencesDataStore(), FakeCrashReporter(), FakeBillCache())

        assertEquals(null, repo.fetchShardIndex())
        assertTrue("no shard index fetched when path absent", api.shardIndexUrls.isEmpty())
    }

    @Test
    fun `fetchShardIndex follows shard_index_path from the current congress entry`() = runTest {
        val shardIndex = BillShardIndex(
            generatedAt = "x",
            congress = 119,
            pageSize = 500,
            totalBills = 3,
            shards = listOf(BillShard(page = 1, path = "congress119_bills_p001.json", count = 3)),
        )
        val api = StubApi(
            manifest = BillsManifest(generatedAt = "x", congress = 119, bills = emptyList()),
            shardIndex = shardIndex,
            shardIndexPath = "congress119_bills_index.json",
        )
        val repo = BillRepository(api, InMemoryPreferencesDataStore(), FakeCrashReporter(), FakeBillCache())

        val result = repo.fetchShardIndex()

        assertSame(shardIndex, result)
        assertEquals(listOf("data/congress119_bills_index.json"), api.shardIndexUrls)
    }

    @Test
    fun `fetchShard fetches the shard file under the data directory`() = runTest {
        val shardManifest = BillsManifest(
            generatedAt = "x",
            congress = 119,
            bills = listOf(sampleBill(id = "hr1-119")),
        )
        val api = StubApi(manifest = shardManifest)
        val repo = BillRepository(api, InMemoryPreferencesDataStore(), FakeCrashReporter(), FakeBillCache())

        val result = repo.fetchShard(BillShard(page = 1, path = "congress119_bills_p001.json", count = 1))

        assertSame(shardManifest, result)
        assertEquals(listOf("data/congress119_bills_p001.json"), api.manifestUrls)
    }

    @Test
    fun `fetchCurrentManifest resolves the whole manifest via the congresses index`() = runTest {
        val manifest = BillsManifest(generatedAt = "x", congress = 119, bills = listOf(sampleBill("hr1-119")))
        val api = StubApi(manifest)
        val repo = BillRepository(api, InMemoryPreferencesDataStore(), FakeCrashReporter(), FakeBillCache())

        val result = repo.fetchCurrentManifest()

        assertSame(manifest, result)
        assertEquals(listOf("data/congress119_bills.json"), api.manifestUrls)
    }

    @Test
    fun `getBills fails when index has no entry for current congress`() = runTest {
        val reporter = FakeCrashReporter()
        val api = StubApi(
            manifest = BillsManifest(generatedAt = "x", congress = 119, bills = emptyList()),
            index = CongressesIndex(
                currentCongress = 119,
                congresses = listOf(CongressEntry(congress = 118, manifestPath = "congress118_bills.json")),
            ),
        )
        val repo = BillRepository(api, InMemoryPreferencesDataStore(), reporter, FakeBillCache())

        val result = repo.getBills(forceRefresh = true)

        assertTrue(result.isFailure)
        assertTrue("manifest URL never fetched", api.manifestUrls.isEmpty())
        assertEquals(1, reporter.recorded.size)
        assertEquals("manifest fetch failed", reporter.recorded.single().message)
    }

    @Test
    fun `successful fetch writes manifest through to cache under PUBLISHED source`() = runTest {
        val bills = listOf(sampleBill("hr1-119"), sampleBill("hr2-119"))
        val cache = FakeBillCache()
        val manifest = BillsManifest(generatedAt = "2026-05-15T00:00:00Z", congress = 119, bills = bills)
        val repo = BillRepository(
            api = StubApi(manifest),
            dataStore = InMemoryPreferencesDataStore(),
            crashReporter = FakeCrashReporter(),
            billCache = cache,
        )

        repo.getBills(forceRefresh = true)

        assertEquals(1, cache.writes.size)
        val write = cache.writes.single()
        assertEquals(119, write.congress)
        assertEquals(BillSource.PUBLISHED, write.source)
        assertEquals(bills, write.bills)
        assertEquals("2026-05-15T00:00:00Z", write.generatedAt)
        val meta = cache.loadManifest(119, BillSource.PUBLISHED)
        assertNotNull(meta)
    }

    @Test
    fun `failed fetch does not write through to cache`() = runTest {
        val cache = FakeBillCache()
        val repo = BillRepository(
            api = ThrowingApi(IOException("boom")),
            dataStore = InMemoryPreferencesDataStore(),
            crashReporter = FakeCrashReporter(),
            billCache = cache,
        )
        repo.getBills(forceRefresh = true)
        assertTrue("no cache write on failure", cache.writes.isEmpty())
    }

    @Test
    fun `failed fetch falls back to freshest cached snapshot`() = runTest {
        val reporter = FakeCrashReporter()
        val cached = listOf(sampleBill("hr1-119"))
        val cache = FakeBillCache().apply {
            replaceForSource(
                congress = 119,
                source = BillSource.PUBLISHED,
                bills = cached,
                generatedAt = "2026-06-01T00:00:00Z",
                fetchedAtMillis = 1L,
            )
            writes.clear()
        }
        val repo = BillRepository(
            api = ThrowingApi(IOException("offline")),
            dataStore = InMemoryPreferencesDataStore(),
            crashReporter = reporter,
            billCache = cache,
        )

        val result = repo.getBills(forceRefresh = true)

        assertTrue(result.isSuccess)
        assertEquals(cached, result.getOrNull())
        // The network failure is still reported even though the UI got data.
        assertEquals(1, reporter.recorded.size)
    }

    @Test
    fun `publishByokBills updates in-memory state and persists under BYOK source`() = runTest {
        val cache = FakeBillCache()
        val repo = BillRepository(
            api = ThrowingApi(IOException("never used")),
            dataStore = InMemoryPreferencesDataStore(),
            crashReporter = FakeCrashReporter(),
            billCache = cache,
        )
        val byokBills = listOf(sampleBill("hr7-119"))

        repo.publishByokBills(
            BillsManifest(generatedAt = "2026-06-12T00:00:00Z", congress = 119, bills = byokBills),
        )

        assertTrue(repo.containsBillId("hr7-119"))
        assertEquals(byokBills, cache.loadBills(119, BillSource.BYOK))
        assertTrue(cache.loadBills(119, BillSource.PUBLISHED).isEmpty())
    }

    @Test
    fun `votes coverage defaults false and flips with the fetched manifest`() = runTest {
        val repo = BillRepository(
            api = StubApi(
                BillsManifest(generatedAt = "x", congress = 119, votesCoverage = true, bills = emptyList()),
            ),
            dataStore = InMemoryPreferencesDataStore(),
            crashReporter = FakeCrashReporter(),
            billCache = FakeBillCache(),
        )

        assertFalse(repo.votesCoverage.value)
        repo.getBills(forceRefresh = true)
        assertTrue(repo.votesCoverage.value)
    }

    @Test
    fun `cache fallback leaves votes coverage off`() = runTest {
        val cache = FakeBillCache().apply {
            replaceForSource(
                congress = 119,
                source = BillSource.PUBLISHED,
                bills = listOf(sampleBill("hr1-119")),
                generatedAt = "2026-06-01T00:00:00Z",
                fetchedAtMillis = 1L,
            )
        }
        val repo = BillRepository(
            api = ThrowingApi(IOException("offline")),
            dataStore = InMemoryPreferencesDataStore(),
            crashReporter = FakeCrashReporter(),
            billCache = cache,
        )

        val result = repo.getBills(forceRefresh = true)

        assertTrue(result.isSuccess)
        // Coverage is manifest-level and not cached, so vote surfaces
        // stay hidden on the offline fallback.
        assertFalse(repo.votesCoverage.value)
    }

    @Test
    fun `publishByokBills carries the manifest votes coverage`() = runTest {
        val repo = BillRepository(
            api = ThrowingApi(IOException("never used")),
            dataStore = InMemoryPreferencesDataStore(),
            crashReporter = FakeCrashReporter(),
            billCache = FakeBillCache(),
        )

        repo.publishByokBills(
            BillsManifest(
                generatedAt = "2026-06-12T00:00:00Z",
                congress = 119,
                votesCoverage = true,
                bills = listOf(sampleBill("hr7-119")),
            ),
        )

        assertTrue(repo.votesCoverage.value)
    }

    @Test
    fun `containsBillId returns false before load`() {
        val repo = BillRepository(
            api = StubApi(BillsManifest(generatedAt = "x", congress = 119, bills = emptyList())),
            dataStore = InMemoryPreferencesDataStore(),
            crashReporter = FakeCrashReporter(),
            billCache = FakeBillCache(),
        )
        assertFalse(repo.containsBillId("hr1-119"))
    }

    private class StubApi(
        private val manifest: BillsManifest,
        private val shardIndex: BillShardIndex? = null,
        shardIndexPath: String? = null,
        private val index: CongressesIndex = CongressesIndex(
            currentCongress = manifest.congress,
            congresses = listOf(
                CongressEntry(
                    congress = manifest.congress,
                    manifestPath = "congress${manifest.congress}_bills.json",
                    isCurrent = true,
                    shardIndexPath = shardIndexPath,
                ),
            ),
        ),
    ) : BillsApi {
        val manifestUrls = mutableListOf<String>()
        val shardIndexUrls = mutableListOf<String>()
        override suspend fun getCongressesIndex(): CongressesIndex = index
        override suspend fun getBillsManifest(url: String): BillsManifest {
            manifestUrls += url
            return manifest
        }
        override suspend fun getBillShardIndex(url: String): BillShardIndex {
            shardIndexUrls += url
            return shardIndex ?: error("no shard index configured")
        }
        override suspend fun getSessionCalendar(): SessionCalendar = error("not used in this test")
        override suspend fun getElectionCalendar(): ElectionCalendar = error("not used in this test")
    }

    private class ThrowingApi(
        private val throwable: Throwable,
        private val failOnIndex: Boolean = true,
        private val index: CongressesIndex = CongressesIndex(
            currentCongress = 119,
            congresses = listOf(CongressEntry(congress = 119, manifestPath = "congress119_bills.json", isCurrent = true)),
        ),
    ) : BillsApi {
        override suspend fun getCongressesIndex(): CongressesIndex =
            if (failOnIndex) throw throwable else index
        override suspend fun getBillsManifest(url: String): BillsManifest = throw throwable
        override suspend fun getBillShardIndex(url: String): BillShardIndex = throw throwable
        override suspend fun getSessionCalendar(): SessionCalendar = error("not used in this test")
        override suspend fun getElectionCalendar(): ElectionCalendar = error("not used in this test")
    }

    private fun sampleBill(id: String) = Bill(
        id = id,
        congress = 119,
        type = "hr",
        number = id.substringAfter("hr").substringBefore("-").substringAfter("s"),
        title = "Sample",
        sponsor = Sponsor(name = "Test", party = "D", state = "CA"),
        introducedDate = "2026-01-01",
        latestAction = Action(date = "2026-01-02", text = "Test"),
        outcome = Outcome.PASSED_HOUSE,
        congressGovUrl = "https://example.com/$id",
    )
}
