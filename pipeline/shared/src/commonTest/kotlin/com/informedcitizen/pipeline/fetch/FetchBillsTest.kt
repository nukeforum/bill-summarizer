package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.PipelineHttpConfig
import com.informedcitizen.pipeline.http.configurePipelineForTest
import com.informedcitizen.pipeline.model.Chamber
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.VoteRef
import com.informedcitizen.pipeline.model.VoteTotals
import com.informedcitizen.pipeline.model.VotesIndex
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

/**
 * Serves a recent-bills list of [enactedNumbers] `hr` bills (all
 * passing the filter as ENACTED) plus their detail/summaries/text, in
 * the array order given (newest-first). Records every detail-path
 * request into [detailHits] so a test can prove which bills were
 * enriched.
 */
private fun mockEnactedBills(
    enactedNumbers: List<Int>,
    detailHits: MutableList<String>,
): HttpClient = HttpClient(MockEngine) {
    configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
    engine {
        addHandler { request ->
            val path = request.url.encodedPath
            val listBody = enactedNumbers.joinToString(",", "[", "]") { n ->
                """{"type":"hr","number":"$n","title":"Bill $n",
                    "latestAction":{"text":"Became Public Law No: 119-$n.","actionDate":"2026-04-01"}}"""
            }
            val detailRe = Regex("""^/v3/bill/119/hr/(\d+)$""")
            val body = when {
                path == "/v3/bill/119" -> """{"bills":$listBody}"""
                detailRe.matches(path) -> {
                    detailHits += path
                    """{"bill":{"title":"Bill","introducedDate":"2026-01-15",
                       "sponsors":[{"fullName":"Rep. Smith","party":"Republican","state":"NE"}]}}"""
                }
                path.endsWith("/summaries") -> """{"summaries":[{"updateDate":"2026-04-01","text":"CRS."}]}"""
                path.endsWith("/text") -> """{"textVersions":[
                    {"date":"2026-04-01","formats":[{"type":"Formatted Text","url":"https://x/b.htm"}]}]}"""
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

    @Test fun attaches_vote_refs_from_votes_index_without_merge_churn() = runTest {
        // Mirrors Python `test_main_attaches_vote_refs_from_votes_index`:
        // bills gain the VoteRef rows whose bill_id matches, and the
        // derived votes field stays out of the merge comparison across runs.
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        val votesStore = FileVotesStore(fs, "/out".toPath())
        val ref = VoteRef(
            id = "house-119-1-17",
            chamber = Chamber.HOUSE,
            session = 1,
            rollNumber = 17,
            date = "2026-04-01",
            question = "On Passage",
            result = "Passed",
            billId = "hr1-119",
            totals = VoteTotals(yea = 220, nay = 211, present = 0, notVoting = 4),
            path = "votes/congress119/house-1-17.json",
        )
        votesStore.saveIndex(
            VotesIndex(
                generatedAt = "2026-07-01T00:00:00Z",
                congress = 119,
                voteCount = 1,
                votes = listOf(ref),
            ),
        )
        val cutoff = Instant.parse("2026-03-15T00:00:00Z")

        val first = fetchBills(
            client = CongressClient(mockApiClient(), apiKey = "k"),
            congress = 119,
            cutoff = cutoff,
            nowIso = "2026-05-15T00:00:00Z",
            manifestStore = store,
            errors = ErrorCollector(),
            votesStore = votesStore,
        )
        val enriched = first.finalManifest.bills.single { it.id == "hr1-119" }
        assertEquals(listOf(ref), enriched.votes)

        // Second run rebuilds the identical record: the already-attached
        // votes must not make the merge count it as updated.
        val second = fetchBills(
            client = CongressClient(mockApiClient(), apiKey = "k"),
            congress = 119,
            cutoff = cutoff,
            nowIso = "2026-05-16T00:00:00Z",
            manifestStore = store,
            errors = ErrorCollector(),
            votesStore = votesStore,
        )
        assertEquals(0, second.mergeStats.updated)
        assertEquals(1, second.mergeStats.unchanged)
        assertEquals(listOf(ref), second.finalManifest.bills.single { it.id == "hr1-119" }.votes)
    }

    @Test fun bounded_run_enriches_only_up_to_maxNew_and_defers_the_rest() = runTest {
        val detailHits = mutableListOf<String>()
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        val cutoff = Instant.parse("2026-03-15T00:00:00Z")

        val result = fetchBills(
            client = CongressClient(mockEnactedBills(listOf(1, 2, 3), detailHits), apiKey = "k"),
            congress = 119,
            cutoff = cutoff,
            nowIso = "2026-05-15T00:00:00Z",
            manifestStore = store,
            errors = ErrorCollector(),
            maxNew = 2,
        )

        // Only the two newest-listed bills are enriched; the third is deferred.
        assertEquals(3, result.evaluated)
        assertEquals(2, result.keptRecords.size)
        assertEquals(1, result.newBillsDeferred)
        assertEquals(setOf("/v3/bill/119/hr/1", "/v3/bill/119/hr/2"), detailHits.toSet())
        assertEquals(setOf("hr1-119", "hr2-119"), result.finalManifest.bills.map { it.id }.toSet())
    }

    @Test fun bounded_run_skips_bills_already_in_the_manifest_for_free() = runTest {
        val detailHits = mutableListOf<String>()
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        val cutoff = Instant.parse("2026-03-15T00:00:00Z")

        // First bounded run (budget 1) enriches only the newest bill.
        val first = fetchBills(
            client = CongressClient(mockEnactedBills(listOf(1, 2, 3), detailHits), apiKey = "k"),
            congress = 119,
            cutoff = cutoff,
            nowIso = "2026-05-15T00:00:00Z",
            manifestStore = store,
            errors = ErrorCollector(),
            maxNew = 1,
        )
        assertEquals(setOf("/v3/bill/119/hr/1"), detailHits.toSet())
        assertEquals(2, first.newBillsDeferred)

        // Second run resumes: hr1 is on disk and skipped for free, so the
        // budget is spent on the next new bill (hr2), not re-fetching hr1.
        detailHits.clear()
        val second = fetchBills(
            client = CongressClient(mockEnactedBills(listOf(1, 2, 3), detailHits), apiKey = "k"),
            congress = 119,
            cutoff = cutoff,
            nowIso = "2026-05-16T00:00:00Z",
            manifestStore = store,
            errors = ErrorCollector(),
            maxNew = 1,
        )
        assertEquals(setOf("/v3/bill/119/hr/2"), detailHits.toSet())
        assertEquals(1, second.newBillsDeferred)
        assertEquals(1, second.mergeStats.added)
        assertEquals(setOf("hr1-119", "hr2-119"), second.finalManifest.bills.map { it.id }.toSet())
    }

    @Test fun uncapped_run_enriches_every_bill_and_defers_none() = runTest {
        val detailHits = mutableListOf<String>()
        val result = fetchBills(
            client = CongressClient(mockEnactedBills(listOf(1, 2, 3), detailHits), apiKey = "k"),
            congress = 119,
            cutoff = Instant.parse("2026-03-15T00:00:00Z"),
            nowIso = "2026-05-15T00:00:00Z",
            manifestStore = FileBillsManifestStore(FakeFileSystem(), "/out".toPath()),
            errors = ErrorCollector(),
            // maxNew defaults to NO_ENRICHMENT_CAP.
        )
        assertEquals(0, result.newBillsDeferred)
        assertEquals(3, result.keptRecords.size)
        assertEquals(3, detailHits.size)
    }
}
