package com.informedcitizen.data.repository

import com.informedcitizen.data.model.Member
import com.informedcitizen.data.model.MemberLegislation
import com.informedcitizen.data.model.MembersIndex

data class RepsForLocation(val house: List<Member>, val senators: List<Member>)

interface MemberRepository {
    suspend fun findRepsForLocation(
        congress: Int,
        stateCode: String,
        district: Int?,
    ): RepsForLocation

    suspend fun getMember(bioguideId: String, congress: Int): Member?

    suspend fun getSponsored(bioguideId: String): MemberLegislation?

    suspend fun getCosponsored(bioguideId: String): MemberLegislation?

    /** Returns the cached or freshly-loaded index, or null on failure. */
    suspend fun getIndex(congress: Int): MembersIndex?
}
