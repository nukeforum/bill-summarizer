package com.informedcitizen.domain.session

import com.informedcitizen.data.model.ChamberCalendar
import com.informedcitizen.data.model.SessionCalendar
import com.informedcitizen.data.model.SessionCalendarSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SessionStatusTest {

    @Test
    fun `both chambers in session today`() {
        val today = LocalDate.of(2026, 5, 6)
        val cal = calendarOf(
            house = listOf("2026-05-05", "2026-05-06", "2026-05-07"),
            senate = listOf("2026-05-06", "2026-05-08"),
        )

        val statuses = cal.statusOn(today)

        assertEquals(2, statuses.size)
        assertEquals(Chamber.HOUSE, statuses[0].chamber)
        assertEquals(Chamber.SENATE, statuses[1].chamber)
        assertTrue(statuses.all { it.inSessionToday })
    }

    @Test
    fun `only House in session today`() {
        val today = LocalDate.of(2026, 5, 6)
        val cal = calendarOf(
            house = listOf("2026-05-06"),
            senate = listOf("2026-05-08"),
        )

        val statuses = cal.statusOn(today)

        assertTrue(statuses.first { it.chamber == Chamber.HOUSE }.inSessionToday)
        val senate = statuses.first { it.chamber == Chamber.SENATE }
        assertFalse(senate.inSessionToday)
        assertEquals(LocalDate.of(2026, 5, 8), senate.nextSessionDay)
    }

    @Test
    fun `only Senate in session today`() {
        val today = LocalDate.of(2026, 5, 6)
        val cal = calendarOf(
            house = listOf("2026-05-08"),
            senate = listOf("2026-05-06"),
        )

        val statuses = cal.statusOn(today)

        assertTrue(statuses.first { it.chamber == Chamber.SENATE }.inSessionToday)
        val house = statuses.first { it.chamber == Chamber.HOUSE }
        assertFalse(house.inSessionToday)
        assertEquals(LocalDate.of(2026, 5, 8), house.nextSessionDay)
    }

    @Test
    fun `both out, both return same day`() {
        val today = LocalDate.of(2026, 5, 9)
        val cal = calendarOf(
            house = listOf("2026-05-12"),
            senate = listOf("2026-05-12"),
        )

        val statuses = cal.statusOn(today)

        assertTrue(statuses.none { it.inSessionToday })
        val expected = LocalDate.of(2026, 5, 12)
        assertTrue(statuses.all { it.nextSessionDay == expected })
    }

    @Test
    fun `both out, return on different days`() {
        val today = LocalDate.of(2026, 5, 9)
        val cal = calendarOf(
            house = listOf("2026-05-12"),
            senate = listOf("2026-05-19"),
        )

        val statuses = cal.statusOn(today)

        assertEquals(LocalDate.of(2026, 5, 12), statuses.first { it.chamber == Chamber.HOUSE }.nextSessionDay)
        assertEquals(LocalDate.of(2026, 5, 19), statuses.first { it.chamber == Chamber.SENATE }.nextSessionDay)
    }

    @Test
    fun `weekend with both out maps to next session day after the weekend`() {
        val today = LocalDate.of(2026, 5, 9)
        val cal = calendarOf(
            house = listOf("2026-05-08", "2026-05-11"),
            senate = listOf("2026-05-08", "2026-05-11"),
        )

        val statuses = cal.statusOn(today)

        assertTrue(statuses.none { it.inSessionToday })
        assertTrue(statuses.all { it.nextSessionDay == LocalDate.of(2026, 5, 11) })
    }

    @Test
    fun `federal holiday on a weekday with both out`() {
        val today = LocalDate.of(2026, 5, 25)
        val cal = calendarOf(
            house = listOf("2026-05-22", "2026-05-26"),
            senate = listOf("2026-05-22", "2026-05-26"),
        )

        val statuses = cal.statusOn(today)

        assertTrue(statuses.none { it.inSessionToday })
        assertTrue(statuses.all { it.nextSessionDay == LocalDate.of(2026, 5, 26) })
    }

    @Test
    fun `end of calendar for House but not Senate`() {
        val today = LocalDate.of(2026, 12, 30)
        val cal = calendarOf(
            house = listOf("2026-12-29"),
            senate = listOf("2026-12-29", "2027-01-05"),
        )

        val statuses = cal.statusOn(today)

        val house = statuses.first { it.chamber == Chamber.HOUSE }
        assertFalse(house.inSessionToday)
        assertNull("House calendar exhausted", house.nextSessionDay)

        val senate = statuses.first { it.chamber == Chamber.SENATE }
        assertEquals(LocalDate.of(2027, 1, 5), senate.nextSessionDay)
    }

    @Test
    fun `end of calendar for both chambers`() {
        val today = LocalDate.of(2027, 1, 10)
        val cal = calendarOf(
            house = listOf("2026-12-29"),
            senate = listOf("2026-12-29"),
        )

        val statuses = cal.statusOn(today)

        assertTrue(statuses.none { it.inSessionToday })
        assertTrue(statuses.all { it.nextSessionDay == null })
    }

    @Test
    fun `today is the very last session day for one chamber`() {
        val today = LocalDate.of(2026, 12, 29)
        val cal = calendarOf(
            house = listOf("2026-12-29"),
            senate = listOf("2026-12-29", "2027-01-05"),
        )

        val statuses = cal.statusOn(today)

        val house = statuses.first { it.chamber == Chamber.HOUSE }
        assertTrue(house.inSessionToday)
        assertNull("no future session day after today", house.nextSessionDay)
    }

    private fun calendarOf(house: List<String>, senate: List<String>): SessionCalendar =
        SessionCalendar(
            generatedAt = "2026-05-05T12:00:00Z",
            source = SessionCalendarSource(house = "h", senate = "s"),
            chambers = mapOf(
                "house" to ChamberCalendar(sessionDays = house),
                "senate" to ChamberCalendar(sessionDays = senate),
            ),
        )
}
