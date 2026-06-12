package com.informedcitizen.pipeline.fetch

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Mirrors Python `test_session_calendar_parsers.py`. The ICS fixture is
 * a trimmed inline version of the USHOR feed snapshot covering every
 * category the full fixture exercises, including the RFC 5545 folded
 * SUMMARY line.
 */
private val HOUSE_ICS_FIXTURE = """
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//USHOR//HouseCal//EN
BEGIN:VEVENT
UID:a
DTSTART;VALUE=DATE:20260217
SUMMARY:📥 Pro Forma Session
CATEGORIES:Pro Forma Session
END:VEVENT
BEGIN:VEVENT
UID:b
DTSTART;VALUE=DATE:20261214
SUMMARY:🏛️ Vote Day
CATEGORIES:Vote Day
END:VEVENT
BEGIN:VEVENT
UID:b-duplicate
DTSTART;VALUE=DATE:20261214
SUMMARY:🏛️ Vote Day
CATEGORIES:Vote Day
END:VEVENT
BEGIN:VEVENT
UID:c
DTSTART;VALUE=DATE:20260525
SUMMARY:🇺🇸 Memorial Day
CATEGORIES:Federal Holiday,Holiday
END:VEVENT
BEGIN:VEVENT
UID:d
DTSTART;VALUE=DATE:20260911
SUMMARY:🗓️ Rosh Hashanah Begins at Sundown
CATEGORIES:Non-federal Holiday,Holiday
END:VEVENT
BEGIN:VEVENT
UID:e
DTSTART;VALUE=DATE:20260202
SUMMARY:📥 Pro Forma Session Adjourning the First Session of the 119th C
 ongress
CATEGORIES:Pro Forma Session
END:VEVENT
BEGIN:VEVENT
UID:f
DTSTART;VALUE=DATE:20260106
SUMMARY:🏛️ Vote Day
CATEGORIES:Vote Day
END:VEVENT
END:VCALENDAR
""".trimIndent()

private val SENATE_XML_FIXTURE = """
<?xml version="1.0" encoding="UTF-8"?><schedule>
    <title>Tentative 2026 Legislative Schedule</title>
    <congress>119th</congress>
    <session>2</session>
    <year>2026</year>
    <preface><p>The <i>tentative schedule</i> for 2026 has been announced. <a href="/legislative/2026_schedule.xml">XML</a></p></preface>
    <dates>
       <date>
           <beginDate>2026-01-19</beginDate>
           <endDate>2026-01-23</endDate>
           <action>State Work Period</action>
           <note/>
           </date>
    <date>
           <beginDate>2026-08-10</beginDate>
           <endDate>2026-09-11</endDate>
           <action>State Work Period</action>
           <note>Labor Day - Sep 7</note>
           </date>
    </dates>
</schedule>
""".trimIndent()

class SessionCalendarParsersTest {
    // ------------------------------------------------------ house ICS

    @Test fun house_ics_returns_sorted_unique_dates() {
        val days = parseHouseIcs(HOUSE_ICS_FIXTURE)
        assertTrue(days.isNotEmpty(), "expected at least one Vote Day in fixture")
        assertEquals(days.sorted(), days)
        assertEquals(days.toSet().size, days.size)
    }

    @Test fun house_ics_excludes_non_voting_categories() {
        val days = parseHouseIcs(HOUSE_ICS_FIXTURE)
        assertFalse(LocalDate(2026, 2, 17) in days) // Pro Forma Session
        assertFalse(LocalDate(2026, 5, 25) in days) // Federal Holiday
        assertFalse(LocalDate(2026, 9, 11) in days) // Non-federal Holiday
        assertFalse(LocalDate(2026, 2, 2) in days) // Pro Forma w/ folded SUMMARY
    }

    @Test fun house_ics_includes_known_vote_day_and_dedupes() {
        val days = parseHouseIcs(HOUSE_ICS_FIXTURE)
        assertEquals(listOf(LocalDate(2026, 1, 6), LocalDate(2026, 12, 14)), days)
    }

    @Test fun house_ics_includes_added_vote_day() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:test
            DTSTART;VALUE=DATE:20260601
            SUMMARY:🏛️ Vote Day
            CATEGORIES:Added Vote Day
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        assertEquals(listOf(LocalDate(2026, 6, 1)), parseHouseIcs(ics))
    }

    @Test fun house_ics_excludes_canceled_vote_day() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20260602
            CATEGORIES:Canceled Vote Day
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        assertEquals(emptyList(), parseHouseIcs(ics))
    }

    @Test fun house_ics_handles_crlf_line_endings() {
        val ics = "BEGIN:VCALENDAR\r\n" +
            "BEGIN:VEVENT\r\n" +
            "DTSTART;VALUE=DATE:20260603\r\n" +
            "CATEGORIES:Vote Day\r\n" +
            "END:VEVENT\r\n" +
            "END:VCALENDAR\r\n"
        assertEquals(listOf(LocalDate(2026, 6, 3)), parseHouseIcs(ics))
    }

    @Test fun house_ics_empty_returns_empty_list() {
        assertEquals(emptyList(), parseHouseIcs(""))
    }

    // ----------------------------------------------------- senate XML

    @Test fun senate_xml_returns_year_and_sorted_unique_dates() {
        val (year, days) = parseSenateXml(SENATE_XML_FIXTURE)
        assertEquals(2026, year)
        assertTrue(days.isNotEmpty(), "expected at least one session day in fixture")
        assertEquals(days.sorted(), days)
        assertEquals(days.toSet().size, days.size)
    }

    @Test fun senate_xml_excludes_recess_periods() {
        val (_, days) = parseSenateXml(SENATE_XML_FIXTURE)
        // Jan 19-23, 2026 is a State Work Period — excluded.
        for (d in listOf(LocalDate(2026, 1, 19), LocalDate(2026, 1, 20), LocalDate(2026, 1, 23))) {
            assertFalse(d in days, "$d should be excluded")
        }
        // Aug 10 - Sep 11, 2026 is the long August recess — excluded.
        assertFalse(LocalDate(2026, 8, 24) in days)
    }

    @Test fun senate_xml_excludes_weekends() {
        val (_, days) = parseSenateXml(SENATE_XML_FIXTURE)
        for (d in days) {
            assertTrue(d.dayOfWeek.isoDayNumber <= 5, "$d is a weekend")
        }
    }

    @Test fun senate_xml_includes_a_known_session_day() {
        // May 11, 2026 is a Monday outside any recess in the fixture.
        val (_, days) = parseSenateXml(SENATE_XML_FIXTURE)
        assertTrue(LocalDate(2026, 5, 11) in days)
    }

    @Test fun senate_xml_single_day_recess_inclusive() {
        val xml = """<?xml version="1.0"?><schedule>
            <year>2026</year>
            <dates>
                <date>
                    <beginDate>2026-09-21</beginDate>
                    <endDate>2026-09-21</endDate>
                    <action/>
                    <note/>
                </date>
            </dates>
        </schedule>"""
        val (_, days) = parseSenateXml(xml)
        assertFalse(LocalDate(2026, 9, 21) in days)
        assertTrue(LocalDate(2026, 9, 22) in days) // next weekday is in session
    }

    @Test fun senate_xml_missing_year_throws() {
        val xml = """<?xml version="1.0"?><schedule><dates/></schedule>"""
        val e = assertFailsWith<IllegalArgumentException> { parseSenateXml(xml) }
        assertTrue("<year>" in (e.message ?: ""))
    }

    @Test fun senate_xml_end_before_begin_throws() {
        val xml = """<?xml version="1.0"?><schedule>
            <year>2026</year>
            <dates>
                <date>
                    <beginDate>2026-09-22</beginDate>
                    <endDate>2026-09-21</endDate>
                </date>
            </dates>
        </schedule>"""
        assertFailsWith<IllegalArgumentException> { parseSenateXml(xml) }
    }

    @Test fun senate_xml_no_recesses_yields_all_weekdays() {
        val xml = """<?xml version="1.0"?><schedule>
            <year>2026</year>
            <dates/>
        </schedule>"""
        val (year, days) = parseSenateXml(xml)
        assertEquals(2026, year)
        var expectedWeekdays = 0
        var cur = LocalDate(2026, 1, 1)
        repeat(365) {
            if (cur.dayOfWeek.isoDayNumber <= 5) expectedWeekdays++
            cur = cur.plus(1, DateTimeUnit.DAY)
        }
        assertEquals(expectedWeekdays, days.size)
    }
}
