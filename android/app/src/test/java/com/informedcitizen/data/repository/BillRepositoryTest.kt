package com.informedcitizen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.model.BillsManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class InMemoryPreferencesDataStoreForBillRepo : DataStore<Preferences> {
    private val _state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> get() = _state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        var updated: Preferences = emptyPreferences()
        _state.update { current -> transform(current).also { updated = it } }
        return updated
    }
}

class BillRepositoryTest {

    @Test
    fun `success path does not call CrashReporter`() = runTest {
        val reporter = FakeCrashReporter()
        val repo = BillRepository(
            api = StubApi(BillsManifest(generatedAt = "2026-01-01", congress = 119, bills = emptyList())),
            dataStore = InMemoryPreferencesDataStoreForBillRepo(),
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
            dataStore = InMemoryPreferencesDataStoreForBillRepo(),
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
