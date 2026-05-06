package com.informedcitizen.data.repository

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.model.Action
import com.informedcitizen.data.model.Bill
import com.informedcitizen.data.model.BillsManifest
import com.informedcitizen.data.model.Outcome
import com.informedcitizen.data.model.SessionCalendar
import com.informedcitizen.data.model.Sponsor
import com.informedcitizen.testutil.InMemoryPreferencesDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        )
        repo.getBills(forceRefresh = true)
        assertTrue(repo.containsBillId("hr1234-119"))
        assertTrue(repo.containsBillId("s5-119"))
        assertFalse(repo.containsBillId("hr9999-119"))
    }

    @Test
    fun `containsBillId returns false before load`() {
        val repo = BillRepository(
            api = StubApi(BillsManifest(generatedAt = "x", congress = 119, bills = emptyList())),
            dataStore = InMemoryPreferencesDataStore(),
            crashReporter = FakeCrashReporter(),
        )
        assertFalse(repo.containsBillId("hr1-119"))
    }

    private class StubApi(private val manifest: BillsManifest) : BillsApi {
        override suspend fun getBills(): BillsManifest = manifest
        override suspend fun getSessionCalendar(): SessionCalendar = error("not used in this test")
    }

    private class ThrowingApi(private val throwable: Throwable) : BillsApi {
        override suspend fun getBills(): BillsManifest = throw throwable
        override suspend fun getSessionCalendar(): SessionCalendar = error("not used in this test")
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
