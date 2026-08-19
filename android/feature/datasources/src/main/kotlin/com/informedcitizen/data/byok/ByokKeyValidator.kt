package com.informedcitizen.data.byok

import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.createPipelineHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess

sealed interface KeyValidationResult {
    data object Valid : KeyValidationResult

    /** The API explicitly rejected the key (401/403). */
    data class Invalid(val httpStatus: Int) : KeyValidationResult

    /** Couldn't reach the API — key validity unknown. */
    data class Unreachable(val message: String) : KeyValidationResult
}

/**
 * Live-checks a Congress.gov key with the cheapest authenticated
 * request (`/bill?limit=1`). Same client stack the BYOK fetch uses, so
 * a Valid here means the fetch will authenticate. Only the status code
 * matters — the body is never parsed.
 *
 * The key goes in [CongressClient.API_KEY_HEADER], not the query string:
 * [KeyValidationResult.Unreachable] carries the exception message straight
 * to the screen, and Ktor's timeout messages embed the request URL. The
 * message is scrubbed on the way out as well — see [redactedDetail].
 */
class ByokKeyValidator(
    private val httpClientFactory: () -> HttpClient = { createPipelineHttpClient() },
) {
    suspend fun validateCongressKey(key: String): KeyValidationResult {
        val http = httpClientFactory()
        return try {
            val status = http.get("${CongressClient.DEFAULT_BASE_URL}/bill") {
                header(CongressClient.API_KEY_HEADER, key)
                parameter("limit", "1")
            }.status
            when {
                status.isSuccess() -> KeyValidationResult.Valid
                status.value == 401 || status.value == 403 ->
                    KeyValidationResult.Invalid(status.value)
                else -> KeyValidationResult.Unreachable("Congress.gov returned HTTP ${status.value}")
            }
        } catch (e: Exception) {
            KeyValidationResult.Unreachable(redactedDetail(e, key))
        } finally {
            http.close()
        }
    }
}
