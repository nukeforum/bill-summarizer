package com.informedcitizen.pipeline.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

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
    fun get_appends_api_key_to_query_and_includes_user_agent() = runTest {
        var capturedUrl: String? = null
        var capturedUserAgent: String? = null
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler { request ->
                    capturedUrl = request.url.toString()
                    capturedUserAgent = request.headers[HttpHeaders.UserAgent]
                    respond("{}", HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val congress = CongressClient(client, apiKey = "S3CRET")
        congress.get("/bill/119/hr/1234", params = mapOf("format" to "json"))

        val url = capturedUrl ?: error("no request captured")
        assertTrue("api_key=S3CRET" in url, url)
        assertTrue("format=json" in url, url)
        assertTrue(url.startsWith("https://api.congress.gov/v3/bill/119/hr/1234"), url)
        assertEquals(PipelineHttpConfig.DEFAULT_USER_AGENT, capturedUserAgent)
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
