package com.informedcitizen.pipeline.fetch

import kotlinx.datetime.Instant
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Mirrors Python `test_check_freshness.py` — a fresh world is seeded,
 * then each test mutates exactly one axis to assert the corresponding
 * failure surfaces.
 */
private val NOW = Instant.parse("2026-06-01T12:00:00Z")
private val OUTPUT_DIR = "/data".toPath()
private val STATE_DIR = "/state".toPath()
private const val CONGRESS = 119

private class World {
    val fs = FakeFileSystem()

    init {
        fs.createDirectories(OUTPUT_DIR)
        fs.createDirectories(STATE_DIR)
        write(
            OUTPUT_DIR / "congress119_bills.json",
            """{"generated_at":"${(NOW - 6.hours)}","congress":119,"bills":[]}""",
        )
        write(
            OUTPUT_DIR / "members_119.json",
            """{"generated_at":"${(NOW - 1.days)}","congress":119,"members":[]}""",
        )
        writeCalendar(house = listOf("2026-06-01", "2026-07-31"), senate = listOf("2026-06-01", "2026-07-31"))
        write(
            STATE_DIR / "backfill_state.json",
            """{"active_congress":118,"active_offset":0,"queue":[118,117],
                "completed":[119],"last_run_at":"${(NOW - 6.hours)}"}""",
        )
    }

    fun write(path: Path, text: String) {
        fs.sink(path).buffer().use { it.writeUtf8(text) }
    }

    fun writeCalendar(house: List<String>, senate: List<String>) {
        val houseJson = house.joinToString(",") { "\"$it\"" }
        val senateJson = senate.joinToString(",") { "\"$it\"" }
        write(
            OUTPUT_DIR / "session_calendar.json",
            """{"generated_at":"$NOW","chambers":{
                "house":{"session_days":[$houseJson]},
                "senate":{"session_days":[$senateJson]}}}""",
        )
    }

    fun check(): List<String> = checkFreshness(fs, OUTPUT_DIR, STATE_DIR, CONGRESS, NOW)
}

class CheckFreshnessTest {
    @Test fun all_green() {
        assertEquals(emptyList(), World().check())
    }

    @Test fun stale_bills_manifest_flagged() {
        val w = World()
        w.write(
            OUTPUT_DIR / "congress119_bills.json",
            """{"generated_at":"${(NOW - 3.days)}","congress":119,"bills":[]}""",
        )
        val failures = w.check()
        assertTrue(failures.any { "bills:" in it && "older than" in it }, "$failures")
    }

    @Test fun missing_bills_manifest_flagged() {
        val w = World()
        w.fs.delete(OUTPUT_DIR / "congress119_bills.json")
        val failures = w.check()
        assertTrue(failures.any { "bills:" in it && "missing" in it }, "$failures")
    }

    @Test fun unparseable_generated_at_flagged() {
        val w = World()
        w.write(
            OUTPUT_DIR / "congress119_bills.json",
            """{"generated_at":"not-a-date","congress":119,"bills":[]}""",
        )
        val failures = w.check()
        assertTrue(failures.any { "bills:" in it && "no parseable" in it }, "$failures")
    }

    @Test fun stale_members_index_flagged() {
        val w = World()
        w.write(
            OUTPUT_DIR / "members_119.json",
            """{"generated_at":"${(NOW - 20.days)}","congress":119,"members":[]}""",
        )
        val failures = w.check()
        assertTrue(failures.any { "members:" in it && "older than" in it }, "$failures")
    }

    @Test fun calendar_low_lookahead_flagged_per_chamber() {
        val w = World()
        // House's last day is only 10 days out — below the 30-day
        // threshold; Senate is fine and must not be flagged.
        w.writeCalendar(house = listOf("2026-06-11"), senate = listOf("2026-07-31"))
        val failures = w.check()
        assertTrue(failures.any { "calendar: house" in it && "less than" in it }, "$failures")
        assertFalse(failures.any { "calendar: senate" in it }, "$failures")
    }

    @Test fun calendar_chamber_fully_past_flagged() {
        val w = World()
        w.writeCalendar(house = listOf("2024-01-01"), senate = listOf("2026-07-31"))
        val failures = w.check()
        assertTrue(failures.any { "calendar: house" in it && "no session days" in it }, "$failures")
    }

    @Test fun missing_calendar_flagged() {
        val w = World()
        w.fs.delete(OUTPUT_DIR / "session_calendar.json")
        val failures = w.check()
        assertTrue(failures.any { "calendar:" in it && "missing" in it }, "$failures")
    }

    @Test fun stale_backfill_cursor_flagged() {
        val w = World()
        w.write(
            STATE_DIR / "backfill_state.json",
            """{"active_congress":118,"active_offset":0,"queue":[118,117],
                "completed":[119],"last_run_at":"${(NOW - 5.days)}"}""",
        )
        val failures = w.check()
        assertTrue(failures.any { "backfill:" in it && "older than" in it }, "$failures")
    }

    @Test fun empty_backfill_queue_is_not_a_failure() {
        // When the backfill queue is exhausted the cursor stays null
        // and last_run_at can legitimately be ancient. Don't flag it.
        val w = World()
        w.write(
            STATE_DIR / "backfill_state.json",
            """{"active_congress":null,"active_offset":0,"queue":[],
                "completed":[119,118,117],"last_run_at":"${(NOW - 400.days)}"}""",
        )
        assertEquals(emptyList(), w.check())
    }

    @Test fun missing_state_file_is_not_a_failure() {
        val w = World()
        w.fs.delete(STATE_DIR / "backfill_state.json")
        assertEquals(emptyList(), w.check())
    }
}
