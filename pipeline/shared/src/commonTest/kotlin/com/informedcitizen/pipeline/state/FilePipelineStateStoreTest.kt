package com.informedcitizen.pipeline.state

import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun store(fs: FakeFileSystem = FakeFileSystem()) =
    fs to FilePipelineStateStore(
        fileSystem = fs,
        backfillPath = "/state/backfill_state.json".toPath(),
    )

private fun readUtf8(fs: FakeFileSystem, path: String): String =
    fs.source(path.toPath()).buffer().use { it.readUtf8() }

class FilePipelineStateStoreTest {
    @Test
    fun load_returns_initial_when_file_missing() {
        val (_, sut) = store()
        val seed = initialBackfillState(119)
        val loaded = sut.loadBackfillState { seed }
        assertEquals(seed, loaded)
    }

    @Test
    fun load_returns_initial_when_file_unparseable() {
        val (fs, sut) = store()
        fs.createDirectories("/state".toPath())
        fs.sink("/state/backfill_state.json".toPath()).buffer().use {
            it.writeUtf8("not json {{{")
        }
        val seed = initialBackfillState(119)
        val loaded = sut.loadBackfillState { seed }
        assertEquals(seed, loaded)
    }

    @Test
    fun save_creates_parent_directories_if_missing() {
        val (fs, sut) = store()
        // /state does not exist yet
        sut.saveBackfillState(initialBackfillState(119))
        assertTrue(fs.exists("/state".toPath()))
        assertTrue(fs.exists("/state/backfill_state.json".toPath()))
    }

    @Test
    fun save_writes_trailing_newline_matching_python() {
        val (fs, sut) = store()
        sut.saveBackfillState(initialBackfillState(119))
        val text = readUtf8(fs, "/state/backfill_state.json")
        assertTrue(text.endsWith("\n"), "expected trailing newline: ${text.takeLast(20)}")
    }

    @Test
    fun save_then_load_roundtrip_preserves_fields() {
        val (_, sut) = store()
        val state = BackfillState(
            activeCongress = 118,
            activeOffset = 750,
            queue = listOf(118, 117),
            completed = listOf(119),
            lastRunAt = "2026-05-05T00:00:00Z",
        )
        sut.saveBackfillState(state)
        val loaded = sut.loadBackfillState { error("should not seed") }
        assertEquals(state, loaded)
    }

    @Test
    fun save_output_matches_python_dump_indent_2_format() {
        // Byte-for-byte file content. If this fails the CI cut-over of
        // backfill-bills.yml will see a different file shape than the
        // Python pipeline produced.
        val (fs, sut) = store()
        val state = BackfillState(
            activeCongress = 118,
            activeOffset = 750,
            queue = listOf(118, 117),
            completed = listOf(119),
            lastRunAt = "2026-05-05T00:00:00Z",
        )
        sut.saveBackfillState(state)
        val actual = readUtf8(fs, "/state/backfill_state.json")
        val expected = """
            {
              "active_congress": 118,
              "active_offset": 750,
              "queue": [
                118,
                117
              ],
              "completed": [
                119
              ],
              "last_run_at": "2026-05-05T00:00:00Z"
            }

        """.trimIndent()
        assertEquals(expected, actual)
    }

    @Test
    fun advance_save_load_chain_is_idempotent() {
        // Mirrors Python `test_advance_state_idempotent_through_save_load_roundtrip`.
        val (_, sut) = store()
        val now = "2026-05-15T00:00:00Z"

        var state = initialBackfillState(119)
        assertEquals(0, state.activeOffset)

        // First chunk: full page, cursor advances to 250.
        state = advanceBackfillState(state, LIST_PAGE_LIMIT, pagesConsumed = 1, nowIso = now)
        sut.saveBackfillState(state)

        val reloaded = sut.loadBackfillState { error("should not seed") }
        assertEquals(LIST_PAGE_LIMIT, reloaded.activeOffset)
        assertEquals(119, reloaded.activeCongress)
        assertEquals(state.queue, reloaded.queue)
        assertEquals(state.completed, reloaded.completed)

        // Second chunk: another full page.
        val second = advanceBackfillState(reloaded, LIST_PAGE_LIMIT, pagesConsumed = 1, nowIso = now)
        sut.saveBackfillState(second)

        val reloaded2 = sut.loadBackfillState { error("should not seed") }
        assertEquals(2 * LIST_PAGE_LIMIT, reloaded2.activeOffset)

        // Third chunk: short page exhausts Congress 119, advances to 118.
        val third = advanceBackfillState(reloaded2, pageReturned = 10, pagesConsumed = 1, nowIso = now)
        assertTrue(119 in third.completed)
        assertEquals(118, third.activeCongress)
        assertEquals(0, third.activeOffset)
    }

    @Test
    fun load_can_read_a_python_style_handcrafted_file() {
        // Simulate the CI cut-over: a file the Python pipeline wrote
        // exists on disk; the Kotlin store reads it without migration.
        val (fs, sut) = store()
        fs.createDirectories("/state".toPath())
        val pythonText = """
            {
              "active_congress": 117,
              "active_offset": 3000,
              "queue": [
                117,
                116,
                115
              ],
              "completed": [
                119,
                118
              ],
              "last_run_at": "2026-05-14T03:00:00Z"
            }

        """.trimIndent()
        fs.sink("/state/backfill_state.json".toPath()).buffer().use { it.writeUtf8(pythonText) }
        val loaded = sut.loadBackfillState { error("should not seed") }
        assertEquals(117, loaded.activeCongress)
        assertEquals(3000, loaded.activeOffset)
        assertEquals(listOf(117, 116, 115), loaded.queue)
        assertEquals(listOf(119, 118), loaded.completed)
        assertEquals("2026-05-14T03:00:00Z", loaded.lastRunAt)
    }
}
