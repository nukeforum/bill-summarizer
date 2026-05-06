package com.informedcitizen.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BillTypeSlugTest {
    @Test fun `house bill slug`() = assertEquals("house-bill", billTypeToCongressGovSlug("hr"))
    @Test fun `senate bill slug`() = assertEquals("senate-bill", billTypeToCongressGovSlug("s"))
    @Test fun `joint resolutions`() {
        assertEquals("house-joint-resolution", billTypeToCongressGovSlug("hjres"))
        assertEquals("senate-joint-resolution", billTypeToCongressGovSlug("sjres"))
    }
    @Test fun `case insensitive input`() = assertEquals("house-bill", billTypeToCongressGovSlug("HR"))
    @Test fun `unknown type falls through to generic bill`() =
        assertEquals("bill", billTypeToCongressGovSlug("xyz"))
    @Test fun `congress gov url assembly`() = assertEquals(
        "https://www.congress.gov/bill/119th-congress/house-bill/1234",
        congressGovUrlFor("hr", "1234", 119),
    )
}
