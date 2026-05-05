package com.informedcitizen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Minimal in-memory [DataStore] for unit tests — avoids file-system / OS locking issues. */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val _state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> get() = _state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        var updated: Preferences = emptyPreferences()
        _state.update { current ->
            transform(current).also { updated = it }
        }
        return updated
    }
}

class CrashReportingPreferenceRepositoryTest {

    private fun newDataStore(): DataStore<Preferences> = InMemoryPreferencesDataStore()

    @Test
    fun `default value is false`() = runTest {
        val repo = CrashReportingPreferenceRepository(newDataStore())
        assertFalse(repo.enabled.first())
    }

    @Test
    fun `set true is observed by enabled flow`() = runTest {
        val repo = CrashReportingPreferenceRepository(newDataStore())
        repo.set(true)
        assertTrue(repo.enabled.first())
    }

    @Test
    fun `set false after true round-trips`() = runTest {
        val repo = CrashReportingPreferenceRepository(newDataStore())
        repo.set(true)
        repo.set(false)
        assertEquals(false, repo.enabled.first())
    }
}
