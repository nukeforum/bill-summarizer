package com.informedcitizen.pipeline.http

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
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
 * - Prepends [baseUrl] to the path, appends `api_key` to the query.
 * - 404 returns an empty [JsonObject] (Python: empty dict). Don't
 *   treat as an error — the API returns 404 for missing detail rows
 *   the orchestrator can skip.
 * - Other non-2xx statuses throw [CongressApiException]. The retry
 *   plugin installed in [configurePipeline] retries 5xx and
 *   transient exceptions according to [PipelineHttpConfig].
 *
 * Returns the raw `JsonObject` for the orchestrator to decode into
 * typed shapes (e.g. via kotlinx-serialization on `BillsManifest`).
 */
class CongressClient(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonObject {
        val response: HttpResponse = client.get(baseUrl + path) {
            accept(ContentType.Application.Json)
            url {
                for ((k, v) in params) parameters.append(k, v)
                parameters.append("api_key", apiKey)
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
    }
}

class CongressApiException(
    val status: Int,
    val path: String,
    val body: String,
) : RuntimeException("Congress.gov $path returned HTTP $status: ${body.take(200)}")
