package com.informedcitizen.data.repository

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.CongressesIndex
import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.ElectionEvent
import com.informedcitizen.pipeline.model.ElectionType
import com.informedcitizen.pipeline.model.SessionCalendar
import com.informedcitizen.testutil.FakeElectionCalendarCache
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ElectionCalendarRepositoryTest {

    @Test
    fun `success path caches result and does not call CrashReporter`() = runTest {
        val reporter = FakeCrashReporter()
        val api = StubApi(SAMPLE_CALENDAR)
        val repo = ElectionCalendarRepository(api, reporter, FakeElectionCalendarCache())

        val first = repo.getCalendar()
        val second = repo.getCalendar()

        assertTrue(first.isSuccess)
        assertEquals(SAMPLE_CALENDAR, first.getOrNull())
        assertSame(first.getOrNull(), second.getOrNull())
        assertEquals(1, api.callCount)
        assertTrue("no non-fatal recorded on success", reporter.recorded.isEmpty())
    }

    @Test
    fun `success path writes through to the persistent cache as PUBLISHED`() = runTest {
        val cache = FakeElectionCalendarCache()
        val repo = ElectionCalendarRepository(StubApi(SAMPLE_CALENDAR), FakeCrashReporter(), cache)

        repo.getCalendar()

        val persisted = cache.load(BillSource.PUBLISHED)
        assertNotNull(persisted)
        assertEquals(SAMPLE_CALENDAR, persisted!!.value)
    }

    @Test
    fun `failure path records non-fatal and surfaces failure when cache empty`() = runTest {
        val reporter = FakeCrashReporter()
        val boom = IOException("simulated network failure")
        val repo = ElectionCalendarRepository(ThrowingApi(boom), reporter, FakeElectionCalendarCache())

        val result = repo.getCalendar()

        assertTrue(result.isFailure)
        assertEquals(1, reporter.recorded.size)
        assertSame(boom, reporter.recorded.single().throwable)
        assertEquals("election calendar fetch failed", reporter.recorded.single().message)
    }

    @Test
    fun `failure path falls back to freshest persisted calendar`() = runTest {
        val reporter = FakeCrashReporter()
        val cache = FakeElectionCalendarCache().apply {
            replaceForSource(BillSource.PUBLISHED, SAMPLE_CALENDAR, fetchedAtMillis = 1L)
        }
        val repo = ElectionCalendarRepository(ThrowingApi(IOException("offline")), reporter, cache)

        val result = repo.getCalendar()

        assertTrue(result.isSuccess)
        assertEquals(SAMPLE_CALENDAR, result.getOrNull())
        // The network failure is still reported even though the UI got data.
        assertEquals(1, reporter.recorded.size)
    }

    @Test
    fun `publishByokCalendar replaces in-memory value and persists as BYOK`() = runTest {
        val cache = FakeElectionCalendarCache()
        val api = StubApi(SAMPLE_CALENDAR)
        val repo = ElectionCalendarRepository(api, FakeCrashReporter(), cache)
        repo.getCalendar()

        val byok = SAMPLE_CALENDAR.copy(generatedAt = "2026-06-12T00:00:00Z")
        repo.publishByokCalendar(byok)

        assertEquals(byok, repo.getCalendar().getOrNull())
        assertEquals(1, api.callCount) // served from memory, no refetch
        assertEquals(byok, cache.load(BillSource.BYOK)?.value)
    }

    @Test
    fun `forceRefresh re-fetches`() = runTest {
        val api = StubApi(SAMPLE_CALENDAR)
        val repo = ElectionCalendarRepository(api, FakeCrashReporter(), FakeElectionCalendarCache())

        repo.getCalendar()
        repo.getCalendar(forceRefresh = true)

        assertEquals(2, api.callCount)
    }

    private companion object {
        val SAMPLE_CALENDAR = ElectionCalendar(
            generatedAt = "2026-05-05T12:00:00Z",
            source = "https://example.invalid/elections",
            elections = listOf(
                ElectionEvent(
                    state = ElectionEvent.NATIONWIDE,
                    date = "2026-11-03",
                    type = ElectionType.GENERAL,
                    electionYear = 2026,
                ),
                ElectionEvent(
                    state = "TX",
                    date = "2026-03-03",
                    type = ElectionType.PRIMARY,
                    electionYear = 2026,
                    source = "https://example.invalid/tx",
                ),
            ),
        )
    }

    private class StubApi(private val calendar: ElectionCalendar) : BillsApi {
        var callCount = 0
        override suspend fun getCongressesIndex(): CongressesIndex = error("not used in this test")
        override suspend fun getBillsManifest(url: String): BillsManifest = error("not used in this test")
        override suspend fun getSessionCalendar(): SessionCalendar = error("not used in this test")
        override suspend fun getElectionCalendar(): ElectionCalendar {
            callCount += 1
            return calendar
        }
    }

    private class ThrowingApi(private val throwable: Throwable) : BillsApi {
        override suspend fun getCongressesIndex(): CongressesIndex = error("not used in this test")
        override suspend fun getBillsManifest(url: String): BillsManifest = error("not used in this test")
        override suspend fun getSessionCalendar(): SessionCalendar = error("not used in this test")
        override suspend fun getElectionCalendar(): ElectionCalendar = throw throwable
    }
}
