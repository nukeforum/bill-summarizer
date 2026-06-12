package com.informedcitizen.data.repository

import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.data.api.MembersApi
import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.data.cache.MembersIndexCache
import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.pipeline.model.MemberLegislation
import com.informedcitizen.pipeline.model.MembersIndex
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedMemberRepository @Inject constructor(
    private val api: MembersApi,
    private val crashReporter: CrashReporter,
    private val persistentCache: MembersIndexCache,
) : MemberRepository {
    private val mutex = Mutex()
    private var indexCache: MembersIndex? = null

    private suspend fun loadIndex(congress: Int): MembersIndex = mutex.withLock {
        indexCache?.let { if (it.congress == congress) return@withLock it }
        val key = "%03d".format(congress)
        runCatching { api.getMembersIndex(key) }
            .onSuccess { writeThroughCache(it) }
            .recoverCatching { networkError ->
                // Offline fallback: freshest persisted index (either
                // source). The failure is still reported as a non-fatal.
                crashReporter.recordNonFatal(networkError, "members index fetch failed")
                persistentCache.loadFreshest(congress)?.value ?: throw networkError
            }
            .getOrThrow()
            .also { indexCache = it }
    }

    private suspend fun writeThroughCache(index: MembersIndex) {
        runCatching {
            persistentCache.replaceForSource(
                source = BillSource.PUBLISHED,
                index = index,
                fetchedAtMillis = System.currentTimeMillis(),
            )
        }.onFailure { crashReporter.recordNonFatal(it, "members cache write-through failed") }
    }

    /**
     * Accept an index the in-app BYOK pipeline just produced: replace
     * the in-memory index the UI reads and persist under
     * [BillSource.BYOK]. Last-write-wins with the published path.
     */
    suspend fun publishByokIndex(index: MembersIndex) {
        mutex.withLock { indexCache = index }
        runCatching {
            persistentCache.replaceForSource(
                source = BillSource.BYOK,
                index = index,
                fetchedAtMillis = System.currentTimeMillis(),
            )
        }.onFailure { crashReporter.recordNonFatal(it, "byok members cache write failed") }
    }

    override suspend fun findRepsForLocation(
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

    override suspend fun findRepsByIds(
        congress: Int,
        bioguideIds: Set<String>,
    ): RepsForLocation {
        if (bioguideIds.isEmpty()) return RepsForLocation(emptyList(), emptyList())
        val index = loadIndex(congress)
        val matched = index.members.filter { it.bioguideId in bioguideIds }
        val house = matched.filter { it.chamber == "house" }
        val senators = matched.filter { it.chamber == "senate" }
        return RepsForLocation(house = house, senators = senators)
    }

    override suspend fun getMember(bioguideId: String, congress: Int): Member? {
        val index = runCatching { loadIndex(congress) }.getOrNull() ?: return null
        return index.members.firstOrNull { it.bioguideId == bioguideId }
    }

    override suspend fun getIndex(congress: Int): MembersIndex? =
        runCatching { loadIndex(congress) }
            .onFailure { crashReporter.recordNonFatal(it, "members index fetch failed") }
            .getOrNull()

    override suspend fun getSponsored(bioguideId: String): MemberLegislation? =
        fetchLegislation(bioguideId) { api.getSponsored(it) }

    override suspend fun getCosponsored(bioguideId: String): MemberLegislation? =
        fetchLegislation(bioguideId) { api.getCosponsored(it) }

    private suspend fun fetchLegislation(
        bioguideId: String,
        block: suspend (String) -> MemberLegislation,
    ): MemberLegislation? = runCatching { block(bioguideId) }
        .map { it.withRenderableBillsOnly() }
        .recover { exc ->
            if (exc is HttpException && exc.code() == 404) {
                MemberLegislation(bioguideId, congress = 0, kind = "", generatedAt = "", bills = emptyList())
            } else {
                throw exc
            }
        }
        .onFailure { crashReporter.recordNonFatal(it, "member legislation fetch failed") }
        .getOrNull()

    // Upstream JSON fixtures occasionally contain entries with empty type/number,
    // which collapse to a non-unique id like "-119" and crash LazyColumn on the
    // member detail screen. Drop them at ingestion since they have no renderable
    // identifier and no resolvable congress.gov URL.
    private fun MemberLegislation.withRenderableBillsOnly(): MemberLegislation =
        copy(bills = bills.filter { it.type.isNotBlank() && it.number.isNotBlank() })
}
