package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.http.PipelineHttpConfig
import com.informedcitizen.pipeline.http.SenateVotesApiException
import com.informedcitizen.pipeline.http.SenateVotesClient
import com.informedcitizen.pipeline.http.configurePipelineForTest
import com.informedcitizen.pipeline.model.RollCallVote
import com.informedcitizen.pipeline.model.VotesIndex
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for [fetchVotes] with mocked senate.gov fetches.
 * Mirrors Python `test_fetch_votes_main.py`: the detail XML is the
 * real fixture for roll call 119-1-618; the vote menu is built inline
 * so tests control exactly which roll calls the driver sees.
 */

private const val NOW_ISO = "2026-07-22T00:00:00Z"

private val MENU_1_URL = senateVoteMenuUrl(119, 1)
private val DETAIL_618_URL = senateVoteSourceUrl(119, 1, 618)

private fun menuXml(congress: Int, session: Int, voteNumbers: List<Int>): String {
    val votes = voteNumbers.joinToString("") {
        "<vote><vote_number>${it.toString().padStart(5, '0')}</vote_number></vote>"
    }
    return "<vote_summary>" +
        "<congress>$congress</congress><session>$session</session>" +
        "<votes>$votes</votes></vote_summary>"
}

/** Block-style legislators YAML carrying just the id keys the scanner reads. */
private fun lisYaml(map: Map<String, String>): String =
    map.entries.joinToString("") { (lis, bioguide) ->
        "- id:\n    lis: $lis\n    bioguide: $bioguide\n"
    }

/** Menu + legislators YAMLs every successful run needs. */
private fun baseResponses(menu: String): MutableMap<String, String> = mutableMapOf(
    MENU_1_URL to menu,
    LEGISLATORS_CURRENT_YAML_URL to lisYaml(LIS_TO_BIOGUIDE),
    LEGISLATORS_HISTORICAL_YAML_URL to "",
)

/**
 * URL -> body; URLs in [errorStatus] answer with that status instead.
 * Unmapped URLs 404 (senate.gov's behavior for unpublished documents).
 * Records every requested URL in [requested] so tests can assert on
 * fetch counts.
 */
private fun mockVotesClient(
    responses: Map<String, String>,
    errorStatus: Map<String, HttpStatusCode> = emptyMap(),
    requested: MutableList<String> = mutableListOf(),
): SenateVotesClient {
    val http = HttpClient(MockEngine) {
        configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
        engine {
            addHandler { request ->
                val url = request.url.toString()
                requested += url
                val status = errorStatus[url]
                when {
                    status != null -> respond("error", status)
                    responses.containsKey(url) -> respond(responses.getValue(url), HttpStatusCode.OK)
                    else -> respond("not found", HttpStatusCode.NotFound)
                }
            }
        }
    }
    return SenateVotesClient(http)
}

private class VotesEnv {
    val fileSystem = FakeFileSystem()
    val store = FileVotesStore(fileSystem, "/out".toPath())
    val errors = ErrorCollector()

    fun readText(path: String): String =
        fileSystem.source(path.toPath()).buffer().use { it.readUtf8() }

    fun readIndex(): VotesIndex =
        ManifestJson.decodeFromString(VotesIndex.serializer(), readText("/out/congress119_votes.json"))
}

class FetchVotesTest {
    @Test fun writes_vote_file_and_index() = runTest {
        val env = VotesEnv()
        val responses = baseResponses(menuXml(119, 1, listOf(618)))
        responses[DETAIL_618_URL] = SENATE_VOTE_618_XML

        val result = fetchVotes(mockVotesClient(responses), env.store, 119, NOW_ISO, env.errors)

        assertEquals(1, result.fetched)
        assertEquals(0, result.skipped)
        assertEquals(0, env.errors.size)

        val savedText = env.readText("/out/votes/congress119/senate-1-618.json")
        val saved = ManifestJson.decodeFromString(RollCallVote.serializer(), savedText)
        assertEquals(parseSenateVote(SENATE_VOTE_618_XML, LIS_TO_BIOGUIDE), saved)

        val index = env.readIndex()
        assertEquals(119, index.congress)
        assertEquals(NOW_ISO, index.generatedAt)
        assertEquals(1, index.voteCount)
        val ref = index.votes.single()
        assertEquals("senate-119-1-618", ref.id)
        assertEquals("hr5371-119", ref.billId)
        assertEquals("votes/congress119/senate-1-618.json", ref.path)
        // The index carries the vote minus positions.
        assertFalse("\"positions\"" in env.readText("/out/congress119_votes.json"))
    }

    @Test fun second_run_skips_existing_votes() = runTest {
        val env = VotesEnv()
        val responses = baseResponses(menuXml(119, 1, listOf(618)))
        responses[DETAIL_618_URL] = SENATE_VOTE_618_XML

        val firstRequested = mutableListOf<String>()
        fetchVotes(
            mockVotesClient(responses, requested = firstRequested),
            env.store, 119, NOW_ISO, env.errors,
        )
        assertEquals(1, firstRequested.count { it == DETAIL_618_URL })

        val secondRequested = mutableListOf<String>()
        val result = fetchVotes(
            mockVotesClient(responses, requested = secondRequested),
            env.store, 119, NOW_ISO, env.errors,
        )
        // Menu only, no detail refetch.
        assertTrue(DETAIL_618_URL !in secondRequested)
        assertEquals(0, result.fetched)
        assertEquals(1, result.skipped)
        assertEquals(1, env.readIndex().voteCount)
    }

    @Test fun failed_vote_is_recorded_and_retried_next_run() = runTest {
        val env = VotesEnv()
        // Roll 619 is on the menu but its detail XML isn't published yet (404).
        val menu = menuXml(119, 1, listOf(618, 619))
        val responses = baseResponses(menu)
        responses[DETAIL_618_URL] = SENATE_VOTE_618_XML

        val result = fetchVotes(mockVotesClient(responses), env.store, 119, NOW_ISO, env.errors)
        assertEquals(1, result.fetched)
        assertEquals(1, env.errors.size)
        val record = env.errors.records().single()
        assertEquals("senate_vote", record.kind)
        assertEquals("SenateVotesApiException", record.errorClass)
        assertFalse(env.fileSystem.exists("/out/votes/congress119/senate-1-619.json".toPath()))

        // Index only carries what's actually on disk.
        assertEquals(listOf("senate-119-1-618"), env.readIndex().votes.map { it.id })

        // Next run retries the missing vote (618 skipped, 619 attempted
        // again) ...but 619's detail XML identifies itself as roll 618,
        // so it is rejected rather than published under the wrong id.
        responses[senateVoteSourceUrl(119, 1, 619)] = SENATE_VOTE_618_XML
        val retryRequested = mutableListOf<String>()
        val retryErrors = ErrorCollector()
        fetchVotes(
            mockVotesClient(responses, requested = retryRequested),
            env.store, 119, NOW_ISO, retryErrors,
        )
        assertTrue(senateVoteSourceUrl(119, 1, 619) in retryRequested)
        assertEquals(1, retryErrors.size)
        assertTrue("identifies itself as session 1 roll 618" in retryErrors.records().single().message)
        assertFalse(env.fileSystem.exists("/out/votes/congress119/senate-1-619.json".toPath()))
    }

    @Test fun missing_session_menu_is_tolerated() = runTest {
        val env = VotesEnv()
        // Session 2 menu 404s (unmapped): only session 1 is processed.
        val responses = baseResponses(menuXml(119, 1, listOf(618)))
        responses[DETAIL_618_URL] = SENATE_VOTE_618_XML

        val result = fetchVotes(mockVotesClient(responses), env.store, 119, NOW_ISO, env.errors)
        assertEquals(listOf(1), result.sessions)
        assertEquals(1, result.fetched)
    }

    @Test fun no_menus_at_all_is_fatal() = runTest {
        val env = VotesEnv()
        val e = assertFailsWith<FetchVotesException> {
            fetchVotes(mockVotesClient(emptyMap()), env.store, 119, NOW_ISO, env.errors)
        }
        assertTrue("no Senate vote menu available" in (e.message ?: ""))
    }

    @Test fun non_404_menu_error_is_fatal() = runTest {
        val env = VotesEnv()
        assertFailsWith<SenateVotesApiException> {
            fetchVotes(
                mockVotesClient(emptyMap(), errorStatus = mapOf(MENU_1_URL to HttpStatusCode.InternalServerError)),
                env.store, 119, NOW_ISO, env.errors,
            )
        }
    }

    @Test fun max_new_caps_fetches_and_defers_rest() = runTest {
        val env = VotesEnv()
        val responses = baseResponses(menuXml(119, 1, listOf(618, 619)))
        responses[DETAIL_618_URL] = SENATE_VOTE_618_XML

        var maxNewReached = false
        val result = fetchVotes(
            mockVotesClient(responses), env.store, 119, NOW_ISO, env.errors,
            maxNew = 1,
            progress = FetchVotesProgress(onMaxNewReached = { maxNewReached = true }),
        )
        assertTrue(maxNewReached)
        // Exactly one vote landed; the deferred one was never attempted,
        // so it is not an error.
        assertEquals(1, result.fetched)
        assertEquals(0, env.errors.size)
        assertEquals(1, env.readIndex().voteCount)
    }

    @Test fun menu_congress_mismatch_is_fatal() = runTest {
        val env = VotesEnv()
        val e = assertFailsWith<FetchVotesException> {
            fetchVotes(
                mockVotesClient(baseResponses(menuXml(118, 1, listOf(618)))),
                env.store, 119, NOW_ISO, env.errors,
            )
        }
        assertTrue("identifies itself as congress 118" in (e.message ?: ""))
    }

    @Test fun build_lis_to_bioguide_unions_current_over_historical() = runTest {
        val historical =
            "- id:\n    lis: S001\n    bioguide: OLD00001\n" +
                "- id:\n    lis: S002\n    bioguide: G000001\n"
        val current = "- id:\n    lis: S001\n    bioguide: NEW00001\n"
        val client = mockVotesClient(
            mapOf(
                LEGISLATORS_HISTORICAL_YAML_URL to historical,
                LEGISLATORS_CURRENT_YAML_URL to current,
            ),
        )
        assertEquals(
            mapOf("S001" to "NEW00001", "S002" to "G000001"),
            buildLisToBioguide(client),
        )
    }
}
