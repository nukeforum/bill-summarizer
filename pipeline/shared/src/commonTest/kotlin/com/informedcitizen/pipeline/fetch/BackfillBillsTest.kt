package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.PipelineHttpConfig
import com.informedcitizen.pipeline.http.configurePipelineForTest
import com.informedcitizen.pipeline.state.BACKFILL_PAGES_PER_RUN
import com.informedcitizen.pipeline.state.BackfillState
import com.informedcitizen.pipeline.state.LIST_PAGE_LIMIT
import com.informedcitizen.pipeline.state.initialBackfillState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

private const val FIXED_NOW = "2026-05-15T00:00:00Z"

/** Bare-minimum detail/summaries/text responses — sponsor + title only. */
private const val MIN_DETAIL = """{"bill":{"title":"A bill","introducedDate":"2025-01-01","sponsors":[{"fullName":"Sen. Test, T [D-XX]"}]}}"""
private const val MIN_SUMMARIES = """{"summaries":[]}"""
private const val MIN_TEXT = """{"textVersions":[]}"""

/**
 * Mock handler that:
 *  - returns the supplied list-page responses for `/bill/{congress}` calls,
 *    in order; when the supplied responses are exhausted, returns `{"bills":[]}`
 *  - returns a minimal detail/summaries/text payload for any other `/bill/...` path
 *
 * Captures the offsets seen on list-page calls so tests can assert the
 * cursor moved as expected.
 */
private fun mockBackfillClient(
    listResponses: List<String>,
): Pair<HttpClient, MutableList<Int>> {
    val capturedOffsets = mutableListOf<Int>()
    val iter = listResponses.iterator()
    val client = HttpClient(MockEngine) {
        configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
        engine {
            addHandler { request ->
                val path = request.url.encodedPath
                // List page: path ends in `/bill/{N}` (two slashes in `/v3/bill/N`).
                if (path.matches(Regex("^/v3/bill/\\d+$"))) {
                    val offsetParam = request.url.parameters["offset"]?.toIntOrNull() ?: 0
                    capturedOffsets += offsetParam
                    val body = if (iter.hasNext()) iter.next() else """{"bills":[]}"""
                    respond(body, HttpStatusCode.OK, jsonHeaders())
                } else if (path.endsWith("/summaries")) {
                    respond(MIN_SUMMARIES, HttpStatusCode.OK, jsonHeaders())
                } else if (path.endsWith("/text")) {
                    respond(MIN_TEXT, HttpStatusCode.OK, jsonHeaders())
                } else {
                    respond(MIN_DETAIL, HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
    }
    return client to capturedOffsets
}

class BackfillBillsTest {
    @Test fun queue_empty_returns_early_without_state_mutation() = runTest {
        val (client, offsets) = mockBackfillClient(emptyList())
        val cc = CongressClient(client, apiKey = "k")
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        val state = BackfillState(
            activeCongress = null,
            activeOffset = 0,
            queue = emptyList(),
            completed = listOf(119, 118),
            lastRunAt = "2026-05-01T00:00:00Z",
        )

        val result = backfillBills(cc, state, FIXED_NOW, store, ErrorCollector(), pagesPerRun = 1)

        assertTrue(result.queueWasEmpty)
        assertNull(result.activeCongress)
        assertEquals(state, result.newState) // no mutation
        assertEquals(0, offsets.size) // no API calls
        assertNull(result.finalManifest)
    }

    @Test fun first_run_seeds_manifest_and_completes_short_page() = runTest {
        // Mirrors Python test_main_first_run_seeds_state_and_writes_manifest:
        // a single 2-bill response (one kept, one rejected) ⇒ congress 119
        // marked complete, advance to 118.
        val listResp = """{"bills":[
            {"type":"hr","number":"1","latestAction":{"text":"Became Public Law No: 119-1","actionDate":"2025-02-15"}},
            {"type":"s","number":"2","latestAction":{"text":"Referred to committee.","actionDate":"2025-03-01"}}
        ]}"""
        val (client, _) = mockBackfillClient(listOf(listResp))
        val cc = CongressClient(client, apiKey = "k")
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        val initial = initialBackfillState(currentCongress = 119)

        val result = backfillBills(
            cc, initial, FIXED_NOW, store, ErrorCollector(),
            pagesPerRun = 1,
        )

        assertFalse(result.queueWasEmpty)
        assertEquals(119, result.activeCongress)
        assertEquals(2, result.evaluated)
        // Only the "Became Public Law" bill is kept.
        assertEquals(setOf("hr1-119"), result.keptRecords.map { it.id }.toSet())
        // Congress 119 marked complete; queue advanced to 118.
        assertTrue(119 in result.newState.completed)
        assertEquals(118, result.newState.activeCongress)
        assertEquals(0, result.newState.activeOffset)
        assertTrue(result.congressCompleted)
        assertFalse(result.cursorHeld)
        // Manifest written for the active congress.
        assertTrue(fs.exists("/out/congress119_bills.json".toPath()))
    }

    @Test fun resumes_from_existing_state_offset() = runTest {
        // Mirrors Python test_main_resumes_from_existing_state: pre-seed at
        // offset 500 mid-Congress, confirm fetch starts at 500 not 0.
        val singleBill = """{"bills":[
            {"type":"hr","number":"5","latestAction":{"text":"Passed House by recorded vote: 220-211","actionDate":"2024-06-01"}}
        ]}"""
        val (client, capturedOffsets) = mockBackfillClient(listOf(singleBill))
        val cc = CongressClient(client, apiKey = "k")
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        val state = BackfillState(
            activeCongress = 118,
            activeOffset = 500,
            queue = listOf(118, 117, 116),
            completed = listOf(119),
            lastRunAt = "2026-05-04T00:00:00Z",
        )

        val result = backfillBills(cc, state, FIXED_NOW, store, ErrorCollector(), pagesPerRun = 1)

        assertEquals(500, capturedOffsets.first())
        // Short page (1 < 250) at offset 500 (>0): real exhaustion, 118 done.
        assertTrue(118 in result.newState.completed)
        assertEquals(117, result.newState.activeCongress)
        assertEquals(0, result.newState.activeOffset)
        assertTrue(result.congressCompleted)
    }

    @Test fun transient_empty_first_page_does_not_mark_complete() = runTest {
        // Regression: 2026-05-05 fix. Empty first page at offset 0 with no
        // prior non-empty pages MUST hold the cursor.
        val empty = """{"bills":[]}"""
        val (client, _) = mockBackfillClient(listOf(empty))
        val cc = CongressClient(client, apiKey = "k")
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        val initial = initialBackfillState(currentCongress = 119)

        val result = backfillBills(
            cc, initial, FIXED_NOW, store, ErrorCollector(),
            pagesPerRun = BACKFILL_PAGES_PER_RUN,
        )

        assertFalse(119 in result.newState.completed)
        assertEquals(119, result.newState.activeCongress)
        assertEquals(0, result.newState.activeOffset)
        assertTrue(result.cursorHeld)
        assertFalse(result.congressCompleted)
        assertEquals(initial.queue, result.newState.queue)
    }

    @Test fun full_page_bumps_offset_within_congress() = runTest {
        // Page returns exactly LIST_PAGE_LIMIT items ⇒ Congress not done;
        // cursor advances to next offset within Congress.
        val fullPage = buildString {
            append("""{"bills":[""")
            for (n in 0 until LIST_PAGE_LIMIT) {
                if (n > 0) append(",")
                append("""{"type":"hr","number":"$n","latestAction":{"text":"Referred to committee.","actionDate":"2025-01-01"}}""")
            }
            append("]}")
        }
        val (client, _) = mockBackfillClient(listOf(fullPage))
        val cc = CongressClient(client, apiKey = "k")
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        val initial = initialBackfillState(currentCongress = 119)

        val result = backfillBills(cc, initial, FIXED_NOW, store, ErrorCollector(), pagesPerRun = 1)

        assertFalse(119 in result.newState.completed)
        assertEquals(119, result.newState.activeCongress)
        assertEquals(LIST_PAGE_LIMIT, result.newState.activeOffset)
        assertFalse(result.congressCompleted)
        assertFalse(result.cursorHeld)
    }

    @Test fun multi_page_run_stops_when_short_page_encountered() = runTest {
        // pagesPerRun=4 but the SECOND page is short; we should stop after
        // page 2 (not consume all 4 page slots) and complete the Congress.
        val fullPage = buildString {
            append("""{"bills":[""")
            for (n in 0 until LIST_PAGE_LIMIT) {
                if (n > 0) append(",")
                append("""{"type":"hr","number":"$n","latestAction":{"text":"Referred.","actionDate":"2025-01-01"}}""")
            }
            append("]}")
        }
        val shortPage = """{"bills":[{"type":"hr","number":"x","latestAction":{"text":"Referred.","actionDate":"2025-01-01"}}]}"""
        val (client, offsets) = mockBackfillClient(listOf(fullPage, shortPage))
        val cc = CongressClient(client, apiKey = "k")
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        val initial = initialBackfillState(currentCongress = 119)

        val result = backfillBills(cc, initial, FIXED_NOW, store, ErrorCollector(), pagesPerRun = 4)

        assertEquals(2, result.pagesConsumed)
        assertEquals(2, offsets.size) // not 4
        assertTrue(119 in result.newState.completed)
        assertTrue(result.congressCompleted)
    }
}
