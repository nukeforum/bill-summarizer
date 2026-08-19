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
 *
 * For the same reason the key is normalized before it reaches the
 * header builder: Ktor's `IllegalHeaderValueException` quotes the whole
 * rejected value in its message, so a key carrying a stray control
 * character (typically the trailing newline from
 * `export CONGRESS_API_KEY=$(cat key.txt)`) would land verbatim in a
 * recorded [com.informedcitizen.pipeline.ErrorCollector] entry and be
 * printed by the CLI. Surrounding whitespace is trimmed and anything
 * still unusable as a header value throws [IllegalArgumentException]
 * with an app-authored message that contains no part of the key —
 * the same closed-outcome contract the Android key validator maps to
 * its `Malformed` result.
 */
class CongressClient(
    private val client: HttpClient,
    apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val headerApiKey: String by lazy { normalizeApiKey(apiKey) }

    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonObject {
        val response: HttpResponse = client.get(baseUrl + path) {
            accept(ContentType.Application.Json)
            header(API_KEY_HEADER, headerApiKey)
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

        internal const val BLANK_KEY_MESSAGE: String =
            "Congress.gov API key is blank."

        internal const val UNSENDABLE_KEY_MESSAGE: String =
            "Congress.gov API key cannot be sent as an HTTP header: it contains a " +
                "control character, typically a line break from a multi-line paste " +
                "or a trailing newline. The key itself is withheld from this message."

        /**
         * Trim surrounding whitespace off [rawKey] and reject anything that
         * still cannot be an HTTP header value. Throws
         * [IllegalArgumentException] whose message names the problem but
         * never any part of [rawKey].
         */
        fun normalizeApiKey(rawKey: String): String {
            val key = rawKey.trim()
            require(key.isNotEmpty()) { BLANK_KEY_MESSAGE }
            require(key.none(::isUnsendableInHeader)) { UNSENDABLE_KEY_MESSAGE }
            return key
        }

        /**
         * Every character Ktor's `HttpHeaders.checkHeaderValue` rejects
         * (anything below the space, tab excepted), plus DEL — which Ktor
         * lets through but no Congress.gov key contains.
         */
        private fun isUnsendableInHeader(c: Char): Boolean = c < ' ' || c == '\u007F'
    }
}

class CongressApiException(
    val status: Int,
    val path: String,
    val body: String,
) : RuntimeException("Congress.gov $path returned HTTP $status: ${body.take(200)}")
