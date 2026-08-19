package com.informedcitizen.data.byok

import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.createPipelineHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.IllegalHeaderValueException
import io.ktor.http.isSuccess

/**
 * The outcome of a live key check. Deliberately carries no free-form
 * text: the key being checked is raw user input, and anything this type
 * could carry ends up rendered directly beneath the password-masked
 * field that hides it. Only closed outcomes and an HTTP status cross
 * this boundary; the wording lives in the UI layer, where it is a
 * constant and cannot echo what the user typed.
 */
sealed interface KeyValidationResult {
    data object Valid : KeyValidationResult

    /** The API explicitly rejected the key (401/403). */
    data class Invalid(val httpStatus: Int) : KeyValidationResult

    /**
     * The key cannot be sent as an HTTP header at all — it carries a
     * control character, typically a line break from a multi-line paste.
     * No request was made.
     */
    data object Malformed : KeyValidationResult

    /**
     * Couldn't reach the API — key validity unknown. [httpStatus] is what
     * Congress.gov answered with, or null when the request never
     * completed at all.
     */
    data class Unreachable(val httpStatus: Int? = null) : KeyValidationResult
}

/**
 * Live-checks a Congress.gov key with the cheapest authenticated
 * request (`/bill?limit=1`). Same client stack the BYOK fetch uses, so
 * a Valid here means the fetch will authenticate. Only the status code
 * matters — the body is never parsed.
 *
 * The key goes in [CongressClient.API_KEY_HEADER], not the query string,
 * so nothing that quotes the request URL can carry it. Header validation
 * is the one shape that still can: Ktor's [IllegalHeaderValueException]
 * embeds the whole rejected value in its message, and OkHttp's equivalent
 * does the same. That is why no upstream message is ever returned from
 * here — a rejected value maps to [KeyValidationResult.Malformed] and the
 * message is dropped rather than scrubbed.
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
                else -> KeyValidationResult.Unreachable(status.value)
            }
        } catch (malformed: IllegalArgumentException) {
            KeyValidationResult.Malformed
        } catch (unreachable: Exception) {
            KeyValidationResult.Unreachable()
        } finally {
            http.close()
        }
    }
}
