package com.informedcitizen.data.repository

import com.informedcitizen.testutil.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationPreferenceRepositoryTest {

    private fun newRepo() = LocationPreferenceRepository(InMemoryPreferencesDataStore())

    @Test
    fun defaultIsNoLocation() = runTest {
        val repo = newRepo()
        val loc = repo.location.first()
        assertNull(loc.stateCode)
        assertNull(loc.district)
    }

    @Test
    fun setLocationRoundTrips() = runTest {
        val repo = newRepo()
        repo.set(stateCode = "TX", district = 21)
        val loc = repo.location.first()
        assertEquals("TX", loc.stateCode)
        assertEquals(21, loc.district)
    }

    @Test
    fun setLocationNullDistrictPersists() = runTest {
        val repo = newRepo()
        repo.set(stateCode = "DC", district = null)
        val loc = repo.location.first()
        assertEquals("DC", loc.stateCode)
        assertNull(loc.district)
    }

    @Test
    fun forgetClearsBoth() = runTest {
        val repo = newRepo()
        repo.set(stateCode = "TX", district = 21)
        repo.forget()
        val loc = repo.location.first()
        assertNull(loc.stateCode)
        assertNull(loc.district)
    }
}
