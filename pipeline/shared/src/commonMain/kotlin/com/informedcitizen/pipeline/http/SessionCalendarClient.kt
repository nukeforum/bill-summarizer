package com.informedcitizen.pipeline.http

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Fetches the two official `.gov` session-day feeds. No auth.
 *
 *  - House: USHOR voting-days iCalendar feed.
 *  - Senate: per-year XML schedule, replaced each November when
 *    leadership announces the following year's tentative schedule.
 *    Superseded years 404, which the builder treats as expected.
 *
 * Mirrors Python `build_session_calendar.py`'s `_fetch` (the shared
 * client already applies the same User-Agent and 30s timeout).
 */
class SessionCalendarClient(
    private val client: HttpClient,
    val houseUrl: String = DEFAULT_HOUSE_URL,
    private val senateUrlTemplate: String = DEFAULT_SENATE_URL_TEMPLATE,
) {
    fun senateUrlForYear(year: Int): String =
        senateUrlTemplate.replace("{year}", year.toString())

    suspend fun fetchHouseIcs(): String = fetch(houseUrl)

    suspend fun fetchSenateXml(year: Int): String = fetch(senateUrlForYear(year))

    private suspend fun fetch(url: String): String {
        val response: HttpResponse = client.get(url)
        if (!response.status.isSuccess()) {
            throw SessionCalendarApiException(
                status = response.status.value,
                url = url,
                body = response.bodyAsText(),
            )
        }
        return response.bodyAsText()
    }

    companion object {
        const val DEFAULT_HOUSE_URL: String =
            "https://votingdays.house.gov/voting-days.ics"
        const val DEFAULT_SENATE_URL_TEMPLATE: String =
            "https://www.senate.gov/legislative/{year}_schedule.xml"
    }
}

class SessionCalendarApiException(
    val status: Int,
    val url: String,
    val body: String,
) : RuntimeException("session-calendar feed $url returned HTTP $status: ${body.take(200)}")
