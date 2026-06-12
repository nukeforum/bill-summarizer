package com.informedcitizen.data.byok

import com.informedcitizen.pipeline.http.createPipelineHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
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
 */
class ByokKeyValidator(
    private val httpClientFactory: () -> HttpClient = { createPipelineHttpClient() },
) {
    suspend fun validateCongressKey(key: String): KeyValidationResult {
        val http = httpClientFactory()
        return try {
            val status = http.get("$CONGRESS_BASE_URL/bill?limit=1&api_key=$key").status
            when {
                status.isSuccess() -> KeyValidationResult.Valid
                status.value == 401 || status.value == 403 ->
                    KeyValidationResult.Invalid(status.value)
                else -> KeyValidationResult.Unreachable("Congress.gov returned HTTP ${status.value}")
            }
        } catch (e: Exception) {
            KeyValidationResult.Unreachable(e.message ?: e::class.simpleName ?: "unknown error")
        } finally {
            http.close()
        }
    }

    private companion object {
        const val CONGRESS_BASE_URL = "https://api.congress.gov/v3"
    }
}
