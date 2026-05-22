package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.LegislatorsClient
import com.informedcitizen.pipeline.http.PipelineHttpConfig
import com.informedcitizen.pipeline.http.configurePipelineForTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val LEGISLATORS_TEST_URL = "https://test.example/legcur.json"

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

/**
 * MockEngine that serves Congress.gov + the legislators-current.json
 * side index. Path-based routing — the legislators URL is identifiable
 * by its host `test.example`.
 *
 * Two members: A001 (Senator, has full contact info), B001 (House rep,
 * only homepage). Phase 1 walks both, Phase 2 hits each one's
 * sponsored + cosponsored endpoints.
 */
private fun mockApiClient(
    onMemberDetail: ((bioguide: String) -> String?)? = null,
    legislatorsBody: String = DEFAULT_LEGISLATORS_BODY,
    legislatorsStatus: HttpStatusCode = HttpStatusCode.OK,
): HttpClient = HttpClient(MockEngine) {
    configurePipelineForTest(PipelineHttpConfig(retryBaseDelayMillis = 0))
    engine {
        addHandler { request ->
            val host = request.url.host
            val path = request.url.encodedPath
            if (host == "test.example" && path == "/legcur.json") {
                return@addHandler respond(legislatorsBody, legislatorsStatus, jsonHeaders())
            }
            val body = when {
                path == "/v3/member/congress/119" -> """{
                  "members":[
                    {"bioguideId":"A001","name":"Alice","state":"California","partyName":"Democratic"},
                    {"bioguideId":"B001","name":"Bob","state":"Texas","partyName":"Republican"}
                  ]
                }"""
                path == "/v3/member/A001" -> onMemberDetail?.invoke("A001") ?: """{"member":{
                  "directOrderName":"Alice Anderson","partyName":"Democratic","state":"California",
                  "terms":[{"chamber":"Senate"}],
                  "depiction":{"imageUrl":"https://x/alice.jpg"},
                  "officialUrl":"https://anderson.senate.gov",
                  "sponsoredLegislation":{"count":5},
                  "cosponsoredLegislation":{"count":12},
                  "addressInformation":{"officeAddress":"100 Russell SOB","phoneNumber":"(202) 224-0000"}
                }}"""
                path == "/v3/member/B001" -> onMemberDetail?.invoke("B001") ?: """{"member":{
                  "directOrderName":"Bob Brown","partyName":"Republican","state":"Texas","district":3,
                  "terms":[{"chamber":"House of Representatives"}],
                  "sponsoredLegislation":{"count":2},"cosponsoredLegislation":{"count":4}
                }}"""
                path == "/v3/member/A001/sponsored-legislation" -> """{"sponsoredLegislation":[
                  {"type":"S","number":"1","congress":119,"latestTitle":"Alice Bill",
                   "introducedDate":"2026-02-01",
                   "latestAction":{"actionDate":"2026-04-10","text":"Passed Senate."}}
                ]}"""
                path == "/v3/member/A001/cosponsored-legislation" -> """{"cosponsoredLegislation":[]}"""
                path == "/v3/member/B001/sponsored-legislation" -> """{"sponsoredLegislation":[
                  {"type":"HR","number":"42","congress":119,"latestTitle":"Bob Bill",
                   "introducedDate":"2026-03-01",
                   "latestAction":{"actionDate":"2026-04-15","text":"Referred."}}
                ]}"""
                path == "/v3/member/B001/cosponsored-legislation" -> """{"cosponsoredLegislation":[]}"""
                else -> "{}"
            }
            respond(body, HttpStatusCode.OK, jsonHeaders())
        }
    }
}

private const val DEFAULT_LEGISLATORS_BODY = """[
  {"id":{"bioguide":"A001"},
   "terms":[
     {"url":"https://anderson.senate.gov","contact_form":"https://anderson.senate.gov/email"}
   ]},
  {"id":{"bioguide":"B001"},
   "terms":[
     {"url":"https://brown.house.gov"}
   ]}
]"""

private fun fakeClock(at: Instant): Clock = object : Clock {
    override fun now(): Instant = at
}

private class TickingClock(start: Instant, private val stepMillis: Long) : Clock {
    private var current: Instant = start
    override fun now(): Instant {
        val out = current
        current = Instant.fromEpochMilliseconds(out.toEpochMilliseconds() + stepMillis)
        return out
    }
}

class FetchMembersTest {
    @Test fun phase1_and_phase2_write_index_and_legislation_with_contact_info() = runTest {
        val httpClient = mockApiClient()
        val cc = CongressClient(httpClient, apiKey = "k")
        val lc = LegislatorsClient(httpClient, sourceUrl = LEGISLATORS_TEST_URL)
        val fs = FakeFileSystem()
        val indexStore = FileMembersIndexStore(fs, "/out".toPath())
        val legStore = FileMemberLegislationStore(fs, "/out".toPath())
        val errors = ErrorCollector()

        val result = fetchMembers(
            congressClient = cc,
            legislatorsClient = lc,
            congress = 119,
            nowIso = "2026-05-15T00:00:00Z",
            indexStore = indexStore,
            legislationStore = legStore,
            errors = errors,
            clockOverride = fakeClock(Instant.parse("2026-05-15T00:00:00Z")),
        )

        assertTrue(result.ranPhase1)
        assertTrue(result.ranPhase2)
        assertEquals(2, result.phase1MemberCount)
        assertEquals(2, result.phase2Backfilled)
        assertEquals(0, result.phase2SkippedCached)
        assertFalse(result.phase2TimedOut)
        assertEquals(0, errors.size)
        assertTrue(result.contactInfoLoaded)
        assertEquals(2, result.contactInfoSize)

        // Index written.
        val index = indexStore.load(119)!!
        assertEquals(2, index.members.size)
        val alice = index.members.single { it.bioguideId == "A001" }
        assertEquals("Alice Anderson", alice.name)
        assertEquals("D", alice.party)
        assertEquals("CA", alice.state)
        assertEquals("senate", alice.chamber)
        assertNull(alice.district) // senators have null district
        assertEquals("https://anderson.senate.gov/email", alice.contactForm)
        assertEquals("https://anderson.senate.gov", alice.website)
        val bob = index.members.single { it.bioguideId == "B001" }
        assertEquals("house", bob.chamber)
        assertEquals(3, bob.district)
        assertNull(bob.contactForm)
        assertEquals("https://brown.house.gov", bob.website)

        // Phase 2 files written.
        assertTrue(legStore.exists("A001", "sponsored"))
        assertTrue(legStore.exists("A001", "cosponsored"))
        assertTrue(legStore.exists("B001", "sponsored"))
        assertTrue(legStore.exists("B001", "cosponsored"))

        // Phase 2 file contents — sponsored has one item, cosponsored is empty.
        val aliceSponsoredText = fs.source(legStore.pathFor("A001", "sponsored"))
            .buffer()
            .use { it.readUtf8() }
        assertTrue("\"id\": \"s1-119\"" in aliceSponsoredText)
        assertTrue("\"title\": \"Alice Bill\"" in aliceSponsoredText)
    }

    @Test fun phase1_only_skips_legislation_files() = runTest {
        val httpClient = mockApiClient()
        val cc = CongressClient(httpClient, apiKey = "k")
        val lc = LegislatorsClient(httpClient, sourceUrl = LEGISLATORS_TEST_URL)
        val fs = FakeFileSystem()
        val indexStore = FileMembersIndexStore(fs, "/out".toPath())
        val legStore = FileMemberLegislationStore(fs, "/out".toPath())

        val result = fetchMembers(
            congressClient = cc,
            legislatorsClient = lc,
            congress = 119,
            nowIso = "2026-05-15T00:00:00Z",
            indexStore = indexStore,
            legislationStore = legStore,
            errors = ErrorCollector(),
            runPhase2 = false,
            clockOverride = fakeClock(Instant.parse("2026-05-15T00:00:00Z")),
        )

        assertTrue(result.ranPhase1)
        assertFalse(result.ranPhase2)
        assertEquals(2, result.phase1MemberCount)
        assertEquals(0, result.phase2Backfilled)
        // No phase-2 files should exist.
        assertFalse(legStore.exists("A001", "sponsored"))
        assertFalse(legStore.exists("B001", "cosponsored"))
    }

    @Test fun phase2_skips_already_cached_members() = runTest {
        val httpClient = mockApiClient()
        val cc = CongressClient(httpClient, apiKey = "k")
        val lc = LegislatorsClient(httpClient, sourceUrl = LEGISLATORS_TEST_URL)
        val fs = FakeFileSystem()
        val indexStore = FileMembersIndexStore(fs, "/out".toPath())
        val legStore = FileMemberLegislationStore(fs, "/out".toPath())

        // Pre-seed both A001 files.
        legStore.save(
            bioguideId = "A001", kind = "sponsored",
            congress = 119, bills = emptyList(),
            nowIso = "2026-05-01T00:00:00Z",
        )
        legStore.save(
            bioguideId = "A001", kind = "cosponsored",
            congress = 119, bills = emptyList(),
            nowIso = "2026-05-01T00:00:00Z",
        )

        val result = fetchMembers(
            congressClient = cc,
            legislatorsClient = lc,
            congress = 119,
            nowIso = "2026-05-15T00:00:00Z",
            indexStore = indexStore,
            legislationStore = legStore,
            errors = ErrorCollector(),
            clockOverride = fakeClock(Instant.parse("2026-05-15T00:00:00Z")),
        )

        assertEquals(1, result.phase2Backfilled)   // only B001
        assertEquals(1, result.phase2SkippedCached) // A001 skipped
    }

    @Test fun phase2_stops_at_time_budget_exceeded() = runTest {
        val httpClient = mockApiClient()
        val cc = CongressClient(httpClient, apiKey = "k")
        val lc = LegislatorsClient(httpClient, sourceUrl = LEGISLATORS_TEST_URL)
        val fs = FakeFileSystem()
        val indexStore = FileMembersIndexStore(fs, "/out".toPath())
        val legStore = FileMemberLegislationStore(fs, "/out".toPath())

        // Clock advances 10 minutes per call; budget is 1 minute → exceeded
        // on the second `now()` (the Phase 2 loop's first iteration check).
        val clock = TickingClock(
            start = Instant.parse("2026-05-15T00:00:00Z"),
            stepMillis = 10L * 60L * 1000L,
        )

        val result = fetchMembers(
            congressClient = cc,
            legislatorsClient = lc,
            congress = 119,
            nowIso = "2026-05-15T00:00:00Z",
            indexStore = indexStore,
            legislationStore = legStore,
            errors = ErrorCollector(),
            timeBudgetMillis = 60L * 1000L,
            clockOverride = clock,
        )

        assertTrue(result.ranPhase1)
        assertTrue(result.phase2TimedOut)
        assertEquals(0, result.phase2Backfilled)
    }

    @Test fun contact_info_failure_is_non_fatal_and_recorded() = runTest {
        val httpClient = mockApiClient(legislatorsStatus = HttpStatusCode.InternalServerError)
        val cc = CongressClient(httpClient, apiKey = "k")
        val lc = LegislatorsClient(httpClient, sourceUrl = LEGISLATORS_TEST_URL)
        val fs = FakeFileSystem()
        val indexStore = FileMembersIndexStore(fs, "/out".toPath())
        val legStore = FileMemberLegislationStore(fs, "/out".toPath())
        val errors = ErrorCollector()

        val result = fetchMembers(
            congressClient = cc,
            legislatorsClient = lc,
            congress = 119,
            nowIso = "2026-05-15T00:00:00Z",
            indexStore = indexStore,
            legislationStore = legStore,
            errors = errors,
            runPhase2 = false,
            clockOverride = fakeClock(Instant.parse("2026-05-15T00:00:00Z")),
        )

        assertTrue(result.ranPhase1)
        assertFalse(result.contactInfoLoaded)
        assertEquals(2, result.phase1MemberCount)
        // Members still written, just without contact info.
        val index = indexStore.load(119)!!
        for (m in index.members) {
            assertNull(m.contactForm)
            assertNull(m.website)
        }
        // One error recorded for the contact-info miss.
        assertTrue(errors.records().any { it.kind == "contact_info_index" })
    }

    @Test fun phase2_only_with_no_index_records_error() = runTest {
        val httpClient = mockApiClient()
        val cc = CongressClient(httpClient, apiKey = "k")
        val lc = LegislatorsClient(httpClient, sourceUrl = LEGISLATORS_TEST_URL)
        val fs = FakeFileSystem()
        val indexStore = FileMembersIndexStore(fs, "/out".toPath())
        val legStore = FileMemberLegislationStore(fs, "/out".toPath())
        val errors = ErrorCollector()

        val result = fetchMembers(
            congressClient = cc,
            legislatorsClient = lc,
            congress = 119,
            nowIso = "2026-05-15T00:00:00Z",
            indexStore = indexStore,
            legislationStore = legStore,
            errors = errors,
            runPhase1 = false,
            clockOverride = fakeClock(Instant.parse("2026-05-15T00:00:00Z")),
        )

        assertFalse(result.ranPhase2)
        assertTrue(errors.records().any { it.kind == "phase2_without_index" })
    }

    @Test fun member_detail_failure_falls_back_to_cached_record() = runTest {
        // First call to A001 detail succeeds and is persisted; a second
        // invocation against a flaky detail endpoint should reuse the
        // cached record (party/state/etc) and record the failure.
        val fs = FakeFileSystem()
        val indexStore = FileMembersIndexStore(fs, "/out".toPath())
        val legStore = FileMemberLegislationStore(fs, "/out".toPath())

        // Pass 1: normal mock, seed the index.
        run {
            val httpClient = mockApiClient()
            val cc = CongressClient(httpClient, apiKey = "k")
            val lc = LegislatorsClient(httpClient, sourceUrl = LEGISLATORS_TEST_URL)
            fetchMembers(
                congressClient = cc,
                legislatorsClient = lc,
                congress = 119,
                nowIso = "2026-05-15T00:00:00Z",
                indexStore = indexStore,
                legislationStore = legStore,
                errors = ErrorCollector(),
                runPhase2 = false,
                clockOverride = fakeClock(Instant.parse("2026-05-15T00:00:00Z")),
            )
        }

        // Pass 2: A001 detail throws; expect reused-cache path.
        val errors = ErrorCollector()
        val httpClient = mockApiClient(onMemberDetail = { bid ->
            if (bid == "A001") error("simulated 503") else null
        })
        val cc = CongressClient(httpClient, apiKey = "k")
        val lc = LegislatorsClient(httpClient, sourceUrl = LEGISLATORS_TEST_URL)
        val result = fetchMembers(
            congressClient = cc,
            legislatorsClient = lc,
            congress = 119,
            nowIso = "2026-05-16T00:00:00Z",
            indexStore = indexStore,
            legislationStore = legStore,
            errors = errors,
            runPhase2 = false,
            clockOverride = fakeClock(Instant.parse("2026-05-16T00:00:00Z")),
        )

        assertEquals(2, result.phase1MemberCount)
        assertTrue(errors.records().any { it.kind == "member_detail_reused_cache" })
        // A001 still in the (rewritten) index thanks to fallback.
        val alice = indexStore.load(119)!!.members.single { it.bioguideId == "A001" }
        assertEquals("Alice Anderson", alice.name)
    }
}
