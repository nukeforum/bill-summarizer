package com.informedcitizen.ui.billslist

import com.informedcitizen.domain.session.Chamber
import com.informedcitizen.domain.session.ChamberStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate


class SessionStatusFormatterTest {

    @Test
    fun `both in session`() {
        val statuses = listOf(
            ChamberStatus(Chamber.HOUSE, inSessionToday = true, nextSessionDay = null),
            ChamberStatus(Chamber.SENATE, inSessionToday = true, nextSessionDay = null),
        )
        assertEquals(
            "House and Senate in session today",
            formatSessionStatusLine(statuses),
        )
    }

    @Test
    fun `only House in session, Senate returns Monday`() {
        val statuses = listOf(
            ChamberStatus(Chamber.HOUSE, inSessionToday = true, nextSessionDay = null),
            ChamberStatus(Chamber.SENATE, inSessionToday = false, nextSessionDay = LocalDate.of(2026, 5, 11)),
        )
        assertEquals(
            "House in session — Senate returns Mon, May 11",
            formatSessionStatusLine(statuses),
        )
    }

    @Test
    fun `only Senate in session`() {
        val statuses = listOf(
            ChamberStatus(Chamber.HOUSE, inSessionToday = false, nextSessionDay = LocalDate.of(2026, 5, 11)),
            ChamberStatus(Chamber.SENATE, inSessionToday = true, nextSessionDay = null),
        )
        assertEquals(
            "Senate in session — House returns Mon, May 11",
            formatSessionStatusLine(statuses),
        )
    }

    @Test
    fun `both out, same return day`() {
        val statuses = listOf(
            ChamberStatus(Chamber.HOUSE, inSessionToday = false, nextSessionDay = LocalDate.of(2026, 5, 11)),
            ChamberStatus(Chamber.SENATE, inSessionToday = false, nextSessionDay = LocalDate.of(2026, 5, 11)),
        )
        assertEquals(
            "Both chambers on recess — they return Mon, May 11",
            formatSessionStatusLine(statuses),
        )
    }

    @Test
    fun `both out, different return days`() {
        val statuses = listOf(
            ChamberStatus(Chamber.HOUSE, inSessionToday = false, nextSessionDay = LocalDate.of(2026, 5, 12)),
            ChamberStatus(Chamber.SENATE, inSessionToday = false, nextSessionDay = LocalDate.of(2026, 5, 19)),
        )
        assertEquals(
            "Both chambers on recess — House returns May 12, Senate May 19",
            formatSessionStatusLine(statuses),
        )
    }

    @Test
    fun `end of calendar for both chambers`() {
        val statuses = listOf(
            ChamberStatus(Chamber.HOUSE, inSessionToday = false, nextSessionDay = null),
            ChamberStatus(Chamber.SENATE, inSessionToday = false, nextSessionDay = null),
        )
        assertNull(formatSessionStatusLine(statuses))
    }

    @Test
    fun `end of calendar for one chamber falls back to null`() {
        val statuses = listOf(
            ChamberStatus(Chamber.HOUSE, inSessionToday = true, nextSessionDay = null),
            ChamberStatus(Chamber.SENATE, inSessionToday = false, nextSessionDay = null),
        )
        assertNull(formatSessionStatusLine(statuses))
    }
}
