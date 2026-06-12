package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.http.HudClient
import com.informedcitizen.pipeline.http.PipelineHttpConfig
import com.informedcitizen.pipeline.http.configurePipelineForTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mirrors Python `test_zip_crosswalk_build.py`'s API-mode coverage. */

private fun json(text: String): JsonObject =
    Json.parseToJsonElement(text) as JsonObject

/**
 * MockEngine serving per-state HUD responses; states absent from
 * [responses] return 404 (which HudClient maps to an empty body — the
 * builder counts it as a miss via failed result extraction).
 */
private fun mockHud(
    responses: Map<String, String>,
    onParams: ((Map<String, String>) -> Unit)? = null,
): HudClient {
    val http = HttpClient(MockEngine) {
        configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
        engine {
            addHandler { request ->
                val params = request.url.parameters.entries()
                    .associate { (k, v) -> k to v.single() }
                onParams?.invoke(params)
                val state = params.getValue("query")
                val body = responses[state]
                if (body == null) {
                    respond("not found", HttpStatusCode.NotFound)
                } else {
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
    }
    return HudClient(http, apiToken = "stub")
}

class BuildZipCrosswalkTest {
    @Test fun inverts_state_query_to_zip_and_normalizes_districts() = runTest {
        val hud = mockHud(
            mapOf(
                "AR" to """{"data":{"results":[
                    {"zip":"72101","cd":"0501","res_ratio":0.9},
                    {"zip":"72102","cd":"0501","res_ratio":0.1},
                    {"zip":"72102","cd":"0502","res_ratio":0.5},
                    {"zip":"72103","cd":"0502","res_ratio":0.5}
                ]}}""",
                "VT" to """{"data":{"results":[{"zip":"05001","cd":"5000","res_ratio":1.0}]}}""",
                "DC" to """{"data":{"results":[{"zip":"20001","cd":"1198","res_ratio":1.0}]}}""",
            ),
        )
        val result = buildZipCrosswalkFromApi(
            hud,
            year = 2024,
            quarter = 4,
            stateQueries = listOf("AR", "VT", "DC"),
        )
        val data = result.byZip
        assertEquals(ZipCrosswalkEntry("AR", listOf(1)), data.getValue("72101"))
        assertEquals(ZipCrosswalkEntry("AR", listOf(1, 2)), data.getValue("72102"))
        assertEquals(ZipCrosswalkEntry("AR", listOf(2)), data.getValue("72103"))
        assertEquals(ZipCrosswalkEntry("VT", listOf(0)), data.getValue("05001")) // at-large
        assertEquals(ZipCrosswalkEntry("DC", listOf(0)), data.getValue("20001")) // delegate
        assertEquals(3, result.statesFetched)
        assertEquals(0, result.misses)
    }

    @Test fun no_zips_collected_throws_rather_than_writing_empty_asset() = runTest {
        val hud = mockHud(emptyMap()) // every state 404s
        val errors = ErrorCollector()
        val e = assertFailsWith<RuntimeException> {
            buildZipCrosswalkFromApi(hud, stateQueries = listOf("AR"), errors = errors)
        }
        assertTrue("empty asset" in (e.message ?: ""), "unexpected message: ${e.message}")
        assertTrue(errors.hasErrors)
    }

    @Test fun skips_rows_with_unparseable_cd() = runTest {
        val hud = mockHud(
            mapOf(
                "AR" to """{"data":{"results":[
                    {"zip":"72101","cd":"0501"},
                    {"zip":"09501","cd":"**"},
                    {"zip":"72102","cd":"0502"}
                ]}}""",
            ),
        )
        val result = buildZipCrosswalkFromApi(hud, stateQueries = listOf("AR"))
        assertEquals(ZipCrosswalkEntry("AR", listOf(1)), result.byZip.getValue("72101"))
        assertEquals(ZipCrosswalkEntry("AR", listOf(2)), result.byZip.getValue("72102"))
        assertFalse("09501" in result.byZip)
    }

    @Test fun omits_year_quarter_params_by_default() = runTest {
        val captured = mutableListOf<Map<String, String>>()
        val hud = mockHud(
            mapOf("AR" to """{"data":{"results":[{"zip":"72101","cd":"0501"}]}}"""),
            onParams = { captured.add(it) },
        )
        buildZipCrosswalkFromApi(hud, stateQueries = listOf("AR"))
        assertEquals(1, captured.size)
        assertEquals(mapOf("type" to "5", "query" to "AR"), captured.single())
    }

    @Test fun includes_year_quarter_params_when_provided() = runTest {
        val captured = mutableListOf<Map<String, String>>()
        val hud = mockHud(
            mapOf("AR" to """{"data":{"results":[{"zip":"72101","cd":"0501"}]}}"""),
            onParams = { captured.add(it) },
        )
        buildZipCrosswalkFromApi(hud, year = 2024, quarter = 4, stateQueries = listOf("AR"))
        val sent = captured.single()
        assertEquals("2024", sent["year"])
        assertEquals("4", sent["quarter"])
    }

    @Test fun normalize_cd_code_handles_geoids_and_specials() {
        assertEquals(1, normalizeCdCode("0501"))
        assertEquals(12, normalizeCdCode("0512"))
        assertEquals(0, normalizeCdCode("5000")) // Vermont at-large
        assertEquals(0, normalizeCdCode("1198")) // DC delegate
        assertEquals(0, normalizeCdCode("0000"))
    }

    @Test fun normalize_cd_code_returns_null_for_unparseable() {
        assertNull(normalizeCdCode("**"))
        assertNull(normalizeCdCode("01**"))
        assertNull(normalizeCdCode(""))
        assertNull(normalizeCdCode("abcd"))
    }

    @Test fun extract_results_handles_alternate_shapes() {
        val dataResults = json("""{"data":{"results":[{"zip":"1"}]}}""")
        val topLevel = json("""{"results":[{"zip":"2"}]}""")
        assertEquals(listOf(json("""{"zip":"1"}""")), extractHudResults(dataResults))
        assertEquals(listOf(json("""{"zip":"2"}""")), extractHudResults(topLevel))
    }

    @Test fun extract_zip_handles_key_variants_and_pads() {
        assertEquals("12345", extractHudZip(json("""{"zip":"12345"}""")))
        assertEquals("12345", extractHudZip(json("""{"ZIP":"12345"}""")))
        assertEquals("12345", extractHudZip(json("""{"zipcode":12345}""")))
        assertEquals("00601", extractHudZip(json("""{"zip":601}""")))
        assertNull(extractHudZip(json("""{"foo":"bar"}""")))
    }

    @Test fun extract_cd_value_handles_key_variants() {
        assertEquals("0501", extractHudCdValue(json("""{"cd":"0501"}""")))
        assertEquals("0501", extractHudCdValue(json("""{"geoid":"0501"}""")))
        assertNull(extractHudCdValue(json("""{"foo":"bar"}""")))
    }

    @Test fun encodes_compact_json_matching_python_separators() {
        val byZip = linkedMapOf(
            "72101" to ZipCrosswalkEntry("AR", listOf(1, 2)),
            "05001" to ZipCrosswalkEntry("VT", listOf(0)),
        )
        assertEquals(
            """{"72101":{"state":"AR","districts":[1,2]},"05001":{"state":"VT","districts":[0]}}""",
            encodeZipCrosswalk(byZip),
        )
    }

    @Test fun store_writes_asset_without_trailing_newline() {
        val fs = FakeFileSystem()
        val store = FileZipCrosswalkStore(fs, "/assets/zip_to_cd.json".toPath())
        val path = store.save(linkedMapOf("72101" to ZipCrosswalkEntry("AR", listOf(1))))
        val text = fs.source(path).buffer().use { it.readUtf8() }
        assertEquals("""{"72101":{"state":"AR","districts":[1]}}""", text)
    }
}
