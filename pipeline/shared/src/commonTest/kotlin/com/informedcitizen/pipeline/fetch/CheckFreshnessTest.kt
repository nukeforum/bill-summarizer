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
        write(
            OUTPUT_DIR / "congress119_votes.json",
            """{"congress":119,"generated_at":"${(NOW - 6.hours)}","vote_count":0,"votes":[]}""",
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

    fun writeElection(elections: String) {
        write(
            OUTPUT_DIR / "election_calendar.json",
            """{"generated_at":"$NOW","source":"test","elections":[$elections]}""",
        )
    }

    /**
     * Publish a shard index plus one shard file per entry. Each [Shard]
     * carries [Shard.count] empty-ish bills so the index/file counts line up
     * unless [Shard.actual] deliberately skews one. [totalBills] overrides the
     * index's `total_bills`, defaulting to the sum of declared counts.
     */
    fun writeShardSet(shards: List<Shard>, totalBills: Int? = null) {
        val entries = shards.joinToString(",") { s ->
            """{"page":${s.page},"path":"${s.path}","count":${s.count},
                "first_action_date":null,"last_action_date":null}"""
        }
        for (s in shards) {
            val bills = (0 until (s.actual ?: s.count)).joinToString(",") { """{"id":"b$it"}""" }
            write(
                OUTPUT_DIR / s.path,
                """{"generated_at":"$NOW","congress":119,"votes_coverage":false,"bills":[$bills]}""",
            )
        }
        val total = totalBills ?: shards.sumOf { it.count }
        write(
            OUTPUT_DIR / "congress119_bills_index.json",
            """{"generated_at":"$NOW","congress":119,"page_size":500,
                "total_bills":$total,"votes_coverage":false,"shards":[$entries]}""",
        )
    }

    fun check(): List<String> = checkFreshness(fs, OUTPUT_DIR, STATE_DIR, CONGRESS, NOW)
}

private data class Shard(val page: Int, val path: String, val count: Int, val actual: Int? = null)

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

    @Test fun stale_votes_index_flagged() {
        val w = World()
        w.write(
            OUTPUT_DIR / "congress119_votes.json",
            """{"congress":119,"generated_at":"${(NOW - 3.days)}","vote_count":0,"votes":[]}""",
        )
        val failures = w.check()
        assertTrue(failures.any { "votes:" in it && "older than" in it }, "$failures")
    }

    @Test fun missing_votes_index_flagged() {
        val w = World()
        w.fs.delete(OUTPUT_DIR / "congress119_votes.json")
        val failures = w.check()
        assertTrue(failures.any { "votes:" in it && "missing" in it }, "$failures")
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

    @Test fun absent_shard_index_is_not_a_failure() {
        // The fresh world never seeds a shard index; its absence is tolerated
        // during the dual-publish transition (issue #40).
        val w = World()
        assertFalse(w.fs.exists(OUTPUT_DIR / "congress119_bills_index.json"))
        assertFalse(w.check().any { "shards:" in it }, "${w.check()}")
    }

    @Test fun consistent_shard_set_is_green() {
        val w = World()
        w.writeShardSet(
            listOf(
                Shard(page = 1, path = "congress119_bills_p001.json", count = 3),
                Shard(page = 2, path = "congress119_bills_p002.json", count = 1),
            ),
        )
        assertFalse(w.check().any { "shards:" in it }, "${w.check()}")
    }

    @Test fun missing_shard_file_flagged() {
        val w = World()
        w.writeShardSet(listOf(Shard(page = 1, path = "congress119_bills_p001.json", count = 2)))
        w.fs.delete(OUTPUT_DIR / "congress119_bills_p001.json")
        val failures = w.check()
        assertTrue(failures.any { "shards:" in it && "missing or unreadable shard" in it }, "$failures")
    }

    @Test fun shard_count_mismatch_flagged() {
        // Index lists 5 but the shard file only holds 2 bills.
        val w = World()
        w.writeShardSet(listOf(Shard(page = 1, path = "congress119_bills_p001.json", count = 5, actual = 2)))
        val failures = w.check()
        assertTrue(failures.any { "holds 2 bills but the index lists 5" in it }, "$failures")
    }

    @Test fun total_bills_mismatch_flagged() {
        // Shard counts sum to 3 but total_bills claims 99.
        val w = World()
        w.writeShardSet(
            listOf(Shard(page = 1, path = "congress119_bills_p001.json", count = 3)),
            totalBills = 99,
        )
        val failures = w.check()
        assertTrue(failures.any { "total_bills=99" in it && "sum of shard counts 3" in it }, "$failures")
    }

    @Test fun orphaned_shard_file_flagged() {
        val w = World()
        w.writeShardSet(listOf(Shard(page = 1, path = "congress119_bills_p001.json", count = 2)))
        // A stale shard from a prior larger run the index no longer references.
        w.write(
            OUTPUT_DIR / "congress119_bills_p002.json",
            """{"generated_at":"$NOW","congress":119,"votes_coverage":false,"bills":[]}""",
        )
        val failures = w.check()
        assertTrue(failures.any { "orphaned shard congress119_bills_p002.json" in it }, "$failures")
    }

    @Test fun absent_election_calendar_is_not_a_failure() {
        // The fresh world never seeds an election calendar; its absence is
        // tolerated until the workflow is live (issue #23).
        val w = World()
        assertFalse(w.check().any { "election:" in it }, "${w.check()}")
    }

    @Test fun election_horizon_lapse_flagged() {
        val w = World()
        w.writeElection("""{"state":"US","date":"2024-11-05","type":"general","election_year":2024}""")
        val failures = w.check()
        assertTrue(failures.any { "election:" in it && "no election on or after" in it }, "$failures")
    }

    @Test fun passed_registration_deadline_on_upcoming_election_flagged() {
        // A registration deadline dated before today, for an election still in
        // the future, is stale by definition and must trip the check (#35).
        val w = World()
        w.writeElection(
            """{"state":"GA","date":"2026-07-01","type":"primary","election_year":2026,
                "registration":{"online":"2026-05-27","source":"https://sos.ga.gov"}}""",
        )
        val failures = w.check()
        assertTrue(failures.any { "election: GA" in it && "registration" in it && "stale" in it }, "$failures")
    }

    @Test fun future_registration_deadline_on_upcoming_election_ok() {
        val w = World()
        w.writeElection(
            """{"state":"GA","date":"2026-07-01","type":"primary","election_year":2026,
                "registration":{"online":"2026-06-11"}}""",
        )
        assertFalse(w.check().any { "registration" in it }, "${w.check()}")
    }

    @Test fun passed_registration_deadline_on_past_election_ignored() {
        // A past election is out of the lookahead window; its (also-past)
        // deadline is not flagged. The horizon check owns "no upcoming
        // election", not this per-event deadline check.
        val w = World()
        w.writeElection(
            """{"state":"US","date":"2026-11-03","type":"general","election_year":2026},
               {"state":"TX","date":"2026-05-01","type":"primary","election_year":2026,
                "registration":{"online":"2026-04-01"}}""",
        )
        assertFalse(w.check().any { "registration" in it }, "${w.check()}")
    }

    @Test fun same_day_registration_is_exempt() {
        // same_day carries no date, so an upcoming election that only advertises
        // same-day registration never trips the deadline check.
        val w = World()
        w.writeElection(
            """{"state":"MN","date":"2026-07-01","type":"primary","election_year":2026,
                "registration":{"same_day":true}}""",
        )
        assertFalse(w.check().any { "registration" in it }, "${w.check()}")
    }
}
