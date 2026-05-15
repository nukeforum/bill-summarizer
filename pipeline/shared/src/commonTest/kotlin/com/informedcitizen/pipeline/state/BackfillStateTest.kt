package com.informedcitizen.pipeline.state

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackfillStateTest {
    @Test
    fun initial_state_seeds_queue_from_current_congress_down_to_93() {
        val state = initialBackfillState(currentCongress = 119)
        assertEquals(119, state.activeCongress)
        assertEquals(0, state.activeOffset)
        assertEquals(119, state.queue.first())
        assertEquals(OLDEST_API_CONGRESS, state.queue.last())
        // 119 down to 93 inclusive = 27 entries.
        assertEquals(27, state.queue.size)
        assertEquals(emptyList(), state.completed)
        assertNull(state.lastRunAt)
    }

    @Test
    fun initial_state_with_current_below_oldest_yields_empty_queue() {
        // Pathological: shouldn't happen in practice, but the formula
        // shouldn't crash. With `currentCongress = 92`, `92 downTo 93`
        // is empty.
        val state = initialBackfillState(currentCongress = 92)
        assertEquals(emptyList(), state.queue)
        assertNull(state.activeCongress)
    }

    @Test
    fun decodes_python_style_json_byte_for_byte_match() {
        // Hand-written to mirror what `_common.save_state(initial_state())`
        // produces on disk. Pins the wire format so the JVM CLI can read
        // a file the Python pipeline wrote mid-crawl.
        val pythonJson = """
            {
              "active_congress": 119,
              "active_offset": 0,
              "queue": [
                119,
                118,
                117
              ],
              "completed": [],
              "last_run_at": null
            }
        """.trimIndent()
        val decoded = Json.decodeFromString(BackfillState.serializer(), pythonJson)
        assertEquals(119, decoded.activeCongress)
        assertEquals(0, decoded.activeOffset)
        assertEquals(listOf(119, 118, 117), decoded.queue)
        assertEquals(emptyList(), decoded.completed)
        assertNull(decoded.lastRunAt)
    }

    @Test
    fun encodes_matching_python_dump_indent_2_format() {
        // Pin output bytes — if kotlinx-serialization ever changes its
        // pretty-print format, this fails loudly before the CI cut-over.
        val state = BackfillState(
            activeCongress = 118,
            activeOffset = 750,
            queue = listOf(118, 117),
            completed = listOf(119),
            lastRunAt = "2026-05-05T00:00:00Z",
        )
        val encoded = PipelineStateJson.encodeToString(BackfillState.serializer(), state)
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
        assertEquals(expected, encoded)
    }

    @Test
    fun encodes_null_active_congress_explicitly_not_omitted() {
        // When the queue is fully exhausted, active_congress is null.
        // Python writes `"active_congress": null`; we must too, so a
        // mid-cut-over reload doesn't see a missing field and seed
        // a fresh queue on top of the completed run.
        val state = BackfillState(
            activeCongress = null,
            activeOffset = 0,
            queue = emptyList(),
            completed = listOf(119, 118),
            lastRunAt = "2026-05-15T00:00:00Z",
        )
        val encoded = PipelineStateJson.encodeToString(BackfillState.serializer(), state)
        assertTrue("\"active_congress\": null" in encoded, encoded)
    }

    @Test
    fun field_order_matches_python_dict_insertion_order() {
        val state = BackfillState(
            activeCongress = 119,
            activeOffset = 250,
            queue = listOf(119),
            completed = emptyList(),
            lastRunAt = "2026-05-15T00:00:00Z",
        )
        val encoded = PipelineStateJson.encodeToString(BackfillState.serializer(), state)
        val positions = listOf(
            encoded.indexOf("\"active_congress\""),
            encoded.indexOf("\"active_offset\""),
            encoded.indexOf("\"queue\""),
            encoded.indexOf("\"completed\""),
            encoded.indexOf("\"last_run_at\""),
        )
        assertEquals(positions, positions.sorted(), "fields out of expected order: $encoded")
    }
}
