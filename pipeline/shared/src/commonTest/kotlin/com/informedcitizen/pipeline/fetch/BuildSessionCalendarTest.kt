package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.http.PipelineHttpConfig
import com.informedcitizen.pipeline.http.SessionCalendarClient
import com.informedcitizen.pipeline.http.configurePipelineForTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val NOW_ISO = "2026-06-12T00:00:00Z"
private val TODAY = LocalDate(2026, 6, 12)

private val VALID_ICS = """
    BEGIN:VCALENDAR
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20260611
    CATEGORIES:Vote Day
    END:VEVENT
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20260202
    CATEGORIES:Vote Day
    END:VEVENT
    END:VCALENDAR
""".trimIndent()

private fun senateXml(year: Int): String = """
    <?xml version="1.0" encoding="UTF-8"?><schedule>
        <year>$year</year>
        <dates>
            <date>
                <beginDate>$year-08-10</beginDate>
                <endDate>$year-09-11</endDate>
                <action>State Work Period</action>
            </date>
        </dates>
    </schedule>
""".trimIndent()

/**
 * MockEngine routed by host: `votingdays.house.gov` serves the ICS,
 * `www.senate.gov` serves per-year schedules from [senateBodies]
 * (absent year → 404, simulating superseded/unpublished schedules).
 */
private fun mockClient(
    icsBody: String = VALID_ICS,
    icsStatus: HttpStatusCode = HttpStatusCode.OK,
    senateBodies: Map<Int, String> = mapOf(2026 to senateXml(2026)),
    senateStatus: HttpStatusCode = HttpStatusCode.OK,
): SessionCalendarClient {
    val http = HttpClient(MockEngine) {
        configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
        engine {
            addHandler { request ->
                when (request.url.host) {
                    "votingdays.house.gov" -> respond(icsBody, icsStatus)
                    "www.senate.gov" -> {
                        val year = Regex("(\\d{4})_schedule\\.xml")
                            .find(request.url.encodedPath)!!.groupValues[1].toInt()
                        val body = senateBodies[year]
                        if (body == null) {
                            respond("not found", HttpStatusCode.NotFound)
                        } else {
                            respond(body, senateStatus)
                        }
                    }
                    else -> error("unexpected host: ${request.url.host}")
                }
            }
        }
    }
    return SessionCalendarClient(http)
}

class BuildSessionCalendarTest {
    @Test fun happy_path_builds_calendar_with_sources_and_sorted_days() = runTest {
        val result = buildSessionCalendar(mockClient(), TODAY, NOW_ISO)
        val cal = result.calendar

        assertEquals(NOW_ISO, cal.generatedAt)
        assertEquals("https://votingdays.house.gov/voting-days.ics", cal.source.house)
        assertEquals("https://www.senate.gov/legislative/2026_schedule.xml", cal.source.senate)

        val house = cal.chambers.getValue("house").sessionDays
        assertEquals(listOf("2026-02-02", "2026-06-11"), house)

        val senate = cal.chambers.getValue("senate").sessionDays
        assertEquals(senate.sorted(), senate)
        assertTrue("2026-08-24" !in senate, "recess day leaked into session days")
        assertTrue("2026-06-12" in senate)
        assertEquals(listOf(2026), result.senateYears)
    }

    @Test fun merges_multiple_senate_years_when_available() = runTest {
        val client = mockClient(
            senateBodies = mapOf(2025 to senateXml(2025), 2026 to senateXml(2026)),
        )
        val result = buildSessionCalendar(client, TODAY, NOW_ISO)
        assertEquals(listOf(2025, 2026), result.senateYears)
        val senate = result.calendar.chambers.getValue("senate").sessionDays
        assertTrue("2025-06-12" in senate)
        assertTrue("2026-06-12" in senate)
    }

    @Test fun senate_year_mismatch_is_skipped() = runTest {
        // The 2026 URL serves a schedule whose <year> says 2025 —
        // invalid, skipped; with no other year available the build fails.
        val client = mockClient(senateBodies = mapOf(2026 to senateXml(2025)))
        assertFailsWith<SessionCalendarBuildException> {
            buildSessionCalendar(client, TODAY, NOW_ISO)
        }
    }

    @Test fun all_senate_years_missing_fails_with_no_days_message() = runTest {
        val client = mockClient(senateBodies = emptyMap())
        val e = assertFailsWith<SessionCalendarBuildException> {
            buildSessionCalendar(client, TODAY, NOW_ISO)
        }
        assertTrue("no session days" in (e.message ?: ""), "unexpected message: ${e.message}")
    }

    @Test fun senate_non_404_error_is_reported_as_last_error() = runTest {
        val client = mockClient(
            senateBodies = mapOf(2026 to "boom"),
            senateStatus = HttpStatusCode.InternalServerError,
        )
        val e = assertFailsWith<SessionCalendarBuildException> {
            buildSessionCalendar(client, TODAY, NOW_ISO)
        }
        assertTrue("last error" in (e.message ?: ""), "unexpected message: ${e.message}")
    }

    @Test fun empty_house_ics_fails() = runTest {
        val client = mockClient(icsBody = "BEGIN:VCALENDAR\nEND:VCALENDAR")
        val e = assertFailsWith<SessionCalendarBuildException> {
            buildSessionCalendar(client, TODAY, NOW_ISO)
        }
        assertTrue("no Vote Day" in (e.message ?: ""), "unexpected message: ${e.message}")
    }

    @Test fun store_writes_session_calendar_json_with_trailing_newline() = runTest {
        val result = buildSessionCalendar(mockClient(), TODAY, NOW_ISO)
        val fs = FakeFileSystem()
        val store = FileSessionCalendarStore(fs, "/out".toPath())
        val path = store.save(result.calendar)

        assertEquals("/out/session_calendar.json", path.toString())
        val text = fs.source(path).buffer().use { it.readUtf8() }
        assertTrue(text.endsWith("}\n"), "expected trailing newline")
        assertTrue("\"generated_at\": \"$NOW_ISO\"" in text)
        assertTrue("\"session_days\"" in text)
    }
}
