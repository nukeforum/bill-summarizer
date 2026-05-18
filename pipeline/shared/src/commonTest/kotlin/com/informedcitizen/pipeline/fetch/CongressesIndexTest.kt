package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.Sponsor
import com.informedcitizen.pipeline.state.BackfillState
import com.informedcitizen.pipeline.state.FilePipelineStateStore
import com.informedcitizen.pipeline.state.initialBackfillState
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Direct port of `data-pipeline/tests/test_index.py`. Mirrors the
 * Python `rebuild_index` behaviour exactly because `congresses.json`
 * goes onto disk byte-for-byte alongside the per-Congress manifests
 * during the parallel-run period.
 */
class CongressesIndexTest {

    private fun bill(id: String, actionDate: String): Bill = Bill(
        id = id,
        congress = id.substringAfterLast('-').toInt(),
        type = "hr",
        number = "1",
        title = "Title",
        shortTitle = null,
        sponsor = Sponsor("Rep. Doe, J", "D", "CA"),
        introducedDate = "2025-01-01",
        latestAction = Action(actionDate, "Some action."),
        outcome = Outcome.ENACTED,
        summaryCrs = null,
        textUrlHtml = null,
        textUrlXml = null,
        textUrlPdf = null,
        congressGovUrl = "https://www.congress.gov/bill/$id",
    )

    private fun writeManifest(
        fs: FakeFileSystem,
        outputDir: String,
        congress: Int,
        bills: List<Bill>,
    ) {
        FileBillsManifestStore(fs, outputDir.toPath())
            .save(congress, bills, nowIso = "2026-05-05T00:00:00Z")
    }

    @Test fun rebuild_writes_index_for_empty_dir() {
        val fs = FakeFileSystem()
        fs.createDirectories("/out".toPath())
        val store = FileCongressesIndexStore(fs, "/out".toPath())

        val index = store.rebuild(
            currentCongress = 119,
            completed = emptySet(),
            nowIso = "2026-05-18T00:00:00Z",
        )

        assertEquals(119, index.currentCongress)
        assertEquals(emptyList(), index.congresses)
        assertTrue(fs.exists("/out/congresses.json".toPath()))
    }

    @Test fun rebuild_reads_existing_manifests() {
        val fs = FakeFileSystem()
        writeManifest(fs, "/out", 119, listOf(
            bill("hr1-119", "2025-03-01"),
            bill("s2-119", "2026-04-30"),
        ))
        writeManifest(fs, "/out", 118, listOf(
            bill("hr5-118", "2024-12-15"),
        ))
        val store = FileCongressesIndexStore(fs, "/out".toPath())

        val index = store.rebuild(
            currentCongress = 119,
            completed = emptySet(),
            nowIso = "2026-05-18T00:00:00Z",
        )

        assertEquals(listOf(119, 118), index.congresses.map { it.congress })
        val cur = index.congresses[0]
        assertEquals(2, cur.billCount)
        assertEquals("2025-03-01", cur.firstActionDate)
        assertEquals("2026-04-30", cur.lastActionDate)
        assertEquals("congress119_bills.json", cur.manifestPath)
        assertTrue(cur.isCurrent)
        assertFalse(cur.backfillComplete)
        assertFalse(index.congresses[1].isCurrent)
    }

    @Test fun rebuild_marks_completed_from_state() {
        val fs = FakeFileSystem()
        writeManifest(fs, "/out", 118, emptyList())
        writeManifest(fs, "/out", 117, emptyList())
        val store = FileCongressesIndexStore(fs, "/out".toPath())

        val index = store.rebuild(
            currentCongress = 119,
            completed = setOf(118, 117),
            nowIso = "2026-05-18T00:00:00Z",
        )

        val byCong = index.congresses.associateBy { it.congress }
        assertTrue(byCong.getValue(118).backfillComplete)
        assertTrue(byCong.getValue(117).backfillComplete)
    }

    @Test fun rebuild_ignores_unrelated_files() {
        val fs = FakeFileSystem()
        fs.createDirectories("/out".toPath())
        fs.sink("/out/bills.json".toPath()).buffer().use { it.writeUtf8("{}") }
        fs.sink("/out/congresses.json".toPath()).buffer().use { it.writeUtf8("{}") }
        writeManifest(fs, "/out", 119, emptyList())
        val store = FileCongressesIndexStore(fs, "/out".toPath())

        val index = store.rebuild(
            currentCongress = 119,
            completed = emptySet(),
            nowIso = "2026-05-18T00:00:00Z",
        )

        assertEquals(listOf(119), index.congresses.map { it.congress })
    }

    @Test fun rebuild_skips_dates_when_bills_missing_action_date() {
        val fs = FakeFileSystem()
        writeManifest(fs, "/out", 119, emptyList())
        val store = FileCongressesIndexStore(fs, "/out".toPath())

        val index = store.rebuild(
            currentCongress = 119,
            completed = emptySet(),
            nowIso = "2026-05-18T00:00:00Z",
        )

        val entry = index.congresses.single()
        assertEquals(0, entry.billCount)
        assertNull(entry.firstActionDate)
        assertNull(entry.lastActionDate)
    }

    @Test fun saved_index_round_trips() {
        val fs = FakeFileSystem()
        writeManifest(fs, "/out", 119, listOf(bill("hr1-119", "2025-03-01")))
        val store = FileCongressesIndexStore(fs, "/out".toPath())

        val written = store.rebuild(
            currentCongress = 119,
            completed = emptySet(),
            nowIso = "2026-05-18T00:00:00Z",
        )
        val loaded = store.load()
        assertNotNull(loaded)
        assertEquals(written, loaded)
    }

    @Test fun saved_index_byte_parity_with_python() {
        // Byte-parity: Python `_write_json` produces 2-space indent, an
        // explicit `null` for absent date fields, and a trailing newline.
        // Same JSON config powers ManifestIO already; this test pins it
        // for the index too.
        val fs = FakeFileSystem()
        writeManifest(fs, "/out", 119, emptyList())
        FileCongressesIndexStore(fs, "/out".toPath()).rebuild(
            currentCongress = 119,
            completed = emptySet(),
            nowIso = "2026-05-18T00:00:00Z",
        )

        val text = fs.source("/out/congresses.json".toPath()).buffer().use { it.readUtf8() }
        assertTrue(text.endsWith("\n"), "expected trailing newline")
        // Field order: generated_at, current_congress, congresses.
        val genAtIdx = text.indexOf("\"generated_at\"")
        val curIdx = text.indexOf("\"current_congress\"")
        val congsIdx = text.indexOf("\"congresses\"")
        assertTrue(genAtIdx in 0 until curIdx, "generated_at must precede current_congress")
        assertTrue(curIdx < congsIdx, "current_congress must precede congresses")
        // Per-entry: explicit null dates when no bills, 2-space indent
        // visible as `\n    "first_action_date"` (top object is at depth
        // 1 with 2 spaces, entries inside the array are at depth 2 with
        // 4 spaces).
        assertTrue(
            "\"first_action_date\": null" in text,
            "expected explicit null first_action_date:\n$text",
        )
        assertTrue(
            "\"last_action_date\": null" in text,
            "expected explicit null last_action_date:\n$text",
        )
    }

    @Test fun rebuild_reads_completed_from_persisted_state_file() {
        // End-to-end composition: a state file persisted by
        // FilePipelineStateStore + manifests on disk + rebuild ->
        // backfill_complete flags reflect the persisted state.
        // Catches glue regressions where the CLI forgets to thread
        // `completed` from state into the index.
        val fs = FakeFileSystem()
        writeManifest(fs, "/out", 119, emptyList())
        writeManifest(fs, "/out", 118, emptyList())
        writeManifest(fs, "/out", 117, emptyList())
        val stateStore = FilePipelineStateStore(fs, "/state/backfill_state.json".toPath())
        stateStore.saveBackfillState(
            BackfillState(activeCongress = 119, queue = listOf(119), completed = listOf(118, 117))
        )

        val state = stateStore.loadBackfillState { initialBackfillState(119) }
        val index = FileCongressesIndexStore(fs, "/out".toPath()).rebuild(
            currentCongress = 119,
            completed = state.completed.toSet(),
            nowIso = "2026-05-18T00:00:00Z",
        )

        val byCong = index.congresses.associateBy { it.congress }
        assertFalse(byCong.getValue(119).backfillComplete, "current congress is never marked complete")
        assertTrue(byCong.getValue(118).backfillComplete)
        assertTrue(byCong.getValue(117).backfillComplete)
    }

    @Test fun saved_index_field_order_within_entry_matches_python() {
        // Per-entry field order: congress, bill_count, first_action_date,
        // last_action_date, manifest_path, is_current, backfill_complete.
        val fs = FakeFileSystem()
        writeManifest(fs, "/out", 119, listOf(bill("hr1-119", "2025-03-01")))
        FileCongressesIndexStore(fs, "/out".toPath()).rebuild(
            currentCongress = 119,
            completed = emptySet(),
            nowIso = "2026-05-18T00:00:00Z",
        )
        val text = fs.source("/out/congresses.json".toPath()).buffer().use { it.readUtf8() }
        val order = listOf(
            "\"congress\"",
            "\"bill_count\"",
            "\"first_action_date\"",
            "\"last_action_date\"",
            "\"manifest_path\"",
            "\"is_current\"",
            "\"backfill_complete\"",
        )
        var lastIdx = -1
        for (key in order) {
            val idx = text.indexOf(key, startIndex = lastIdx + 1)
            assertTrue(idx > lastIdx, "expected $key after position $lastIdx; full text:\n$text")
            lastIdx = idx
        }
    }
}
