package com.informedcitizen.pipeline.http

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Fetches Senate roll-call documents (senate.gov LIS XML) and the
 * congress-legislators YAML files. No auth.
 *
 * Unlike [CongressClient] this is a plain text fetch keyed by full URL
 * — mirroring Python `fetch_votes._fetch` — because the URL builders
 * live next to the vote parsers in the fetch package
 * (`senateVoteMenuUrl` / `senateVoteSourceUrl`).
 *
 * Non-2xx throws [SenateVotesApiException]; the fetch-votes driver
 * treats a 404 vote menu as an unpublished session (skipped) and any
 * per-vote failure as retryable on the next run.
 */
class SenateVotesClient(private val client: HttpClient) {
    suspend fun fetch(url: String): String {
        val response: HttpResponse = client.get(url)
        if (!response.status.isSuccess()) {
            throw SenateVotesApiException(
                status = response.status.value,
                url = url,
                body = response.bodyAsText(),
            )
        }
        return response.bodyAsText()
    }
}

class SenateVotesApiException(
    val status: Int,
    val url: String,
    val body: String,
) : RuntimeException("senate votes fetch $url returned HTTP $status: ${body.take(200)}")
