package com.informedcitizen.data.cache

import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.MemberVotes
import com.informedcitizen.pipeline.model.MembersIndex
import com.informedcitizen.pipeline.model.SessionCalendar

/** A cached artifact plus its provenance and freshness metadata. */
data class CachedArtifact<T>(
    val value: T,
    val source: BillSource,
    val generatedAt: String,
    val fetchedAtMillis: Long,
)

/**
 * Persistent output cache for the per-Congress members index. Backed
 * by SQLDelight in production; tests use [com.informedcitizen.testutil]
 * fakes. Same provenance model as [BillCache]: the published path and
 * the BYOK in-app pipeline write the same shape under different
 * [BillSource] tags, and the UI reads one source of truth.
 */
interface MembersIndexCache {
    suspend fun replaceForSource(source: BillSource, index: MembersIndex, fetchedAtMillis: Long)

    suspend fun load(congress: Int, source: BillSource): CachedArtifact<MembersIndex>?

    /** The most recently fetched index for [congress], regardless of source. */
    suspend fun loadFreshest(congress: Int): CachedArtifact<MembersIndex>?

    suspend fun clearSource(source: BillSource)
}

/** Persistent output cache for the session calendar. */
interface SessionCalendarCache {
    suspend fun replaceForSource(source: BillSource, calendar: SessionCalendar, fetchedAtMillis: Long)

    suspend fun load(source: BillSource): CachedArtifact<SessionCalendar>?

    /** The most recently fetched calendar, regardless of source. */
    suspend fun loadFreshest(): CachedArtifact<SessionCalendar>?

    suspend fun clearSource(source: BillSource)
}

/** Persistent output cache for the federal election calendar. */
interface ElectionCalendarCache {
    suspend fun replaceForSource(source: BillSource, calendar: ElectionCalendar, fetchedAtMillis: Long)

    suspend fun load(source: BillSource): CachedArtifact<ElectionCalendar>?

    /** The most recently fetched calendar, regardless of source. */
    suspend fun loadFreshest(): CachedArtifact<ElectionCalendar>?

    suspend fun clearSource(source: BillSource)
}

/**
 * Persistent output cache for per-member roll-call position shards
 * (`votes/members/{bioguideId}.json`), keyed by bioguide ID. Fetched
 * once per saved rep — never per bill — so the bills list can badge
 * saved reps' votes without eager per-bill downloads.
 */
interface MemberVotesCache {
    suspend fun replaceForSource(source: BillSource, votes: MemberVotes, fetchedAtMillis: Long)

    suspend fun load(bioguideId: String, source: BillSource): CachedArtifact<MemberVotes>?

    /** The most recently fetched shard for [bioguideId], regardless of source. */
    suspend fun loadFreshest(bioguideId: String): CachedArtifact<MemberVotes>?

    suspend fun clearSource(source: BillSource)
}

/** Freshest bills snapshot for the offline cold-start fallback. */
data class FreshestBills(
    val congress: Int,
    val source: BillSource,
    val bills: List<Bill>,
    val generatedAt: String,
    val fetchedAtMillis: Long,
)
