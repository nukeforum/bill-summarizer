package com.informedcitizen.pipeline.http

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject

/**
 * Thin Ktor wrapper around the HUD USPS ZIP-to-Congressional-district
 * crosswalk API (`https://www.huduser.gov/hudapi/public/usps`). Auth
 * is a bearer token rather than a query-string key.
 *
 * 404 maps to empty payload like [CongressClient]. Other non-2xx
 * throw [HudApiException].
 */
class HudClient(
    private val client: HttpClient,
    private val apiToken: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonObject {
        val response: HttpResponse = client.get(baseUrl + path) {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiToken")
            url {
                for ((k, v) in params) parameters.append(k, v)
            }
        }
        if (response.status == HttpStatusCode.NotFound) {
            return JsonObject(emptyMap())
        }
        if (!response.status.isSuccess()) {
            throw HudApiException(
                status = response.status.value,
                path = path,
                body = response.bodyAsText(),
            )
        }
        return response.body()
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://www.huduser.gov/hudapi/public"
    }
}

class HudApiException(
    val status: Int,
    val path: String,
    val body: String,
) : RuntimeException("HUD $path returned HTTP $status: ${body.take(200)}")
