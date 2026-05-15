package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.PipelineHttpConfig
import com.informedcitizen.pipeline.http.configurePipelineForTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

class RecentBillsListTest {
    @Test fun fetches_a_single_short_page_and_stops() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler {
                    calls++
                    respond(
                        """{"bills": [
                          {"type": "hr", "number": "1"},
                          {"type": "s", "number": "2"}
                        ]}""",
                        HttpStatusCode.OK, jsonHeaders(),
                    )
                }
            }
        }
        val congress = CongressClient(client, apiKey = "k")
        val bills = listRecentBills(congress, 119, Instant.parse("2026-03-15T00:00:00Z"), maxPages = 8)
        assertEquals(2, bills.size)
        assertEquals(1, calls) // short page (2 < 250), stopped after first request
    }

    @Test fun fetches_multiple_pages_until_short_page() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler { request ->
                    calls++
                    // Full first page (250 items); short second page (3 items).
                    val items = if (calls == 1) {
                        (1..250).joinToString(",") { """{"type":"hr","number":"$it"}""" }
                    } else {
                        """{"type":"hr","number":"251"},{"type":"hr","number":"252"},{"type":"hr","number":"253"}"""
                    }
                    respond("""{"bills":[$items]}""", HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val congress = CongressClient(client, apiKey = "k")
        val bills = listRecentBills(congress, 119, Instant.parse("2026-03-15T00:00:00Z"), maxPages = 8)
        assertEquals(253, bills.size)
        assertEquals(2, calls)
    }

    @Test fun stops_at_max_pages() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler {
                    calls++
                    val items = (1..250).joinToString(",") { """{"type":"hr","number":"$it"}""" }
                    respond("""{"bills":[$items]}""", HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val congress = CongressClient(client, apiKey = "k")
        val bills = listRecentBills(congress, 119, Instant.parse("2026-03-15T00:00:00Z"), maxPages = 3)
        assertEquals(750, bills.size)
        assertEquals(3, calls)
    }

    @Test fun fromDateTime_is_seconds_precision_iso8601() = runTest {
        var capturedUrl: String? = null
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler { request ->
                    capturedUrl = request.url.toString()
                    respond("""{"bills":[]}""", HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val congress = CongressClient(client, apiKey = "k")
        // Pass a cutoff with fractional seconds; ensure we send seconds-precision.
        listRecentBills(congress, 119, Instant.parse("2026-03-15T00:00:00.987Z"))
        val url = capturedUrl ?: error("no request captured")
        // Ktor URL-encodes the colons in the query string (`:` → `%3A`).
        // Decode for the assertion to keep it readable.
        val decoded = url.replace("%3A", ":")
        assertTrue("fromDateTime=2026-03-15T00:00:00Z" in decoded, decoded)
        assertTrue(".987" !in decoded, "fromDateTime should not include fractional seconds: $decoded")
    }

    @Test fun empty_first_page_returns_empty_list() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler {
                    calls++
                    respond("""{"bills":[]}""", HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val congress = CongressClient(client, apiKey = "k")
        val bills = listRecentBills(congress, 119, Instant.parse("2026-03-15T00:00:00Z"))
        assertEquals(0, bills.size)
        assertEquals(1, calls)
    }
}
