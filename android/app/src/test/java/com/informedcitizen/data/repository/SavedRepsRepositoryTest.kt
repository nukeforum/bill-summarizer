package com.informedcitizen.data.repository

import com.informedcitizen.testutil.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedRepsRepositoryTest {

    private fun newRepo() = SavedRepsRepository(InMemoryPreferencesDataStore())

    @Test
    fun `default is empty set`() = runTest {
        val repo = newRepo()
        assertTrue(repo.savedIds.first().isEmpty())
    }

    @Test
    fun `set persists and round-trips`() = runTest {
        val repo = newRepo()
        repo.set(setOf("G000595", "T000476", "C001056"))
        assertEquals(setOf("G000595", "T000476", "C001056"), repo.savedIds.first())
    }

    @Test
    fun `set with empty set clears the entry`() = runTest {
        val repo = newRepo()
        repo.set(setOf("G000595"))
        repo.set(emptySet())
        assertTrue(repo.savedIds.first().isEmpty())
    }

    @Test
    fun `forget clears saved ids`() = runTest {
        val repo = newRepo()
        repo.set(setOf("G000595", "T000476", "C001056"))
        repo.forget()
        assertTrue(repo.savedIds.first().isEmpty())
    }
}
