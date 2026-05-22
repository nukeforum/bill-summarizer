package com.informedcitizen.pipeline.http

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Fetches the raw `legislators-current.json` file hosted on the
 * gh-pages branch of unitedstates/congress-legislators. No auth.
 *
 * Default URL points at the JSON file Pages serves; the YAML lives on
 * the main branch but lacks a KMP parser, so the pipeline reads the
 * co-published JSON instead. See TODO "Shared Pipeline (KMP)" for the
 * rationale.
 *
 * Non-2xx (including 404) throws [LegislatorsApiException] — unlike
 * [CongressClient], there's no useful "empty" representation when the
 * full legislators file is missing, and the Phase 1 caller treats the
 * failure as non-fatal (records contact_form / website as null).
 */
class LegislatorsClient(
    private val client: HttpClient,
    private val sourceUrl: String = DEFAULT_SOURCE_URL,
) {
    suspend fun fetchCurrent(): String {
        val response: HttpResponse = client.get(sourceUrl)
        if (!response.status.isSuccess()) {
            throw LegislatorsApiException(
                status = response.status.value,
                url = sourceUrl,
                body = response.bodyAsText(),
            )
        }
        return response.bodyAsText()
    }

    companion object {
        const val DEFAULT_SOURCE_URL: String =
            "https://unitedstates.github.io/congress-legislators/legislators-current.json"
    }
}

class LegislatorsApiException(
    val status: Int,
    val url: String,
    val body: String,
) : RuntimeException("legislators-current $url returned HTTP $status: ${body.take(200)}")
