// pipeline/shared/src/commonMain/kotlin/com/informedcitizen/pipeline/http/LegislatorsClient.kt
package com.informedcitizen.pipeline.http

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Fetches files hosted on the gh-pages branch of
 * unitedstates/congress-legislators. No auth.
 *
 * The gh-pages branch publishes the source-of-truth YAML files as JSON
 * (e.g. legislators-current.yaml → legislators-current.json). The KMP
 * pipeline reads JSON because no KMP YAML parser exists; Python reads
 * the YAML directly.
 *
 * Non-2xx (including 404) throws [LegislatorsApiException] — the Phase 1
 * caller treats the failure as non-fatal for the affected field
 * (records contact_form/website as null when [fetchCurrent] fails;
 * records socials=[] when [fetchSocialMedia] fails).
 */
class LegislatorsClient(
    private val client: HttpClient,
    private val currentUrl: String = DEFAULT_CURRENT_URL,
    private val socialMediaUrl: String = DEFAULT_SOCIAL_MEDIA_URL,
) {
    suspend fun fetchCurrent(): String = fetch(currentUrl)

    suspend fun fetchSocialMedia(): String = fetch(socialMediaUrl)

    private suspend fun fetch(url: String): String {
        val response: HttpResponse = client.get(url)
        if (!response.status.isSuccess()) {
            throw LegislatorsApiException(
                status = response.status.value,
                url = url,
                body = response.bodyAsText(),
            )
        }
        return response.bodyAsText()
    }

    companion object {
        const val DEFAULT_CURRENT_URL: String =
            "https://unitedstates.github.io/congress-legislators/legislators-current.json"
        const val DEFAULT_SOCIAL_MEDIA_URL: String =
            "https://unitedstates.github.io/congress-legislators/legislators-social-media.json"

        // Back-compat alias for the old single-URL constructor parameter name.
        @Deprecated(
            message = "Use DEFAULT_CURRENT_URL.",
            replaceWith = ReplaceWith("DEFAULT_CURRENT_URL"),
            level = DeprecationLevel.HIDDEN,
        )
        const val DEFAULT_SOURCE_URL: String = DEFAULT_CURRENT_URL
    }
}

class LegislatorsApiException(
    val status: Int,
    val url: String,
    val body: String,
) : RuntimeException("legislators-current $url returned HTTP $status: ${body.take(200)}")
