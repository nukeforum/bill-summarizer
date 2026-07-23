package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.ElectionEvent
import com.informedcitizen.pipeline.model.ElectionType
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parity shadow tests for [buildElectionCalendar] / [validateCuratedPrimary] /
 * [loadCuratedPrimaries] / [FileElectionCalendarStore], mirroring Python
 * `tests/test_election_calendar.py` (issue #23).
 */
private const val GEN_AT = "2026-07-22T00:00:00Z"
private val TODAY = LocalDate(2026, 7, 22)

private fun primary(
    state: String,
    date: String,
    year: Int,
    type: String = "primary",
    source: String? = null,
) = CuratedPrimary(state = state, date = date, type = type, electionYear = year, source = source)

class BuildElectionCalendarTest {

    // --- calendar assembly -------------------------------------------------

    @Test
    fun general_row_always_present_and_statutory() {
        val cal = buildElectionCalendar(TODAY, GEN_AT)
        val general = cal.elections.filter { it.type == ElectionType.GENERAL }
        assertEquals(1, general.size)
        val row = general.single()
        assertEquals(ElectionEvent.NATIONWIDE, row.state)
        assertEquals("US", row.state)
        assertEquals("2026-11-03", row.date)
        assertEquals(2026, row.electionYear)
        assertNull(row.source, "general row falls back to calendar-level source")
    }

    @Test
    fun top_level_shape_matches_wire_contract() {
        val cal = buildElectionCalendar(TODAY, GEN_AT)
        assertEquals(GEN_AT, cal.generatedAt)
        assertTrue(cal.source.isNotEmpty())
        assertEquals(DEFAULT_ELECTION_SOURCE, cal.source)
    }

    @Test
    fun valid_curated_primary_passes_through_with_source() {
        val row = primary("TX", "2026-03-03", 2026, source = "Texas Election Code §41.007")
        val cal = buildElectionCalendar(TODAY, GEN_AT, curatedPrimaries = listOf(row))
        val tx = cal.elections.single { it.state == "TX" }
        assertEquals(
            ElectionEvent("TX", "2026-03-03", ElectionType.PRIMARY, 2026, "Texas Election Code §41.007"),
            tx,
        )
    }

    @Test
    fun events_sorted_by_date_then_state() {
        val primaries = listOf(
            primary("PA", "2026-05-19", 2026),
            primary("OR", "2026-05-19", 2026),
            primary("TX", "2026-03-03", 2026),
        )
        val cal = buildElectionCalendar(TODAY, GEN_AT, curatedPrimaries = primaries)
        val ordering = cal.elections.map { it.date to it.state }
        assertEquals(
            listOf(
                "2026-03-03" to "TX",
                "2026-05-19" to "OR", // same date tie-breaks by state code
                "2026-05-19" to "PA",
                "2026-11-03" to "US",
            ),
            ordering,
        )
    }

    // --- validation: malformed rows dropped, not published -----------------

    @Test
    fun bad_state_dropped() = assertOnlyGeneral(primary("ZZ", "2026-03-03", 2026))

    @Test
    fun bad_date_dropped() = assertOnlyGeneral(primary("TX", "not-a-date", 2026))

    @Test
    fun bad_type_dropped() = assertOnlyGeneral(primary("TX", "2026-03-03", 2026, type = "general"))

    @Test
    fun odd_election_year_dropped() = assertOnlyGeneral(primary("TX", "2027-03-03", 2027))

    @Test
    fun date_year_mismatch_dropped() = assertOnlyGeneral(primary("TX", "2027-03-03", 2026))

    @Test
    fun null_date_dropped() = assertOnlyGeneral(CuratedPrimary(state = "TX", type = "primary", electionYear = 2026))

    @Test
    fun stale_cycle_primary_dropped() {
        val primaries = listOf(
            primary("TX", "2024-03-05", 2024), // past cycle
            primary("OH", "2026-05-05", 2026), // current cycle
        )
        val cal = buildElectionCalendar(TODAY, GEN_AT, curatedPrimaries = primaries)
        assertEquals(setOf("US", "OH"), cal.elections.map { it.state }.toSet())
    }

    @Test
    fun empty_curated_publishes_general_only() {
        val cal = buildElectionCalendar(TODAY, GEN_AT, curatedPrimaries = emptyList())
        assertEquals(1, cal.elections.size)
        assertEquals(ElectionType.GENERAL, cal.elections.single().type)
    }

    @Test
    fun validate_warns_on_drop_but_not_on_stale() {
        val warnings = mutableListOf<String>()
        assertNull(validateCuratedPrimary(primary("ZZ", "2026-03-03", 2026), minYear = 2026) { warnings.add(it) })
        assertEquals(1, warnings.size)
        assertContains(warnings.single(), "unknown state")

        warnings.clear()
        // Stale cycle is dropped silently (no warning).
        assertNull(validateCuratedPrimary(primary("TX", "2024-03-03", 2024), minYear = 2026) { warnings.add(it) })
        assertTrue(warnings.isEmpty(), "stale cycle should not warn")
    }

    // --- loader ------------------------------------------------------------

    @Test
    fun loader_reads_primaries_and_ignores_meta() {
        val fs = FakeFileSystem()
        val path = "/data/election_primaries.json".toPath()
        fs.createDirectories(path.parent!!)
        fs.sink(path).buffer().use {
            it.writeUtf8(
                """
                {
                  "_meta": { "note": "ignored" },
                  "primaries": [
                    {"state": "TX", "date": "2026-03-03", "type": "primary", "election_year": 2026, "source": "x"}
                  ]
                }
                """.trimIndent(),
            )
        }
        val curated = loadCuratedPrimaries(fs, path)
        assertEquals(1, curated.size)
        assertEquals("TX", curated.single().state)
        assertEquals(2026, curated.single().electionYear)
    }

    @Test
    fun loader_missing_file_warns_and_returns_empty() {
        val fs = FakeFileSystem()
        val warnings = mutableListOf<String>()
        val curated = loadCuratedPrimaries(fs, "/nope.json".toPath()) { warnings.add(it) }
        assertTrue(curated.isEmpty())
        assertEquals(1, warnings.size)
        assertContains(warnings.single(), "missing")
    }

    @Test
    fun loader_throws_on_file_without_primaries_list() {
        val fs = FakeFileSystem()
        val path = "/broken.json".toPath()
        fs.sink(path).buffer().use { it.writeUtf8("""{"_meta": {}}""") }
        assertFailsWith<IllegalStateException> { loadCuratedPrimaries(fs, path) }
    }

    // --- store -------------------------------------------------------------

    @Test
    fun store_writes_calendar_and_omits_null_source_on_general_row() {
        val fs = FakeFileSystem()
        val store = FileElectionCalendarStore(fs, "/out".toPath())
        val cal = buildElectionCalendar(TODAY, GEN_AT)
        val path = store.save(cal)

        val text = fs.source(path).buffer().use { it.readUtf8() }
        // The general row carries no source, so no null-source key is emitted.
        assertFalse(text.contains("\"source\": null"), "explicitNulls=false must drop the general row's source key")
        assertTrue(text.endsWith("\n"))

        // Round-trips back to the same model via the app's lenient reader.
        val reread = ManifestReadJson.decodeFromString(ElectionCalendar.serializer(), text)
        assertEquals(cal, reread)
    }

    private fun assertOnlyGeneral(bad: CuratedPrimary) {
        val cal = buildElectionCalendar(TODAY, GEN_AT, curatedPrimaries = listOf(bad))
        assertEquals(listOf(ElectionType.GENERAL), cal.elections.map { it.type })
    }
}
