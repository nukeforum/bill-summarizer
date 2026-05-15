package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.PipelineHttpConfig
import com.informedcitizen.pipeline.http.configurePipelineForTest
import com.informedcitizen.pipeline.model.Outcome
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")
private fun parse(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

class BillRecordBuilderTest {
    @Test fun assembles_bill_from_detail_summaries_and_text_endpoints() = runTest {
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler { request ->
                    val path = request.url.encodedPath
                    val body = when {
                        path.endsWith("/summaries") -> """
                            {"summaries":[
                              {"updateDate":"2026-03-01","text":"OLD"},
                              {"updateDate":"2026-04-01","text":"LATEST"}
                            ]}
                        """.trimIndent()
                        path.endsWith("/text") -> """
                            {"textVersions":[
                              {"date":"2026-04-01","formats":[
                                {"type":"Formatted Text","url":"https://x/bill.htm"},
                                {"type":"Formatted XML","url":"https://x/bill.xml"},
                                {"type":"PDF","url":"https://x/bill.pdf"}
                              ]}
                            ]}
                        """.trimIndent()
                        else -> """
                            {"bill":{
                              "title":"A Bill For All Purposes",
                              "titles":[{"titleType":"Short Title","title":"AllPurposes Act"}],
                              "introducedDate":"2026-01-15",
                              "sponsors":[{"fullName":"Rep. Smith, Adrian [R-NE-3]","party":"Republican","state":"NE"}]
                            }}
                        """.trimIndent()
                    }
                    respond(body, HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val cc = CongressClient(client, apiKey = "k")
        val summary = parse(
            """{"type":"hr","number":"1234","title":"summary fallback title",
              "latestAction":{"text":"Became Public Law","actionDate":"2026-04-01"}}"""
        )
        val bill = buildBillRecord(cc, 119, summary, "enacted")
        assertEquals("hr1234-119", bill.id)
        assertEquals(119, bill.congress)
        assertEquals("hr", bill.type)
        assertEquals("1234", bill.number)
        assertEquals("A Bill For All Purposes", bill.title)
        assertEquals("AllPurposes Act", bill.shortTitle)
        assertEquals("Rep. Smith, Adrian", bill.sponsor.name) // suffix stripped
        assertEquals("R", bill.sponsor.party)
        assertEquals("NE", bill.sponsor.state)
        assertEquals("2026-01-15", bill.introducedDate)
        assertEquals("2026-04-01", bill.latestAction.date)
        assertEquals(Outcome.ENACTED, bill.outcome)
        assertEquals("LATEST", bill.summaryCrs)
        assertEquals("https://x/bill.htm", bill.textUrlHtml)
        assertEquals("https://x/bill.xml", bill.textUrlXml)
        assertEquals("https://x/bill.pdf", bill.textUrlPdf)
        assertEquals("https://www.congress.gov/bill/119th-congress/house-bill/1234", bill.congressGovUrl)
    }

    @Test fun falls_back_to_summary_title_when_detail_title_missing() = runTest {
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler {
                    respond("""{"bill":{}}""", HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val cc = CongressClient(client, apiKey = "k")
        val summary = parse(
            """{"type":"hr","number":"1","title":"fallback title from summary",
              "latestAction":{"text":"Passed House","actionDate":"2026-04-01"}}"""
        )
        val bill = buildBillRecord(cc, 119, summary, "passed_house")
        assertEquals("fallback title from summary", bill.title)
        assertNull(bill.shortTitle)
    }

    @Test fun no_summaries_endpoint_response_yields_null_summaryCrs() = runTest {
        val client = HttpClient(MockEngine) {
            configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
            engine {
                addHandler { request ->
                    val body = if (request.url.encodedPath.endsWith("/summaries")) {
                        """{}"""  // no `summaries` field
                    } else {
                        """{"bill":{}}"""
                    }
                    respond(body, HttpStatusCode.OK, jsonHeaders())
                }
            }
        }
        val cc = CongressClient(client, apiKey = "k")
        val summary = parse("""{"type":"s","number":"42","latestAction":{"text":"Became law","actionDate":"2026-04-01"}}""")
        val bill = buildBillRecord(cc, 119, summary, "enacted")
        assertNull(bill.summaryCrs)
        assertNull(bill.textUrlHtml)
    }
}
