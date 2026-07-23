package com.informedcitizen.ui.calendar

import com.informedcitizen.pipeline.model.RegistrationDeadline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RegistrationDeadlineFormatterTest {

    private val today = LocalDate.of(2026, 3, 25)

    private fun open(display: RegistrationDeadlineDisplay?): RegistrationDeadlineDisplay.Open =
        display as RegistrationDeadlineDisplay.Open

    private fun closed(display: RegistrationDeadlineDisplay?): RegistrationDeadlineDisplay.Closed =
        display as RegistrationDeadlineDisplay.Closed

    // -- Omitted data stays silent --------------------------------------------

    @Test
    fun `null deadline renders nothing`() {
        assertNull(registrationDeadlineDisplay(null, today))
    }

    @Test
    fun `deadline with no dates renders nothing even with same-day flag`() {
        val deadline = RegistrationDeadline(sameDay = true, source = "https://example.gov")
        assertNull(registrationDeadlineDisplay(deadline, today))
    }

    @Test
    fun `unparseable date is dropped and blanks the block when it is the only field`() {
        val deadline = RegistrationDeadline(online = "not-a-date")
        assertNull(registrationDeadlineDisplay(deadline, today))
    }

    // -- Boundary: day-before, day-of, day-after ------------------------------

    @Test
    fun `deadline in the future is open with a days-remaining headline`() {
        val deadline = RegistrationDeadline(online = "2026-04-06")
        val display = open(registrationDeadlineDisplay(deadline, today))

        assertEquals(12L, display.headline.daysUntil)
        assertFalse(display.urgent)
        assertEquals("Registration closes in 12 days (Apr 6)", registrationOpenHeadline(display))
    }

    @Test
    fun `deadline one day out reads 'in 1 day', not 'tomorrow'`() {
        val deadline = RegistrationDeadline(online = "2026-03-26")
        val display = open(registrationDeadlineDisplay(deadline, today))

        assertEquals("Registration closes in 1 day (Mar 26)", registrationOpenHeadline(display))
        assertTrue(display.urgent)
    }

    @Test
    fun `deadline on today reads 'closes today' and is still open`() {
        val deadline = RegistrationDeadline(online = "2026-03-25")
        val display = open(registrationDeadlineDisplay(deadline, today))

        assertEquals(0L, display.headline.daysUntil)
        assertEquals("Registration closes today (Mar 25)", registrationOpenHeadline(display))
    }

    @Test
    fun `deadline the day before today is closed`() {
        val deadline = RegistrationDeadline(online = "2026-03-24")
        val display = closed(registrationDeadlineDisplay(deadline, today))

        assertEquals(listOf(RegistrationMethod.ONLINE), display.closedMethods)
        assertEquals(
            "Online registration has closed for this election.",
            registrationClosedText(display),
        )
    }

    @Test
    fun `deadline within seven days is urgent`() {
        val deadline = RegistrationDeadline(online = "2026-04-01")
        val display = open(registrationDeadlineDisplay(deadline, today))
        assertEquals(7L, display.headline.daysUntil)
        assertTrue(display.urgent)
    }

    // -- Same-day registration ------------------------------------------------

    @Test
    fun `closed state notes same-day registration when the flag is true`() {
        val deadline = RegistrationDeadline(online = "2026-03-01", sameDay = true)
        val display = closed(registrationDeadlineDisplay(deadline, today))

        assertTrue(display.sameDay)
        val description = registrationDeadlineDescription(display)
        assertTrue(description.contains("has closed for this election."))
        assertTrue(description.contains(SAME_DAY_REGISTRATION_NOTE))
    }

    @Test
    fun `closed state without same-day flag has no polls note`() {
        val deadline = RegistrationDeadline(online = "2026-03-01", sameDay = false)
        val display = closed(registrationDeadlineDisplay(deadline, today))

        assertFalse(display.sameDay)
        assertFalse(registrationDeadlineDescription(display).contains(SAME_DAY_REGISTRATION_NOTE))
    }

    // -- Differing method dates -----------------------------------------------

    @Test
    fun `multiple methods pick the earliest upcoming as the headline and list all in order`() {
        val deadline = RegistrationDeadline(
            online = "2026-04-06",
            byMail = "2026-04-01",
            inPerson = "2026-05-05",
        )
        val display = open(registrationDeadlineDisplay(deadline, today))

        assertEquals(RegistrationMethod.BY_MAIL, display.headline.method)
        assertEquals(
            listOf(RegistrationMethod.ONLINE, RegistrationMethod.BY_MAIL, RegistrationMethod.IN_PERSON),
            display.methods.map { it.method },
        )
        assertEquals(
            listOf("Online: Apr 6", "By mail: Apr 1", "In person: May 5"),
            display.methods.map(::registrationDetailLine),
        )
        assertNull(registrationPartialClosedText(display))
    }

    @Test
    fun `in-person still open while online has closed enumerates the closed method and keeps counting down`() {
        val deadline = RegistrationDeadline(
            online = "2026-03-01",
            inPerson = "2026-05-05",
        )
        val display = open(registrationDeadlineDisplay(deadline, today))

        assertEquals(RegistrationMethod.IN_PERSON, display.headline.method)
        assertEquals(listOf(RegistrationMethod.ONLINE), display.closedMethods)
        assertEquals("Online registration has closed.", registrationPartialClosedText(display))
        assertTrue(registrationOpenHeadline(display).startsWith("Registration closes in "))
    }

    @Test
    fun `all methods closed enumerates each with an oxford comma`() {
        val deadline = RegistrationDeadline(
            online = "2026-03-01",
            byMail = "2026-03-02",
            inPerson = "2026-03-03",
        )
        val display = closed(registrationDeadlineDisplay(deadline, today))
        assertEquals(
            "Online, by mail, and in person registration has closed for this election.",
            registrationClosedText(display),
        )
    }

    // -- Accessibility --------------------------------------------------------

    @Test
    fun `open description merges countdown, spelled-out date, and methods`() {
        val deadline = RegistrationDeadline(online = "2026-04-06", byMail = "2026-04-06")
        val display = open(registrationDeadlineDisplay(deadline, today))
        assertEquals(
            "Registration closes in 12 days, April 6, online and by mail.",
            registrationDeadlineDescription(display),
        )
    }

    @Test
    fun `urgent open description prepends Warning`() {
        val deadline = RegistrationDeadline(online = "2026-03-28")
        val display = open(registrationDeadlineDisplay(deadline, today))
        assertTrue(registrationDeadlineDescription(display).startsWith("Warning: "))
    }
}
