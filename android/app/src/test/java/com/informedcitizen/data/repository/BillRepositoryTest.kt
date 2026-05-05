package com.informedcitizen.data.repository

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.model.BillsManifest
import com.informedcitizen.testutil.InMemoryPreferencesDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private class StubApi(private val manifest: BillsManifest) : BillsApi {
        override suspend fun getBills(): BillsManifest = manifest
    }

    private class ThrowingApi(private val throwable: Throwable) : BillsApi {
        override suspend fun getBills(): BillsManifest = throw throwable
    }
}
