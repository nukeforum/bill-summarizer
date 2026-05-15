package com.informedcitizen.pipeline.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

class HudClientTest {
    @Test
    fun get_sends_bearer_auth_header_and_returns_parsed_json() = runTest {
        var capturedAuth: String? = null
        var capturedUrl: String? = null
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler { request ->
                    capturedAuth = request.headers[HttpHeaders.Authorization]
                    capturedUrl = request.url.toString()
                    respond(
                        """{"results":[{"zip":"90210","cd":"30"}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders(),
                    )
                }
            }
        }
        val hud = HudClient(client, apiToken = "HUD_TOK")
        val body: JsonObject = hud.get(
            "/usps",
            params = mapOf("type" to "5", "query" to "CA"),
        )
        assertEquals("Bearer HUD_TOK", capturedAuth)
        val url = capturedUrl ?: error("no request captured")
        assertTrue("type=5" in url, url)
        assertTrue("query=CA" in url, url)
        assertTrue(url.startsWith("https://www.huduser.gov/hudapi/public/usps"), url)
        assertTrue("results" in body)
    }

    @Test
    fun get_returns_empty_object_on_404() = runTest {
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler { respond("not found", HttpStatusCode.NotFound) }
            }
        }
        val hud = HudClient(client, apiToken = "X")
        val body = hud.get("/usps")
        assertEquals(JsonObject(emptyMap()), body)
    }

    @Test
    fun get_throws_hud_api_exception_on_500() = runTest {
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0, retryCount = 1))
            engine {
                addHandler { respond("server is on fire", HttpStatusCode.InternalServerError) }
            }
        }
        val hud = HudClient(client, apiToken = "X")
        val ex = assertFailsWith<HudApiException> {
            hud.get("/usps")
        }
        assertEquals(500, ex.status)
        assertEquals("/usps", ex.path)
        assertTrue("server is on fire" in ex.body, ex.body)
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
        val hud = HudClient(client, apiToken = "X", baseUrl = "https://hud.test/api")
        hud.get("/lookup")
        val url = capturedUrl ?: error("no request captured")
        assertTrue(url.startsWith("https://hud.test/api/lookup"), url)
    }
}
