package com.informedcitizen.testutil

import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.data.cache.CachedArtifact
import com.informedcitizen.data.cache.MembersIndexCache
import com.informedcitizen.data.cache.SessionCalendarCache
import com.informedcitizen.pipeline.model.MembersIndex
import com.informedcitizen.pipeline.model.SessionCalendar

/** In-memory fake of [MembersIndexCache] for repository tests. */
class FakeMembersIndexCache : MembersIndexCache {
    private val byKey = mutableMapOf<Pair<Int, BillSource>, CachedArtifact<MembersIndex>>()

    override suspend fun replaceForSource(
        source: BillSource,
        index: MembersIndex,
        fetchedAtMillis: Long,
    ) {
        byKey[index.congress to source] = CachedArtifact(
            value = index,
            source = source,
            generatedAt = index.generatedAt,
            fetchedAtMillis = fetchedAtMillis,
        )
    }

    override suspend fun load(congress: Int, source: BillSource): CachedArtifact<MembersIndex>? =
        byKey[congress to source]

    override suspend fun loadFreshest(congress: Int): CachedArtifact<MembersIndex>? =
        byKey.entries.filter { it.key.first == congress }
            .maxByOrNull { it.value.fetchedAtMillis }?.value

    override suspend fun clearSource(source: BillSource) {
        byKey.keys.removeAll { it.second == source }
    }
}

/** In-memory fake of [SessionCalendarCache] for repository tests. */
class FakeSessionCalendarCache : SessionCalendarCache {
    private val bySource = mutableMapOf<BillSource, CachedArtifact<SessionCalendar>>()

    override suspend fun replaceForSource(
        source: BillSource,
        calendar: SessionCalendar,
        fetchedAtMillis: Long,
    ) {
        bySource[source] = CachedArtifact(
            value = calendar,
            source = source,
            generatedAt = calendar.generatedAt,
            fetchedAtMillis = fetchedAtMillis,
        )
    }

    override suspend fun load(source: BillSource): CachedArtifact<SessionCalendar>? =
        bySource[source]

    override suspend fun loadFreshest(): CachedArtifact<SessionCalendar>? =
        bySource.values.maxByOrNull { it.fetchedAtMillis }

    override suspend fun clearSource(source: BillSource) {
        bySource.remove(source)
    }
}
