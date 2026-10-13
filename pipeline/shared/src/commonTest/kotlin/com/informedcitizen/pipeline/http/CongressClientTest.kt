package com.informedcitizen.pipeline.http

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.fetch.buildBillRecordsParallel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

/**
 * Every message in the failure, including anything an upstream library
 * wrapped. A leak that only shows up on `cause.cause` is still a leak:
 * the CLI prints stack traces and the app forwards throwables to crash
 * reporting.
 */
private fun Throwable.chainText(): String =
    generateSequence(this) { it.cause }
        .take(16)
        .joinToString("\n") { "${it::class.simpleName}: ${it.message} / ${it}" }

class CongressClientTest {
    @Test
    fun get_returns_parsed_json_object_on_200() = runTest {
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler {
                    respond(
                        content = """{"bill":{"number":"1234"}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }
            }
        }
        val congress = CongressClient(client, apiKey = "TEST_KEY")
        val body: JsonObject = congress.get("/bill/119/hr/1234")
        val bill = body["bill"] as JsonObject
        assertEquals("1234", bill["number"]!!.jsonPrimitive.content)
    }

    @Test
    fun get_sends_api_key_in_header_and_includes_user_agent() = runTest {
        var capturedUrl: String? = null
        var capturedUserAgent: String? = null
        var capturedApiKeyHeader: String? = null
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler { request ->
                    capturedUrl = request.url.toString()
                    capturedUserAgent = request.headers[HttpHeaders.UserAgent]
                    capturedApiKeyHeader = request.headers[CongressClient.API_KEY_HEADER]
                    respond("{}", HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val congress = CongressClient(client, apiKey = "S3CRET")
        congress.get("/bill/119/hr/1234", params = mapOf("format" to "json"))

        val url = capturedUrl ?: error("no request captured")
        assertEquals("S3CRET", capturedApiKeyHeader)
        assertTrue("format=json" in url, url)
        assertTrue(url.startsWith("https://api.congress.gov/v3/bill/119/hr/1234"), url)
        assertEquals(PipelineHttpConfig.DEFAULT_USER_AGENT, capturedUserAgent)
    }

    /**
     * The credential must never reach the URL. Ktor's timeout exceptions
     * embed `request.url.buildString()` verbatim, and the app forwards
     * those messages to crash reporting and to on-screen error text — so a
     * key in the query string is a credential leak, not a style nit.
     */
    @Test
    fun get_never_puts_the_api_key_in_the_url() = runTest {
        val key = "NOT_A_REAL_KEY_ABC123"
        var capturedUrl: String? = null
        var capturedQuery: String? = null
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler { request ->
                    capturedUrl = request.url.toString()
                    capturedQuery = request.url.parameters["api_key"]
                    respond("{}", HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val congress = CongressClient(client, apiKey = key)
        congress.get("/bill", params = mapOf("limit" to "1"))

        val url = capturedUrl ?: error("no request captured")
        assertNull(capturedQuery, "api_key query parameter must not be emitted")
        assertFalse(key in url, "api key leaked into the URL: $url")
    }

    /**
     * A keyfile-sourced `export CONGRESS_API_KEY=$(cat key.txt)` carries a
     * trailing newline. Ktor rejects that as a header value and quotes the
     * whole key back in the exception message, so the client normalizes
     * before the header builder ever sees it. The request must still go
     * out, authenticated, with the key nowhere near the URL.
     */
    @Test
    fun get_trims_surrounding_whitespace_off_the_key_and_still_authenticates() = runTest {
        val key = "NOT_A_REAL_KEY_ABC123"
        var capturedApiKeyHeader: String? = null
        var capturedUrl: String? = null
        var capturedQuery: String? = null
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler { request ->
                    capturedApiKeyHeader = request.headers[CongressClient.API_KEY_HEADER]
                    capturedUrl = request.url.toString()
                    capturedQuery = request.url.parameters["api_key"]
                    respond("{}", HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val congress = CongressClient(client, apiKey = "  $key\n")
        congress.get("/bill", params = mapOf("limit" to "1"))

        assertEquals(key, capturedApiKeyHeader)
        assertNull(capturedQuery, "api_key query parameter must not be emitted")
        assertFalse(key in (capturedUrl ?: ""), "api key leaked into the URL: $capturedUrl")
    }

    /**
     * A key that still cannot be a header value after trimming — a line
     * break in the middle, the shape a multi-line paste produces — must
     * fail with an app-authored message. Ktor's own
     * `IllegalHeaderValueException` embeds the entire rejected value, and
     * that message is what the CLI records and prints.
     */
    @Test
    fun get_rejects_an_unsendable_key_without_quoting_any_of_it() = runTest {
        val key = "NOT_A_REAL_KEY_ABC123"
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine { addHandler { respond("{}", HttpStatusCode.OK, jsonHeaders()) } }
        }
        val congress = CongressClient(client, apiKey = "$key\nTRAILING")

        val exc = assertFailsWith<IllegalArgumentException> {
            congress.get("/bill/119/hr/1234")
        }
        val text = exc.chainText()
        assertFalse(key in text, "api key leaked into the failure: $text")
        assertFalse("TRAILING" in text, "api key leaked into the failure: $text")
        assertTrue(CongressClient.UNSENDABLE_KEY_MESSAGE in text, text)
    }

    /**
     * A key copied out of a rendered HTML or PDF activation email picks up
     * characters that look like ASCII but are not: a non-breaking space
     * where a space was, a smart quote where an apostrophe was. Ktor lets
     * them through — it only rejects `c < ' '` — and OkHttp then throws
     * from `Headers.checkValue` with the whole key appended, because
     * `X-Api-Key` is not on its sensitive-header list. Interior ones
     * matter: `trim()` strips a trailing NBSP but not one in the middle.
     *
     * Asserted against [CongressClient.normalizeApiKey] directly rather
     * than through a request, because MockEngine never reaches OkHttp's
     * validator; the transport side is pinned by
     * `CongressClientOkHttpBoundaryTest` in jvmTest.
     */
    @Test
    fun normalize_rejects_an_interior_non_breaking_space_without_quoting_the_key() {
        val key = "NOT_A_REAL\u00A0KEY_ABC123"

        val exc = assertFailsWith<IllegalArgumentException> {
            CongressClient.normalizeApiKey(key)
        }
        val text = exc.chainText()
        assertFalse("NOT_A_REAL" in text, "api key leaked into the failure: $text")
        assertFalse("KEY_ABC123" in text, "api key leaked into the failure: $text")
        assertEquals(CongressClient.UNSENDABLE_KEY_MESSAGE, exc.message)
    }

    @Test
    fun normalize_rejects_an_interior_smart_quote_without_quoting_the_key() {
        val key = "NOT_A_REAL\u2019S_KEY_ABC123"

        val exc = assertFailsWith<IllegalArgumentException> {
            CongressClient.normalizeApiKey(key)
        }
        val text = exc.chainText()
        assertFalse("NOT_A_REAL" in text, "api key leaked into the failure: $text")
        assertFalse("S_KEY_ABC123" in text, "api key leaked into the failure: $text")
        assertEquals(CongressClient.UNSENDABLE_KEY_MESSAGE, exc.message)
    }

    @Test
    fun normalize_keeps_every_printable_ascii_key_and_only_trims_the_edges() {
        val key = "aBcD-1234_~!@#\$%^&*()+={}[]|;:'\",.<>/?"

        assertEquals(key, CongressClient.normalizeApiKey("  $key\n"))
    }

    @Test
    fun get_rejects_a_blank_key_without_making_a_request() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler {
                    calls++
                    respond("{}", HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val congress = CongressClient(client, apiKey = "   ")

        val exc = assertFailsWith<IllegalArgumentException> { congress.get("/bill") }
        assertEquals(CongressClient.BLANK_KEY_MESSAGE, exc.message)
        assertEquals(0, calls)
    }

    /**
     * The CLI failure path end to end: `buildBillRecordsParallel` records
     * `exc.message` into the [ErrorCollector], and `fetch-bills` prints
     * `renderSummary` to stderr — straight into CI logs. Nothing that
     * reaches that string may contain the credential.
     */
    @Test
    fun an_unsendable_key_never_reaches_the_rendered_cli_error_summary() = runTest {
        val key = "NOT_A_REAL_KEY_ABC123"
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine { addHandler { respond("{}", HttpStatusCode.OK, jsonHeaders()) } }
        }
        val congress = CongressClient(client, apiKey = "$key\nTRAILING")
        val errors = ErrorCollector()
        val summaries = listOf(
            buildJsonObject {
                put("type", "HR")
                put("number", "1234")
            } to "enacted",
        )

        val (records, failures) = buildBillRecordsParallel(
            client = congress,
            congress = 119,
            items = summaries,
            errors = errors,
        )

        assertTrue(records.isEmpty())
        assertEquals(1, failures)
        val summary = errors.renderSummary(label = "fetch_bills")
        assertTrue(CongressClient.UNSENDABLE_KEY_MESSAGE in summary, summary)
        assertFalse(key in summary, "api key leaked into the CLI error summary: $summary")
        assertFalse("TRAILING" in summary, "api key leaked into the CLI error summary: $summary")
    }

    @Test
    fun get_returns_empty_object_on_404_without_throwing() = runTest {
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler {
                    respond(
                        content = """{"error":"not found"}""",
                        status = HttpStatusCode.NotFound,
                        headers = jsonHeaders(),
                    )
                }
            }
        }
        val congress = CongressClient(client, apiKey = "X")
        val body = congress.get("/bill/119/hr/9999999")
        assertEquals(JsonObject(emptyMap()), body)
    }

    @Test
    fun get_throws_congress_api_exception_on_400() = runTest {
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler {
                    respond("bad request", HttpStatusCode.BadRequest)
                }
            }
        }
        val congress = CongressClient(client, apiKey = "X")
        val ex = assertFailsWith<CongressApiException> {
            congress.get("/bill/119/hr/garbled")
        }
        assertEquals(400, ex.status)
        assertEquals("/bill/119/hr/garbled", ex.path)
        assertTrue("bad request" in ex.body, ex.body)
    }

    @Test
    fun get_retries_on_503_then_succeeds() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler {
                    calls++
                    if (calls == 1) {
                        respond("upstream busy", HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders())
                    }
                }
            }
        }
        val congress = CongressClient(client, apiKey = "X")
        val body = congress.get("/bill/119/hr/1234")
        assertEquals(2, calls)
        assertTrue("ok" in body, body.toString())
    }

    @Test
    fun get_exhausts_retries_and_throws_on_persistent_5xx() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0, retryCount = 3))
            engine {
                addHandler {
                    calls++
                    respond("still busy", HttpStatusCode.ServiceUnavailable)
                }
            }
        }
        val congress = CongressClient(client, apiKey = "X")
        assertFailsWith<CongressApiException> {
            congress.get("/bill/119/hr/1234")
        }
        // retryCount=3 means 1 initial + 2 retries = 3 total attempts.
        assertEquals(3, calls)
    }

    @Test
    fun get_does_not_retry_on_404() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler {
                    calls++
                    respond("nope", HttpStatusCode.NotFound)
                }
            }
        }
        val congress = CongressClient(client, apiKey = "X")
        congress.get("/missing")
        assertEquals(1, calls)
    }

    @Test
    fun custom_base_url_is_honored() = runTest {
        var capturedUrl: String? = null
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler { request ->
                    capturedUrl = request.url.toString()
                    respond("{}", HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val congress = CongressClient(
            client = client,
            apiKey = "X",
            baseUrl = "https://example.test/v9",
        )
        congress.get("/ping")
        val url = capturedUrl ?: error("no request captured")
        assertTrue(url.startsWith("https://example.test/v9/ping"), url)
    }
}
