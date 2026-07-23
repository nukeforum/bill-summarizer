package com.informedcitizen.ui.billslist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillStatusFilterTest {

    @Test fun `ALL applies no status predicate`() {
        assertNull(BillStatusFilter.ALL.sqlStatus)
    }

    @Test fun `lifecycle filters map to the #39 wire status strings`() {
        assertEquals("introduced", BillStatusFilter.INTRODUCED.sqlStatus)
        assertEquals("in_committee", BillStatusFilter.IN_COMMITTEE.sqlStatus)
        assertEquals("reported", BillStatusFilter.REPORTED.sqlStatus)
    }

    @Test fun `default is ALL and every entry has a display name`() {
        assertEquals(BillStatusFilter.ALL, BillStatusFilter.entries.first())
        assertEquals(
            emptyList<BillStatusFilter>(),
            BillStatusFilter.entries.filter { it.displayName.isBlank() },
        )
    }
}
