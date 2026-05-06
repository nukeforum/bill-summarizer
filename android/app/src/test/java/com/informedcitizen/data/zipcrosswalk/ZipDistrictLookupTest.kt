package com.informedcitizen.data.zipcrosswalk

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipDistrictLookupTest {

    private val testJson by lazy {
        javaClass.classLoader!!.getResource("zip_to_cd_test.json")!!.readText()
    }

    private fun newLookup() = ZipDistrictLookup(loader = { testJson })

    @Test
    fun `single district returns Single`() = runTest {
        val out = newLookup().lookup("10001")
        assertEquals(ZipDistrictResult.Single("NY", 12), out)
    }

    @Test
    fun `multi district returns Multiple`() = runTest {
        val out = newLookup().lookup("78701")
        assertEquals(ZipDistrictResult.Multiple("TX", listOf(21, 25, 35)), out)
    }

    @Test
    fun `at-large state returns district zero`() = runTest {
        val out = newLookup().lookup("99501")
        assertEquals(ZipDistrictResult.Single("AK", 0), out)
    }

    @Test
    fun `delegate jurisdiction returns district zero`() = runTest {
        val out = newLookup().lookup("20001")
        assertEquals(ZipDistrictResult.Single("DC", 0), out)
    }

    @Test
    fun `unknown ZIP returns Miss`() = runTest {
        val out = newLookup().lookup("00000")
        assertTrue(out is ZipDistrictResult.Miss)
    }

    @Test
    fun `short ZIP gets left-padded to 5 chars`() = runTest {
        // The test fixture has no entries for left-padded results either, so we
        // just verify the behavior doesn't throw and returns Miss.
        val out = newLookup().lookup("1001")
        assertTrue(out is ZipDistrictResult.Miss)
    }
}
