package com.informedcitizen.data.byok

import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.pipeline.model.MemberVoteRow
import com.informedcitizen.pipeline.model.MemberVotes
import com.informedcitizen.pipeline.model.VotePosition
import com.informedcitizen.testutil.FakeMemberVotesCache
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublishByokMemberVotesTest {

    private fun shard(bioguideId: String) = MemberVotes(
        generatedAt = "2026-07-22T00:00:00Z",
        bioguideId = bioguideId,
        voteCount = 1,
        votes = listOf(
            MemberVoteRow(
                voteId = "house-119-2-1", congress = 119, date = "2026-07-22",
                question = "On Passage", result = "Passed", position = VotePosition.YEA,
                billId = "hr1-119", type = "hr", number = "1", shortTitle = "Sample Act",
            ),
        ),
    )

    @Test
    fun `publishes saved reps' shards under the BYOK source`() = runTest {
        val cache = FakeMemberVotesCache()
        val onDisk = mapOf("A000001" to shard("A000001"), "B000002" to shard("B000002"))

        val published = publishByokMemberVotes(
            savedIds = setOf("A000001", "B000002"),
            cache = cache,
            fetchedAtMillis = 1_234L,
            loadShard = { onDisk[it] },
        )

        assertEquals(2, published)
        val a = cache.load("A000001", BillSource.BYOK)
        assertEquals(onDisk["A000001"], a?.value)
        assertEquals(1_234L, a?.fetchedAtMillis)
        assertEquals(onDisk["B000002"], cache.load("B000002", BillSource.BYOK)?.value)
    }

    @Test
    fun `skips a saved rep with no shard on disk`() = runTest {
        val cache = FakeMemberVotesCache()
        val onDisk = mapOf("A000001" to shard("A000001"))

        val published = publishByokMemberVotes(
            savedIds = setOf("A000001", "C000003"),
            cache = cache,
            fetchedAtMillis = 1L,
            loadShard = { onDisk[it] },
        )

        assertEquals(1, published)
        // The rep with no recorded votes in the window is left to the
        // read path's own network fallback, not cached as empty BYOK.
        assertNull(cache.load("C000003", BillSource.BYOK))
    }

    @Test
    fun `publishes nothing when no reps are saved`() = runTest {
        val cache = FakeMemberVotesCache()

        val published = publishByokMemberVotes(
            savedIds = emptySet(),
            cache = cache,
            fetchedAtMillis = 1L,
            loadShard = { error("must not load any shard") },
        )

        assertEquals(0, published)
    }
}
