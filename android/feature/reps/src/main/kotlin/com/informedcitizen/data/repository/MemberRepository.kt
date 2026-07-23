package com.informedcitizen.data.repository

import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.pipeline.model.MemberLegislation
import com.informedcitizen.pipeline.model.MemberVotes
import com.informedcitizen.pipeline.model.MembersIndex

data class RepsForLocation(val house: List<Member>, val senators: List<Member>)

interface MemberRepository {
    suspend fun findRepsForLocation(
        congress: Int,
        stateCode: String,
        district: Int?,
    ): RepsForLocation

    /**
     * Resolves saved bioguide IDs against the current Congress's index.
     * Members are partitioned by chamber. IDs not present in the index are
     * dropped (signalling a stale save, which the ViewModel detects by
     * counting). The order within each list mirrors the index order.
     */
    suspend fun findRepsByIds(congress: Int, bioguideIds: Set<String>): RepsForLocation

    suspend fun getMember(bioguideId: String, congress: Int): Member?

    suspend fun getSponsored(bioguideId: String): MemberLegislation?

    suspend fun getCosponsored(bioguideId: String): MemberLegislation?

    /**
     * The member's per-member roll-call positions shard
     * (`votes/members/<bioguideId>.json`), newest first. A member with
     * no recorded votes (a new member, or a 404 shard) resolves to an
     * empty [MemberVotes]; a genuine fetch failure resolves to null.
     */
    suspend fun getVotes(bioguideId: String): MemberVotes?

    /** Returns the cached or freshly-loaded index, or null on failure. */
    suspend fun getIndex(congress: Int): MembersIndex?
}
