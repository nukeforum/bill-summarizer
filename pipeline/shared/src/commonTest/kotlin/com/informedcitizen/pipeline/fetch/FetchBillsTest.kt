package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.PipelineHttpConfig
import com.informedcitizen.pipeline.http.configurePipelineForTest
import com.informedcitizen.pipeline.model.Outcome
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

/**
 * Mock handler that serves a small recent-bills list + detail/summaries/text
 * responses for two bills, one of which passes the filter and one of which
 * doesn't. Used by the end-to-end fetcher test.
 */
private fun mockApiClient(): HttpClient = HttpClient(MockEngine) {
    configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
    engine {
        addHandler { request ->
            val path = request.url.encodedPath
            val body = when {
                path == "/v3/bill/119" -> """{
                  "bills":[
                    {"type":"hr","number":"1","title":"Enacted Bill",
                     "latestAction":{"text":"Became Public Law No: 119-1.","actionDate":"2026-04-01"}},
                    {"type":"hr","number":"2","title":"Stalled Bill",
                     "latestAction":{"text":"Referred to Committee on X.","actionDate":"2026-04-02"}}
                  ]
                }"""
                path == "/v3/bill/119/hr/1" -> """{"bill":{
                  "title":"Enacted Bill",
                  "introducedDate":"2026-01-15",
                  "sponsors":[{"fullName":"Rep. Smith, Adrian [R-NE-3]","party":"Republican","state":"NE"}]
                }}"""
                path == "/v3/bill/119/hr/1/summaries" -> """{"summaries":[{"updateDate":"2026-04-01","text":"CRS summary."}]}"""
                path == "/v3/bill/119/hr/1/text" -> """{"textVersions":[
                  {"date":"2026-04-01","formats":[{"type":"Formatted Text","url":"https://x/hr1.htm"}]}
                ]}"""
                else -> "{}"
            }
            respond(body, HttpStatusCode.OK, jsonHeaders())
        }
    }
}

class FetchBillsTest {
    @Test fun end_to_end_filters_enriches_merges_and_saves() = runTest {
        val client = mockApiClient()
        val cc = CongressClient(client, apiKey = "k")
        val errors = ErrorCollector()
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        val cutoff = Instant.parse("2026-03-15T00:00:00Z")

        val result = fetchBills(
            client = cc,
            congress = 119,
            cutoff = cutoff,
            nowIso = "2026-05-15T00:00:00Z",
            manifestStore = store,
            errors = errors,
        )

        assertEquals(2, result.evaluated)
        assertEquals(1, result.keptRecords.size)
        val kept = result.keptRecords.single()
        assertEquals("hr1-119", kept.id)
        assertEquals(Outcome.ENACTED, kept.outcome)
        // The rejected bill should be tagged as no_outcome_match.
        assertEquals(1, result.rejectionCounts[RejectionReasons.NO_OUTCOME_MATCH])
        // Merge stats: 1 added, 0 updated, 0 unchanged.
        assertEquals(1, result.mergeStats.added)
        assertEquals(0, result.mergeStats.updated)
        // Manifest written to disk.
        assertTrue(fs.exists("/out/congress119_bills.json".toPath()))
        assertEquals(1, result.finalManifest.bills.size)
        assertEquals("2026-05-15T00:00:00Z", result.finalManifest.generatedAt)
        // ErrorCollector should still be empty (no enrichment failures).
        assertEquals(0, errors.size)
    }

    @Test fun merges_with_existing_manifest_preserving_older_bills() = runTest {
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        // Seed the manifest with a bill the new fetch won't touch.
        store.save(
            congress = 119,
            bills = listOf(
                com.informedcitizen.pipeline.model.Bill(
                    id = "hr999-119",
                    congress = 119,
                    type = "hr",
                    number = "999",
                    title = "Old Bill",
                    sponsor = com.informedcitizen.pipeline.model.Sponsor("X", "D", "CA"),
                    introducedDate = "2025-01-01",
                    latestAction = com.informedcitizen.pipeline.model.Action("2025-12-01", "Became Public Law"),
                    outcome = Outcome.ENACTED,
                    congressGovUrl = "https://example/hr999",
                ),
            ),
            nowIso = "2025-12-01T00:00:00Z",
        )

        val client = mockApiClient()
        val cc = CongressClient(client, apiKey = "k")
        val result = fetchBills(
            client = cc,
            congress = 119,
            cutoff = Instant.parse("2026-03-15T00:00:00Z"),
            nowIso = "2026-05-15T00:00:00Z",
            manifestStore = store,
            errors = ErrorCollector(),
        )

        // Newly fetched bill (hr1) is added; existing hr999 preserved.
        assertEquals(1, result.mergeStats.added)
        val ids = result.finalManifest.bills.map { it.id }.toSet()
        assertEquals(setOf("hr1-119", "hr999-119"), ids)
    }
}
