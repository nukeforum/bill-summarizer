package com.informedcitizen.data.repository

import com.informedcitizen.testutil.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportingPreferenceRepositoryTest {

    @Test
    fun `default value is false`() = runTest {
        val repo = CrashReportingPreferenceRepository(InMemoryPreferencesDataStore())
        assertFalse(repo.enabled.first())
    }

    @Test
    fun `set true is observed by enabled flow`() = runTest {
        val repo = CrashReportingPreferenceRepository(InMemoryPreferencesDataStore())
        repo.set(true)
        assertTrue(repo.enabled.first())
    }

    @Test
    fun `set false after true round-trips`() = runTest {
        val repo = CrashReportingPreferenceRepository(InMemoryPreferencesDataStore())
        repo.set(true)
        repo.set(false)
        assertEquals(false, repo.enabled.first())
    }
}
