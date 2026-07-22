package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.http.PipelineHttpConfig
import com.informedcitizen.pipeline.http.SenateVotesApiException
import com.informedcitizen.pipeline.http.SenateVotesClient
import com.informedcitizen.pipeline.http.configurePipelineForTest
import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.Chamber
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.RollCallVote
import com.informedcitizen.pipeline.model.Sponsor
import com.informedcitizen.pipeline.model.VoteTotals
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
 * Integration tests for [fetchVotes] with mocked senate.gov and
 * clerk.house.gov fetches. Mirrors Python `test_fetch_votes_main.py`:
 * the detail XMLs are the real fixtures for Senate roll call 119-1-618
 * and House 2025 roll 17; the vote menu is built inline so tests
 * control exactly which roll calls the driver sees.
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

    // ---------- House probe loop ---------------------------------------
    //
    // The Senate side of these tests is a menu with zero votes, so all
    // activity comes from the House probe. Unmapped House URLs 404,
    // ending each session's probe (clerk.house.gov's behavior past the
    // newest published roll).

    private val emptyMenu = menuXml(119, 1, emptyList())

    /** Save a minimal-but-valid vote file so the probe skips this roll. */
    private fun seedHouseVote(store: FileVotesStore, congress: Int, session: Int, roll: Int) {
        store.saveVote(
            RollCallVote(
                id = "house-$congress-$session-$roll",
                congress = congress,
                chamber = Chamber.HOUSE,
                session = session,
                rollNumber = roll,
                date = "2025-01-03",
                question = "On Passage",
                result = "Passed",
                billId = null,
                totals = VoteTotals(yea = 0, nay = 0, present = 0, notVoting = 0),
                positions = emptyList(),
            ),
        )
    }

    @Test fun house_fetches_past_existing_rolls_and_stops_at_404() = runTest {
        val env = VotesEnv()
        for (roll in 1..16) seedHouseVote(env.store, 119, 1, roll)
        val responses = baseResponses(emptyMenu)
        responses[houseVoteSourceUrl(119, 1, 17)] = HOUSE_VOTE_ROLL17_XML

        val requested = mutableListOf<String>()
        val result = fetchVotes(
            mockVotesClient(responses, requested = requested),
            env.store, 119, NOW_ISO, env.errors,
        )

        assertEquals(1, result.fetched)
        assertEquals(0, result.senateFetched)
        assertEquals(1, result.houseFetched)
        assertEquals(16, result.skipped)
        assertEquals(0, result.unsupported)
        assertEquals(0, env.errors.size)

        val savedText = env.readText("/out/votes/congress119/house-1-17.json")
        val saved = ManifestJson.decodeFromString(RollCallVote.serializer(), savedText)
        assertEquals(parseHouseVote(HOUSE_VOTE_ROLL17_XML), saved)

        // Existing rolls cost no fetches; the probe stopped at the
        // first 404 (roll 18) and never looked further.
        assertTrue(houseVoteSourceUrl(119, 1, 16) !in requested)
        assertTrue(houseVoteSourceUrl(119, 1, 18) in requested)
        assertTrue(houseVoteSourceUrl(119, 1, 19) !in requested)
        // Session 2 was probed and found unpublished (single 404).
        assertTrue(houseVoteSourceUrl(119, 2, 1) in requested)
        assertTrue(houseVoteSourceUrl(119, 2, 2) !in requested)

        val index = env.readIndex()
        assertEquals(17, index.voteCount)
        val ref = index.votes.first { it.id == "house-119-1-17" }
        assertEquals("hr30-119", ref.billId)
        assertEquals("votes/congress119/house-1-17.json", ref.path)
    }

    @Test fun house_speaker_election_is_skipped_not_errored() = runTest {
        val env = VotesEnv()
        val responses = baseResponses(emptyMenu)
        responses[houseVoteSourceUrl(119, 1, 1)] = HOUSE_VOTE_QUORUM_XML
        responses[houseVoteSourceUrl(119, 1, 2)] = HOUSE_VOTE_SPEAKER_XML

        val requested = mutableListOf<String>()
        val unsupportedKeys = mutableListOf<String>()
        val result = fetchVotes(
            mockVotesClient(responses, requested = requested),
            env.store, 119, NOW_ISO, env.errors,
            progress = FetchVotesProgress(onUnsupportedVote = { key, _ -> unsupportedKeys += key }),
        )

        assertEquals(1, result.houseFetched)
        assertEquals(1, result.unsupported)
        assertEquals(0, env.errors.size)
        assertEquals(listOf("119-1-2"), unsupportedKeys)
        assertTrue(env.fileSystem.exists("/out/votes/congress119/house-1-1.json".toPath()))
        assertFalse(env.fileSystem.exists("/out/votes/congress119/house-1-2.json".toPath()))
        // The probe continued past the unsupported vote to roll 3 (404 -> stop).
        assertTrue(houseVoteSourceUrl(119, 1, 3) in requested)

        // Next run: roll 1 is on disk (skipped), the Speaker election is
        // re-probed and re-skipped — cheap, and keeps disk as the only cursor.
        val secondRequested = mutableListOf<String>()
        val second = fetchVotes(
            mockVotesClient(responses, requested = secondRequested),
            env.store, 119, NOW_ISO, ErrorCollector(),
        )
        assertTrue(houseVoteSourceUrl(119, 1, 1) !in secondRequested)
        assertTrue(houseVoteSourceUrl(119, 1, 2) in secondRequested)
        assertEquals(1, second.unsupported)
    }

    @Test fun house_consecutive_parse_failures_stop_probe() = runTest {
        val env = VotesEnv()
        val responses = baseResponses(emptyMenu)
        responses[houseVoteSourceUrl(119, 1, 1)] = HOUSE_VOTE_QUORUM_XML
        responses[houseVoteSourceUrl(119, 1, 2)] = "<not-a-vote/>"
        responses[houseVoteSourceUrl(119, 1, 3)] = "<not-a-vote/>"
        responses[houseVoteSourceUrl(119, 1, 4)] = "<not-a-vote/>"
        responses[houseVoteSourceUrl(119, 1, 5)] = HOUSE_VOTE_QUORUM_XML

        val requested = mutableListOf<String>()
        var failureStop: Pair<Int, Int>? = null
        val result = fetchVotes(
            mockVotesClient(responses, requested = requested),
            env.store, 119, NOW_ISO, env.errors,
            progress = FetchVotesProgress(
                onConsecutiveFailures = { count, session -> failureStop = count to session },
            ),
        )

        // Three consecutive failures capped the session probe before roll 5.
        assertTrue(houseVoteSourceUrl(119, 1, 5) !in requested)
        assertEquals(3 to 1, failureStop)
        assertEquals(1, result.houseFetched)
        assertEquals(3, env.errors.size)
        assertTrue(env.errors.records().all { it.kind == "house_vote" })
    }

    @Test fun house_identity_mismatch_stops_probe() = runTest {
        val env = VotesEnv()
        // Roll 1's URL serves XML identifying itself as roll 17: the
        // year/session mapping must be wrong, so nothing is published
        // and the probe stops.
        val responses = baseResponses(emptyMenu)
        responses[houseVoteSourceUrl(119, 1, 1)] = HOUSE_VOTE_ROLL17_XML

        val requested = mutableListOf<String>()
        val result = fetchVotes(
            mockVotesClient(responses, requested = requested),
            env.store, 119, NOW_ISO, env.errors,
        )

        assertEquals(0, result.houseFetched)
        assertFalse(env.fileSystem.exists("/out/votes/congress119/house-1-1.json".toPath()))
        assertFalse(env.fileSystem.exists("/out/votes/congress119/house-1-17.json".toPath()))
        assertTrue(houseVoteSourceUrl(119, 1, 2) !in requested)
        val record = env.errors.records().single()
        assertEquals("house_vote", record.kind)
        assertTrue("identifies itself as house-119-1-17" in record.message)
    }

    @Test fun house_non_404_error_recorded_and_stops_probe() = runTest {
        val env = VotesEnv()
        val requested = mutableListOf<String>()
        fetchVotes(
            mockVotesClient(
                baseResponses(emptyMenu),
                errorStatus = mapOf(houseVoteSourceUrl(119, 1, 1) to HttpStatusCode.InternalServerError),
                requested = requested,
            ),
            env.store, 119, NOW_ISO, env.errors,
        )
        assertTrue(houseVoteSourceUrl(119, 1, 2) !in requested)
        val record = env.errors.records().single()
        assertEquals("house_vote", record.kind)
        assertEquals("SenateVotesApiException", record.errorClass)
    }

    @Test fun house_deferred_when_senate_exhausts_budget() = runTest {
        val env = VotesEnv()
        val responses = baseResponses(menuXml(119, 1, listOf(618)))
        responses[DETAIL_618_URL] = SENATE_VOTE_618_XML
        responses[houseVoteSourceUrl(119, 1, 1)] = HOUSE_VOTE_QUORUM_XML

        val requested = mutableListOf<String>()
        var houseDeferred = false
        val result = fetchVotes(
            mockVotesClient(responses, requested = requested),
            env.store, 119, NOW_ISO, env.errors,
            maxNew = 1,
            progress = FetchVotesProgress(onHouseDeferred = { houseDeferred = true }),
        )
        assertTrue(houseDeferred)
        assertEquals(1, result.senateFetched)
        assertEquals(0, result.houseFetched)
        assertTrue(houseVoteSourceUrl(119, 1, 1) !in requested)
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

    @Test fun attaches_vote_refs_to_bills_manifest() = runTest {
        // Mirrors Python `test_main_attaches_vote_refs_to_bills_manifest`.
        val env = VotesEnv()
        val manifestStore = FileBillsManifestStore(env.fileSystem, "/out".toPath())
        manifestStore.save(
            congress = 119,
            bills = listOf(seedBill("hr5371-119"), seedBill("s42-119")),
            nowIso = "2026-01-01T00:00:00Z",
        )
        val responses = baseResponses(menuXml(119, 1, listOf(618)))
        responses[DETAIL_618_URL] = SENATE_VOTE_618_XML

        val result = fetchVotes(
            mockVotesClient(responses), env.store, 119, NOW_ISO, env.errors,
            manifestStore = manifestStore,
        )
        assertTrue(result.billManifestRefreshed)

        val manifest = manifestStore.load(119)!!
        val bills = manifest.bills.associateBy { it.id }
        // The linked bill carries the index refs verbatim; the other gets [].
        assertEquals(env.readIndex().votes, bills.getValue("hr5371-119").votes)
        assertTrue(bills.getValue("s42-119").votes.isEmpty())
        assertEquals(NOW_ISO, manifest.generatedAt)
        assertTrue(manifest.votesCoverage)
    }

    @Test fun manifest_rewritten_when_only_votes_coverage_flips() = runTest {
        // Mirrors Python `test_manifest_rewritten_when_only_votes_coverage_flips`:
        // a manifest whose bills gain no vote refs (the only fetched vote links
        // a different bill) must still be rewritten on the first index publish,
        // so votes_coverage flips and the app can stop hiding vote surfaces.
        val env = VotesEnv()
        val manifestStore = FileBillsManifestStore(env.fileSystem, "/out".toPath())
        manifestStore.save(
            congress = 119,
            bills = listOf(seedBill("s42-119")),
            nowIso = "2026-01-01T00:00:00Z",
        )
        assertFalse(manifestStore.load(119)!!.votesCoverage)
        val responses = baseResponses(menuXml(119, 1, listOf(618)))
        responses[DETAIL_618_URL] = SENATE_VOTE_618_XML

        val result = fetchVotes(
            mockVotesClient(responses), env.store, 119, NOW_ISO, env.errors,
            manifestStore = manifestStore,
        )
        assertTrue(result.billManifestRefreshed)

        val manifest = manifestStore.load(119)!!
        assertTrue(manifest.votesCoverage)
        assertTrue(manifest.bills.single().votes.isEmpty())
    }

    @Test fun bills_manifest_untouched_when_vote_refs_unchanged() = runTest {
        // Mirrors Python `test_bills_manifest_untouched_when_vote_refs_unchanged`:
        // no new votes on the second run — the manifest must not churn (its
        // generated_at would otherwise bump on every nightly votes run).
        val env = VotesEnv()
        val manifestStore = FileBillsManifestStore(env.fileSystem, "/out".toPath())
        manifestStore.save(
            congress = 119,
            bills = listOf(seedBill("hr5371-119")),
            nowIso = "2026-01-01T00:00:00Z",
        )
        val responses = baseResponses(menuXml(119, 1, listOf(618)))
        responses[DETAIL_618_URL] = SENATE_VOTE_618_XML

        val first = fetchVotes(
            mockVotesClient(responses), env.store, 119, NOW_ISO, env.errors,
            manifestStore = manifestStore,
        )
        assertTrue(first.billManifestRefreshed)
        val enriched = env.readText("/out/congress119_bills.json")

        val second = fetchVotes(
            mockVotesClient(responses), env.store, 119, "2026-07-23T00:00:00Z", env.errors,
            manifestStore = manifestStore,
        )
        assertFalse(second.billManifestRefreshed)
        assertEquals(enriched, env.readText("/out/congress119_bills.json"))
    }
}

/** Minimal bill record for the manifest-refresh tests. */
private fun seedBill(id: String): Bill = Bill(
    id = id,
    congress = 119,
    type = "hr",
    number = "1",
    title = "A bill",
    sponsor = Sponsor("X", "D", "CA"),
    introducedDate = "2025-01-01",
    latestAction = Action("2025-11-10", "Passed"),
    outcome = Outcome.PASSED_HOUSE,
    congressGovUrl = "https://example.test",
)
