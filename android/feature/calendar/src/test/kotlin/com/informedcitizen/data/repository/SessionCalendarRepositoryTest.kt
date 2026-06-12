package com.informedcitizen.data.repository

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.CongressEntry
import com.informedcitizen.pipeline.model.CongressesIndex
import com.informedcitizen.pipeline.model.ChamberCalendar
import com.informedcitizen.pipeline.model.SessionCalendar
import com.informedcitizen.pipeline.model.SessionCalendarSource
import com.informedcitizen.testutil.FakeSessionCalendarCache
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SessionCalendarRepositoryTest {

    @Test
    fun `success path caches result and does not call CrashReporter`() = runTest {
        val reporter = FakeCrashReporter()
        val api = StubApi(SAMPLE_CALENDAR)
        val repo = SessionCalendarRepository(api, reporter, FakeSessionCalendarCache())

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
        val cache = FakeSessionCalendarCache()
        val repo = SessionCalendarRepository(StubApi(SAMPLE_CALENDAR), FakeCrashReporter(), cache)

        repo.getCalendar()

        val persisted = cache.load(BillSource.PUBLISHED)
        assertNotNull(persisted)
        assertEquals(SAMPLE_CALENDAR, persisted!!.value)
    }

    @Test
    fun `failure path records non-fatal and surfaces failure when cache empty`() = runTest {
        val reporter = FakeCrashReporter()
        val boom = IOException("simulated network failure")
        val repo = SessionCalendarRepository(ThrowingApi(boom), reporter, FakeSessionCalendarCache())

        val result = repo.getCalendar()

        assertTrue(result.isFailure)
        assertEquals(1, reporter.recorded.size)
        assertSame(boom, reporter.recorded.single().throwable)
        assertEquals("session calendar fetch failed", reporter.recorded.single().message)
    }

    @Test
    fun `failure path falls back to freshest persisted calendar`() = runTest {
        val reporter = FakeCrashReporter()
        val cache = FakeSessionCalendarCache().apply {
            replaceForSource(BillSource.PUBLISHED, SAMPLE_CALENDAR, fetchedAtMillis = 1L)
        }
        val repo = SessionCalendarRepository(
            ThrowingApi(IOException("offline")),
            reporter,
            cache,
        )

        val result = repo.getCalendar()

        assertTrue(result.isSuccess)
        assertEquals(SAMPLE_CALENDAR, result.getOrNull())
        // The network failure is still reported even though the UI got data.
        assertEquals(1, reporter.recorded.size)
    }

    @Test
    fun `publishByokCalendar replaces in-memory value and persists as BYOK`() = runTest {
        val cache = FakeSessionCalendarCache()
        val api = StubApi(SAMPLE_CALENDAR)
        val repo = SessionCalendarRepository(api, FakeCrashReporter(), cache)
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
        val repo = SessionCalendarRepository(api, FakeCrashReporter(), FakeSessionCalendarCache())

        repo.getCalendar()
        repo.getCalendar(forceRefresh = true)

        assertEquals(2, api.callCount)
    }

    private companion object {
        val SAMPLE_CALENDAR = SessionCalendar(
            generatedAt = "2026-05-05T12:00:00Z",
            source = SessionCalendarSource(
                house = "https://example.invalid/house",
                senate = "https://example.invalid/senate",
            ),
            chambers = mapOf(
                "house" to ChamberCalendar(sessionDays = listOf("2026-05-06")),
                "senate" to ChamberCalendar(sessionDays = listOf("2026-05-07")),
            ),
        )
    }

    private class StubApi(private val calendar: SessionCalendar) : BillsApi {
        var callCount = 0
        override suspend fun getCongressesIndex(): CongressesIndex = error("not used in this test")
        override suspend fun getBillsManifest(url: String): BillsManifest = error("not used in this test")
        override suspend fun getSessionCalendar(): SessionCalendar {
            callCount += 1
            return calendar
        }
    }

    private class ThrowingApi(private val throwable: Throwable) : BillsApi {
        override suspend fun getCongressesIndex(): CongressesIndex = error("not used in this test")
        override suspend fun getBillsManifest(url: String): BillsManifest = error("not used in this test")
        override suspend fun getSessionCalendar(): SessionCalendar = throw throwable
    }
}
