package com.informedcitizen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.informedcitizen.testutil.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RepsContactPreferenceRepositoryTest {

    @Test
    fun `default value is false`() = runTest {
        val repo = RepsContactPreferenceRepository(InMemoryPreferencesDataStore())
        assertEquals(false, repo.hasSeenWebsiteFallbackDialog.first())
    }

    @Test
    fun `markWebsiteFallbackDialogSeen flips the flow to true`() = runTest {
        val repo = RepsContactPreferenceRepository(InMemoryPreferencesDataStore())
        repo.markWebsiteFallbackDialogSeen()
        assertEquals(true, repo.hasSeenWebsiteFallbackDialog.first())
    }

    @Test
    fun `read failure emits false instead of throwing`() = runTest {
        val failing = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                throw java.io.IOException("simulated read failure")
            }
            override suspend fun updateData(
                transform: suspend (Preferences) -> Preferences,
            ): Preferences = error("not used")
        }
        val repo = RepsContactPreferenceRepository(failing)
        assertEquals(false, repo.hasSeenWebsiteFallbackDialog.first())
    }
}
