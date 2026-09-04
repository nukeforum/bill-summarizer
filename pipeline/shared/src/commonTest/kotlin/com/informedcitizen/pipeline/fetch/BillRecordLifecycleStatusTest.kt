package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.PipelineHttpConfig
import com.informedcitizen.pipeline.http.configurePipelineForTest
import com.informedcitizen.pipeline.model.LifecycleStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pre-floor lifecycle status on a built bill record (backlog #39, #116).
 *
 * Mirrors Python's `data-pipeline/tests/test_bill_lifecycle_status.py` case for
 * case — same action texts, same expectations — so the two pipelines cannot
 * silently drift apart on the field that gates the #75 parity week.
 */
class BillRecordLifecycleStatusTest {
    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    /**
     * Every enrichment endpoint answers empty and the detail payload is fixed,
     * so the only thing a test varies is the summary's `latestAction.text` —
     * the Kotlin twin of Python's `_ActionTextClient`.
     */
    private fun actionTextClient(policyArea: String? = null): CongressClient {
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler { request ->
                    val path = request.url.encodedPath
                    val body = when {
                        path.endsWith("/summaries") -> """{"summaries":[]}"""
                        path.endsWith("/text") -> """{"textVersions":[]}"""
                        path.endsWith("/subjects") -> """{"subjects":{"legislativeSubjects":[]}}"""
                        else -> {
                            val policy = policyArea?.let { ""","policyArea":{"name":"$it"}""" } ?: ""
                            """{"bill":{"title":"Stub title","introducedDate":"2025-01-01",
                              "sponsors":[{"fullName":"Sen. Stub, S. [D-XX]","party":"D","state":"XX"}],
                              "titles":[]$policy}}"""
                        }
                    }
                    respond(body, HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        return CongressClient(client, apiKey = "k")
    }

    // Action texts are plain prose (no quotes or backslashes), so embedding
    // them directly keeps the fixture readable and JSON-valid.
    private fun summary(actionText: String): JsonObject = Json.parseToJsonElement(
        """{"type":"hr","number":"1","title":"",
          "latestAction":{"text":"$actionText","actionDate":"2025-06-01"}}"""
    ) as JsonObject

    private suspend fun record(
        actionText: String,
        outcome: String = "enacted",
        policyArea: String? = null,
    ) = buildBillRecord(actionTextClient(policyArea), 119, summary(actionText), outcome)

    // ---------- each lifecycle stage reaches the record --------------------

    @Test fun status_introduced() = runTest {
        assertEquals(LifecycleStatus.INTRODUCED, record("Introduced in House").lifecycleStatus)
    }

    @Test fun status_in_committee() = runTest {
        assertEquals(
            LifecycleStatus.IN_COMMITTEE,
            record("Referred to the House Committee on Ways and Means.").lifecycleStatus,
        )
    }

    @Test fun status_reported() = runTest {
        assertEquals(
            LifecycleStatus.REPORTED,
            record("Reported by the Committee on Finance. H. Rept. 119-42.").lifecycleStatus,
        )
    }

    @Test fun status_reported_beats_in_committee() = runTest {
        // A reported action still names the committee; "reported" must win.
        assertEquals(
            LifecycleStatus.REPORTED,
            record("Placed on the Union Calendar, Calendar No. 88.").lifecycleStatus,
        )
    }

    // ---------- precedence: a terminal outcome beats a lifecycle status ----

    @Test fun terminal_outcome_leaves_status_unset() = runTest {
        // classifyBillStatus owns the precedence: an enacted bill carries no
        // lifecycle status, so the field stays null and the key is omitted on
        // write (see ManifestIOTest).
        assertNull(record("Became Public Law No: 119-12.").lifecycleStatus)
    }

    @Test fun terminal_outcome_wins_even_when_lifecycle_text_also_matches() = runTest {
        // Action text satisfying BOTH rule sets — "Passed House" is terminal,
        // "referred to the Committee" is a lifecycle match. The outcome wins.
        assertNull(
            record(
                "Passed House. Referred to the Committee on Homeland Security.",
                outcome = "passed_house",
            ).lifecycleStatus,
        )
    }

    @Test fun unrecognized_action_text_leaves_status_unset() = runTest {
        assertNull(record("Some unrecognized action text.").lifecycleStatus)
    }
}
