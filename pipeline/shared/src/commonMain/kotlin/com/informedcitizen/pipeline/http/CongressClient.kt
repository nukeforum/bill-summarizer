package com.informedcitizen.pipeline.http

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject

/**
 * Thin Ktor wrapper around the Congress.gov API. Mirrors the Python
 * `_common.CongressClient.get` contract:
 *
 * - Prepends [baseUrl] to the path and authenticates with the
 *   [API_KEY_HEADER] request header.
 * - 404 returns an empty [JsonObject] (Python: empty dict). Don't
 *   treat as an error — the API returns 404 for missing detail rows
 *   the orchestrator can skip.
 * - Other non-2xx statuses throw [CongressApiException]. The retry
 *   plugin installed in [configurePipeline] retries 5xx and
 *   transient exceptions according to [PipelineHttpConfig].
 *
 * Returns the raw `JsonObject` for the orchestrator to decode into
 * typed shapes (e.g. via kotlinx-serialization on `BillsManifest`).
 *
 * The key travels in a header, never in the query string. Congress.gov
 * accepts both, but a URL carrying the key ends up inside Ktor's
 * timeout-exception messages (`"…expired [url=…]"`), which the app's
 * BYOK path forwards to crash reporting and renders in the UI. Keeping
 * it out of the URL means no exception message, log line, or retry
 * trace can ever contain it. Do not move it back into `parameters`.
 */
class CongressClient(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonObject {
        val response: HttpResponse = client.get(baseUrl + path) {
            accept(ContentType.Application.Json)
            header(API_KEY_HEADER, apiKey)
            url {
                for ((k, v) in params) parameters.append(k, v)
            }
        }
        if (response.status == HttpStatusCode.NotFound) {
            return JsonObject(emptyMap())
        }
        if (!response.status.isSuccess()) {
            throw CongressApiException(
                status = response.status.value,
                path = path,
                body = response.bodyAsText(),
            )
        }
        return response.body()
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.congress.gov/v3"

        /** Congress.gov's header-based alternative to the `api_key` query parameter. */
        const val API_KEY_HEADER: String = "X-Api-Key"
    }
}

class CongressApiException(
    val status: Int,
    val path: String,
    val body: String,
) : RuntimeException("Congress.gov $path returned HTTP $status: ${body.take(200)}")
