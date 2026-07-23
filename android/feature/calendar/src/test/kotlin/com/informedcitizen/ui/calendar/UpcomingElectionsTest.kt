package com.informedcitizen.ui.calendar

import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.ElectionEvent
import com.informedcitizen.pipeline.model.ElectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UpcomingElectionsTest {

    private fun event(
        state: String,
        date: String,
        type: ElectionType,
        year: Int = 2026,
    ) = ElectionEvent(state = state, date = date, type = type, electionYear = year)

    private fun calendar(vararg events: ElectionEvent) =
        ElectionCalendar(generatedAt = "2026-01-01T00:00:00Z", source = "test", elections = events.toList())

    private val today = LocalDate.of(2026, 3, 1)

    @Test
    fun `state known returns that state's rows plus nationwide, soonest first`() {
        val cal = calendar(
            event(ElectionEvent.NATIONWIDE, "2026-11-03", ElectionType.GENERAL),
            event("TX", "2026-03-03", ElectionType.PRIMARY),
            event("OH", "2026-05-05", ElectionType.PRIMARY),
        )
        val result = upcomingElections(cal, today, stateCode = "TX")

        assertEquals(listOf("TX", "US"), result.map { it.event.state })
        assertEquals(2L, result[0].daysUntil)
        assertTrue(result[1].isNationwide)
    }

    @Test
    fun `no state yields nationwide only`() {
        val cal = calendar(
            event(ElectionEvent.NATIONWIDE, "2026-11-03", ElectionType.GENERAL),
            event("TX", "2026-03-03", ElectionType.PRIMARY),
        )
        val result = upcomingElections(cal, today, stateCode = null)

        assertEquals(1, result.size)
        assertTrue(result.single().isNationwide)
    }

    @Test
    fun `blank state is treated as no state`() {
        val cal = calendar(
            event(ElectionEvent.NATIONWIDE, "2026-11-03", ElectionType.GENERAL),
            event("TX", "2026-03-03", ElectionType.PRIMARY),
        )
        assertEquals(1, upcomingElections(cal, today, stateCode = "  ").size)
    }

    @Test
    fun `state match is case-insensitive`() {
        val cal = calendar(event("TX", "2026-03-03", ElectionType.PRIMARY))
        assertEquals(1, upcomingElections(cal, today, stateCode = "tx").size)
    }

    @Test
    fun `past events are excluded`() {
        val cal = calendar(
            event("TX", "2026-02-01", ElectionType.PRIMARY),
            event(ElectionEvent.NATIONWIDE, "2026-11-03", ElectionType.GENERAL),
        )
        val result = upcomingElections(cal, today, stateCode = "TX")
        assertEquals(listOf("US"), result.map { it.event.state })
    }

    @Test
    fun `event on today is included and reads Today`() {
        val cal = calendar(event("TX", "2026-03-01", ElectionType.PRIMARY))
        val result = upcomingElections(cal, today, stateCode = "TX")
        assertEquals(0L, result.single().daysUntil)
        assertEquals("Today", result.single().countdownText)
    }

    @Test
    fun `countdown reads Tomorrow and in N days`() {
        val cal = calendar(
            event("TX", "2026-03-02", ElectionType.PRIMARY),
            event("TX", "2026-03-08", ElectionType.PRIMARY_RUNOFF),
        )
        val result = upcomingElections(cal, today, stateCode = "TX")
        assertEquals("Tomorrow", result[0].countdownText)
        assertEquals("in 7 days", result[1].countdownText)
    }

    @Test
    fun `unparseable date row is skipped without blanking the surface`() {
        val cal = calendar(
            event("TX", "not-a-date", ElectionType.PRIMARY),
            event(ElectionEvent.NATIONWIDE, "2026-11-03", ElectionType.GENERAL),
        )
        val result = upcomingElections(cal, today, stateCode = "TX")
        assertEquals(listOf("US"), result.map { it.event.state })
    }

    @Test
    fun `limit caps the result`() {
        val cal = calendar(
            event(ElectionEvent.NATIONWIDE, "2026-11-03", ElectionType.GENERAL),
            event("TX", "2026-03-03", ElectionType.PRIMARY),
            event("TX", "2026-04-03", ElectionType.PRIMARY_RUNOFF),
            event("TX", "2026-05-03", ElectionType.PRIMARY),
        )
        assertEquals(2, upcomingElections(cal, today, stateCode = "TX", limit = 2).size)
    }

    @Test
    fun `nextElection returns the soonest`() {
        val cal = calendar(
            event(ElectionEvent.NATIONWIDE, "2026-11-03", ElectionType.GENERAL),
            event("TX", "2026-03-03", ElectionType.PRIMARY),
        )
        val next = nextElection(cal, today, stateCode = "TX")
        assertEquals("TX", next?.event?.state)
        assertFalse(next!!.isNationwide)
    }

    @Test
    fun `nextElection is null when nothing upcoming`() {
        val cal = calendar(event("TX", "2026-02-01", ElectionType.PRIMARY))
        assertNull(nextElection(cal, today, stateCode = "TX"))
    }

    @Test
    fun `electionTypeLabel maps every type including unknown`() {
        assertEquals("General election", electionTypeLabel(ElectionType.GENERAL))
        assertEquals("Primary", electionTypeLabel(ElectionType.PRIMARY))
        assertEquals("Primary runoff", electionTypeLabel(ElectionType.PRIMARY_RUNOFF))
        assertEquals("Election", electionTypeLabel(ElectionType.UNKNOWN))
    }

    @Test
    fun `electionRowLabel omits state for nationwide and prefixes state otherwise`() {
        val cal = calendar(
            event(ElectionEvent.NATIONWIDE, "2026-11-03", ElectionType.GENERAL),
            event("TX", "2026-03-03", ElectionType.PRIMARY),
        )
        val rows = upcomingElections(cal, today, stateCode = "TX")
        val tx = rows.first { it.event.state == "TX" }
        val us = rows.first { it.isNationwide }

        assertEquals("TX Primary", electionRowLabel(tx))
        assertEquals("General election", electionRowLabel(us))
    }

    @Test
    fun `electionRowDescription merges label date and countdown`() {
        val cal = calendar(event("TX", "2026-03-03", ElectionType.PRIMARY))
        val row = upcomingElections(cal, today, stateCode = "TX").single()

        assertEquals("TX Primary, Tue Mar 3, 2026, in 2 days", electionRowDescription(row, "Tue Mar 3, 2026"))
    }
}
