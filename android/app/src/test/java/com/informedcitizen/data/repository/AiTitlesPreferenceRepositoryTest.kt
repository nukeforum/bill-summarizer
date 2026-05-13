package com.informedcitizen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.informedcitizen.data.work.SummarizationScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTitlesPreferenceRepositoryTest {

    @Test fun `defaults are toggle-off and Progressive-50`() = runTest {
        val repo = AiTitlesPreferenceRepositoryImpl(StubStore())
        assertFalse(repo.enabled.first())
        assertEquals(SummarizationScope.Progressive(50), repo.scope.first())
    }

    @Test fun `setEnabled persists`() = runTest {
        val repo = AiTitlesPreferenceRepositoryImpl(StubStore())
        repo.setEnabled(true)
        assertTrue(repo.enabled.first())
    }

    @Test fun `setScope round-trips Progressive with custom cap`() = runTest {
        val repo = AiTitlesPreferenceRepositoryImpl(StubStore())
        repo.setScope(SummarizationScope.Progressive(123))
        assertEquals(SummarizationScope.Progressive(123), repo.scope.first())
    }

    @Test fun `setScope round-trips Floor and Recent60 and All`() = runTest {
        val repo = AiTitlesPreferenceRepositoryImpl(StubStore())
        repo.setScope(SummarizationScope.FloorActionOnly)
        assertEquals(SummarizationScope.FloorActionOnly, repo.scope.first())
        repo.setScope(SummarizationScope.Recent60Days)
        assertEquals(SummarizationScope.Recent60Days, repo.scope.first())
        repo.setScope(SummarizationScope.All)
        assertEquals(SummarizationScope.All, repo.scope.first())
    }

    @Test fun `empty store falls back to default Progressive`() = runTest {
        val repo = AiTitlesPreferenceRepositoryImpl(StubStore())
        assertEquals(SummarizationScope.Progressive(50), repo.scope.first())
    }
}

private class StubStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
