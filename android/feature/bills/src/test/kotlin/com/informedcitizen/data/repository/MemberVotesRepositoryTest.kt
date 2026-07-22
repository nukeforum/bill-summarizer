package com.informedcitizen.data.repository

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.api.MembersApi
import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.pipeline.model.MemberLegislation
import com.informedcitizen.pipeline.model.MemberVoteRow
import com.informedcitizen.pipeline.model.MemberVotes
import com.informedcitizen.pipeline.model.MembersIndex
import com.informedcitizen.pipeline.model.VotePosition
import com.informedcitizen.testutil.FakeMemberVotesCache
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

private val moreno = SavedRep("M002222", "Bernie Moreno", "R", "OH", "senate")
private val husted = SavedRep("H002222", "Jon Husted", "R", "OH", "senate")
private val turner = SavedRep("T000463", "Michael Turner", "R", "OH", "house")

private fun row(
    voteId: String,
    billId: String?,
    position: VotePosition = VotePosition.YEA,
) = MemberVoteRow(
    voteId = voteId,
    congress = 119,
    date = "2026-07-10",
    question = "On Passage",
    result = "Passed",
    position = position,
    billId = billId,
    type = billId?.let { "hr" },
    number = billId?.let { "1" },
    shortTitle = null,
)

private fun shard(rep: SavedRep, vararg rows: MemberVoteRow) = MemberVotes(
    generatedAt = "2026-07-21T06:30:00Z",
    bioguideId = rep.bioguideId,
    voteCount = rows.size,
    votes = rows.toList(),
)

private fun http404(): HttpException = HttpException(
    Response.error<Any>(404, "".toResponseBody("application/json".toMediaType())),
)

private class FakeVotesApi(
    private val shards: Map<String, MemberVotes> = emptyMap(),
    private val failures: Map<String, Throwable> = emptyMap(),
) : MembersApi {
    var voteFetches = 0
    override suspend fun getMembersIndex(congress: String): MembersIndex =
        error("not used by MemberVotesRepository")
    override suspend fun getSponsored(bioguideId: String): MemberLegislation =
        error("not used by MemberVotesRepository")
    override suspend fun getCosponsored(bioguideId: String): MemberLegislation =
        error("not used by MemberVotesRepository")
    override suspend fun getMemberVotes(bioguideId: String): MemberVotes {
        voteFetches++
        failures[bioguideId]?.let { throw it }
        return shards[bioguideId] ?: throw http404()
    }
}

class MemberVotesRepositoryTest {

    @Test
    fun `fetches one shard per rep and keys positions by bill id`() = runTest {
        val api = FakeVotesApi(
            shards = mapOf(
                moreno.bioguideId to shard(
                    moreno,
                    row("senate-119-2-618", "hr5371-119"),
                    row("senate-119-2-600", "s5-119", VotePosition.NAY),
                ),
                husted.bioguideId to shard(
                    husted,
                    row("senate-119-2-618", "hr5371-119", VotePosition.NOT_VOTING),
                ),
            ),
        )
        val repo = MemberVotesRepository(api, FakeMemberVotesCache(), FakeCrashReporter())

        val out = repo.positions(listOf(moreno, husted)).first()

        assertEquals(2, api.voteFetches)
        assertFalse(out.fetchFailed)
        assertEquals(setOf("hr5371-119", "s5-119"), out.positionsByBillId.keys)
        val onCr = out.positionsByBillId.getValue("hr5371-119")
        assertEquals(listOf(moreno, husted), onCr.map { it.rep })
        assertEquals(listOf(VotePosition.YEA, VotePosition.NOT_VOTING), onCr.map { it.position })
        assertEquals("senate-119-2-618", onCr.first().voteId)
        assertEquals(
            listOf(VotePosition.NAY),
            out.positionsByBillId.getValue("s5-119").map { it.position },
        )
    }

    @Test
    fun `no saved reps means no fetches`() = runTest {
        val api = FakeVotesApi()
        val repo = MemberVotesRepository(api, FakeMemberVotesCache(), FakeCrashReporter())

        val out = repo.positions(emptyList()).first()

        assertEquals(RepVotes.Empty, out)
        assertEquals(0, api.voteFetches)
    }

    @Test
    fun `second load reuses in-process shards without new fetches`() = runTest {
        val api = FakeVotesApi(
            shards = mapOf(turner.bioguideId to shard(turner, row("house-119-2-17", "hr30-119"))),
        )
        val repo = MemberVotesRepository(api, FakeMemberVotesCache(), FakeCrashReporter())

        repo.positions(listOf(turner)).first()
        val out = repo.positions(listOf(turner)).first()

        assertEquals(1, api.voteFetches)
        assertEquals(setOf("hr30-119"), out.positionsByBillId.keys)
    }

    @Test
    fun `fresh persistent cache is served without network`() = runTest {
        val cache = FakeMemberVotesCache()
        cache.replaceForSource(
            source = BillSource.PUBLISHED,
            votes = shard(turner, row("house-119-2-17", "hr30-119")),
            fetchedAtMillis = System.currentTimeMillis(),
        )
        val api = FakeVotesApi()
        val repo = MemberVotesRepository(api, cache, FakeCrashReporter())

        val out = repo.positions(listOf(turner)).first()

        assertEquals(0, api.voteFetches)
        assertFalse(out.fetchFailed)
        assertEquals(setOf("hr30-119"), out.positionsByBillId.keys)
    }

    @Test
    fun `stale persistent cache triggers a refetch`() = runTest {
        val cache = FakeMemberVotesCache()
        cache.replaceForSource(
            source = BillSource.PUBLISHED,
            votes = shard(turner, row("house-119-2-17", "hr30-119")),
            fetchedAtMillis = System.currentTimeMillis() - 7 * 60 * 60 * 1000L,
        )
        val api = FakeVotesApi(
            shards = mapOf(
                turner.bioguideId to shard(
                    turner,
                    row("house-119-2-17", "hr30-119"),
                    row("house-119-2-18", "hr31-119"),
                ),
            ),
        )
        val repo = MemberVotesRepository(api, cache, FakeCrashReporter())

        val out = repo.positions(listOf(turner)).first()

        assertEquals(1, api.voteFetches)
        assertEquals(setOf("hr30-119", "hr31-119"), out.positionsByBillId.keys)
        val refreshed = cache.loadFreshest(turner.bioguideId)
        assertEquals(2, refreshed?.value?.votes?.size)
    }

    @Test
    fun `network failure falls back to stale cache and records a non-fatal`() = runTest {
        val cache = FakeMemberVotesCache()
        cache.replaceForSource(
            source = BillSource.PUBLISHED,
            votes = shard(turner, row("house-119-2-17", "hr30-119")),
            fetchedAtMillis = System.currentTimeMillis() - 7 * 60 * 60 * 1000L,
        )
        val reporter = FakeCrashReporter()
        val api = FakeVotesApi(failures = mapOf(turner.bioguideId to IOException("offline")))
        val repo = MemberVotesRepository(api, cache, reporter)

        val out = repo.positions(listOf(turner)).first()

        assertFalse("stale data beats an error state", out.fetchFailed)
        assertEquals(setOf("hr30-119"), out.positionsByBillId.keys)
        assertEquals(1, reporter.recorded.size)
        assertEquals("member votes fetch failed", reporter.recorded.single().message)
    }

    @Test
    fun `network failure with no cache flags the failure but keeps other reps`() = runTest {
        val api = FakeVotesApi(
            shards = mapOf(moreno.bioguideId to shard(moreno, row("senate-119-2-618", "hr5371-119"))),
            failures = mapOf(husted.bioguideId to IOException("offline")),
        )
        val repo = MemberVotesRepository(api, FakeMemberVotesCache(), FakeCrashReporter())

        val out = repo.positions(listOf(moreno, husted)).first()

        assertTrue(out.fetchFailed)
        assertEquals(setOf("hr5371-119"), out.positionsByBillId.keys)
        assertEquals(listOf(moreno), out.positionsByBillId.getValue("hr5371-119").map { it.rep })
    }

    @Test
    fun `404 shard means no recorded votes, not a failure`() = runTest {
        val api = FakeVotesApi()
        val reporter = FakeCrashReporter()
        val repo = MemberVotesRepository(api, FakeMemberVotesCache(), reporter)

        val out = repo.positions(listOf(turner)).first()

        assertFalse(out.fetchFailed)
        assertTrue(out.positionsByBillId.isEmpty())
        assertTrue("404 is not a reportable failure", reporter.recorded.isEmpty())
    }

    @Test
    fun `rows without a bill id are excluded from the bill map`() = runTest {
        val api = FakeVotesApi(
            shards = mapOf(
                moreno.bioguideId to shard(
                    moreno,
                    row("senate-119-2-618", "hr5371-119"),
                    row("senate-119-2-619", billId = null),
                ),
            ),
        )
        val repo = MemberVotesRepository(api, FakeMemberVotesCache(), FakeCrashReporter())

        val out = repo.positions(listOf(moreno)).first()

        assertEquals(setOf("hr5371-119"), out.positionsByBillId.keys)
    }
}
