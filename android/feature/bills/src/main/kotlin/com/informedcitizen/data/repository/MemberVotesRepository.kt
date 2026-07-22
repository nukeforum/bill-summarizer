package com.informedcitizen.data.repository

import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.data.api.MembersApi
import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.data.cache.MemberVotesCache
import com.informedcitizen.pipeline.model.MemberVotes
import com.informedcitizen.pipeline.model.VotePosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** One saved rep's recorded position on one roll call. */
data class RepPosition(
    val voteId: String,
    val rep: SavedRep,
    val position: VotePosition,
)

/**
 * Saved reps' positions across all bills with recorded votes, keyed by
 * [com.informedcitizen.pipeline.model.Bill.id]. Positions within a bill
 * are grouped in saved-rep order; the UI matches them to a specific
 * roll call by [RepPosition.voteId]. [fetchFailed] is true when at
 * least one rep's shard could not be loaded from network or cache, so
 * the detail screen can show its quiet partial-data line.
 */
data class RepVotes(
    val positionsByBillId: Map<String, List<RepPosition>>,
    val fetchFailed: Boolean,
) {
    companion object {
        val Empty = RepVotes(emptyMap(), fetchFailed = false)
    }
}

/**
 * Fetches and caches the per-member positions shards
 * (`votes/members/{bioguideId}.json`) behind the "your reps voted"
 * surfaces (issue #21). Fetch is per saved rep, never per bill: at
 * most one request per rep on first collection, then served from the
 * in-process memo or the persistent cache until [STALE_AFTER_MILLIS]
 * elapses — so the bills list never triggers per-bill downloads.
 */
@Singleton
class MemberVotesRepository @Inject constructor(
    private val api: MembersApi,
    private val cache: MemberVotesCache,
    private val crashReporter: CrashReporter,
) {
    private val mutex = Mutex()
    private val shards = mutableMapOf<String, MemberVotes>()

    /** Cold flow emitting one [RepVotes] snapshot for [reps]. */
    fun positions(reps: List<SavedRep>): Flow<RepVotes> = flow { emit(load(reps)) }

    suspend fun load(reps: List<SavedRep>): RepVotes {
        if (reps.isEmpty()) return RepVotes.Empty
        var fetchFailed = false
        val byBill = LinkedHashMap<String, MutableList<RepPosition>>()
        for (rep in reps) {
            val shard = shardFor(rep.bioguideId)
            if (shard == null) {
                fetchFailed = true
                continue
            }
            for (row in shard.votes) {
                val billId = row.billId ?: continue
                byBill.getOrPut(billId) { mutableListOf() } +=
                    RepPosition(voteId = row.voteId, rep = rep, position = row.position)
            }
        }
        return RepVotes(positionsByBillId = byBill, fetchFailed = fetchFailed)
    }

    /**
     * Resolution order: in-process memo → persistent cache younger
     * than [STALE_AFTER_MILLIS] → network (written through) → stale
     * cache as offline fallback → null (counted as a fetch failure).
     * A 404 means the member has no recorded votes yet — an empty
     * shard, not a failure — matching the sponsored/cosponsored
     * per-member endpoints.
     */
    private suspend fun shardFor(bioguideId: String): MemberVotes? = mutex.withLock {
        shards[bioguideId]?.let { return@withLock it }
        val cached = runCatching { cache.loadFreshest(bioguideId) }.getOrNull()
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.fetchedAtMillis < STALE_AFTER_MILLIS) {
            return@withLock cached.value.also { shards[bioguideId] = it }
        }
        runCatching { api.getMemberVotes(bioguideId) }
            .onSuccess { writeThroughCache(it, now) }
            .recoverCatching { networkError ->
                if (networkError is HttpException && networkError.code() == 404) {
                    MemberVotes(generatedAt = "", bioguideId = bioguideId, voteCount = 0, votes = emptyList())
                } else {
                    crashReporter.recordNonFatal(networkError, "member votes fetch failed")
                    cached?.value ?: throw networkError
                }
            }
            .getOrNull()
            ?.also { shards[bioguideId] = it }
    }

    private suspend fun writeThroughCache(votes: MemberVotes, fetchedAtMillis: Long) {
        runCatching {
            cache.replaceForSource(
                source = BillSource.PUBLISHED,
                votes = votes,
                fetchedAtMillis = fetchedAtMillis,
            )
        }.onFailure { crashReporter.recordNonFatal(it, "member votes cache write failed") }
    }

    private companion object {
        // Shards are republished daily by the votes workflow; a few
        // hours of staleness only delays the newest roll calls, never
        // corrupts anything, so favour cache hits over re-downloads.
        const val STALE_AFTER_MILLIS = 6 * 60 * 60 * 1000L
    }
}
