package com.informedcitizen.data.repository

import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.data.api.MembersApi
import com.informedcitizen.data.model.Member
import com.informedcitizen.data.model.MemberLegislation
import com.informedcitizen.data.model.MembersIndex
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

data class RepsForLocation(val house: List<Member>, val senators: List<Member>)

@Singleton
class MemberRepository @Inject constructor(
    private val api: MembersApi,
    private val crashReporter: CrashReporter,
) {
    private val mutex = Mutex()
    private var indexCache: MembersIndex? = null

    private suspend fun loadIndex(congress: Int): MembersIndex = mutex.withLock {
        indexCache?.let { if (it.congress == congress) return@withLock it }
        val key = "%03d".format(congress)
        runCatching { api.getMembersIndex(key) }
            .onFailure { crashReporter.recordNonFatal(it, "members index fetch failed") }
            .getOrThrow()
            .also { indexCache = it }
    }

    suspend fun findRepsForLocation(
        congress: Int,
        stateCode: String,
        district: Int?,
    ): RepsForLocation {
        val index = loadIndex(congress)
        val state = stateCode.uppercase()
        val house = if (district == null) emptyList() else index.members.filter {
            it.state == state && it.chamber == "house" && it.district == district
        }
        val senators = index.members.filter { it.state == state && it.chamber == "senate" }
        return RepsForLocation(house = house, senators = senators)
    }

    suspend fun getMember(bioguideId: String, congress: Int): Member? {
        val index = runCatching { loadIndex(congress) }.getOrNull() ?: return null
        return index.members.firstOrNull { it.bioguideId == bioguideId }
    }

    suspend fun getSponsored(bioguideId: String): MemberLegislation? =
        fetchLegislation(bioguideId) { api.getSponsored(it) }

    suspend fun getCosponsored(bioguideId: String): MemberLegislation? =
        fetchLegislation(bioguideId) { api.getCosponsored(it) }

    private suspend fun fetchLegislation(
        bioguideId: String,
        block: suspend (String) -> MemberLegislation,
    ): MemberLegislation? = runCatching { block(bioguideId) }
        .recover { exc ->
            if (exc is HttpException && exc.code() == 404) {
                MemberLegislation(bioguideId, congress = 0, kind = "", generatedAt = "", bills = emptyList())
            } else {
                throw exc
            }
        }
        .onFailure { crashReporter.recordNonFatal(it, "member legislation fetch failed") }
        .getOrNull()
}
