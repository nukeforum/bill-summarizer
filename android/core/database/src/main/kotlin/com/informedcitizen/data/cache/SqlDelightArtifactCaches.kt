package com.informedcitizen.data.cache

import com.informedcitizen.cache.BillSummaryDatabase
import com.informedcitizen.pipeline.model.MembersIndex
import com.informedcitizen.pipeline.model.SessionCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val ArtifactJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}

class SqlDelightMembersIndexCache(
    private val db: BillSummaryDatabase,
    private val json: Json = ArtifactJson,
) : MembersIndexCache {

    private val q = db.artifactCacheQueries

    override suspend fun replaceForSource(
        source: BillSource,
        index: MembersIndex,
        fetchedAtMillis: Long,
    ) {
        withContext(Dispatchers.IO) {
            q.upsertMembersIndex(
                congress = index.congress.toLong(),
                source = source.wireString,
                payload = json.encodeToString(MembersIndex.serializer(), index),
                generated_at = index.generatedAt,
                fetched_at = fetchedAtMillis,
            )
        }
    }

    override suspend fun load(congress: Int, source: BillSource): CachedArtifact<MembersIndex>? =
        withContext(Dispatchers.IO) {
            q.selectMembersIndex(congress = congress.toLong(), source = source.wireString)
                .executeAsOneOrNull()
                ?.let { row ->
                    CachedArtifact(
                        value = json.decodeFromString(MembersIndex.serializer(), row.payload),
                        source = source,
                        generatedAt = row.generated_at,
                        fetchedAtMillis = row.fetched_at,
                    )
                }
        }

    override suspend fun loadFreshest(congress: Int): CachedArtifact<MembersIndex>? =
        withContext(Dispatchers.IO) {
            q.selectFreshestMembersIndex(congress = congress.toLong())
                .executeAsOneOrNull()
                ?.let { row ->
                    val source = BillSource.fromWire(row.source) ?: return@let null
                    CachedArtifact(
                        value = json.decodeFromString(MembersIndex.serializer(), row.payload),
                        source = source,
                        generatedAt = row.generated_at,
                        fetchedAtMillis = row.fetched_at,
                    )
                }
        }

    override suspend fun clearSource(source: BillSource) {
        withContext(Dispatchers.IO) {
            q.clearMembersIndexForSource(source = source.wireString)
        }
    }
}

class SqlDelightSessionCalendarCache(
    private val db: BillSummaryDatabase,
    private val json: Json = ArtifactJson,
) : SessionCalendarCache {

    private val q = db.artifactCacheQueries

    override suspend fun replaceForSource(
        source: BillSource,
        calendar: SessionCalendar,
        fetchedAtMillis: Long,
    ) {
        withContext(Dispatchers.IO) {
            q.upsertSessionCalendar(
                source = source.wireString,
                payload = json.encodeToString(SessionCalendar.serializer(), calendar),
                generated_at = calendar.generatedAt,
                fetched_at = fetchedAtMillis,
            )
        }
    }

    override suspend fun load(source: BillSource): CachedArtifact<SessionCalendar>? =
        withContext(Dispatchers.IO) {
            q.selectSessionCalendar(source = source.wireString)
                .executeAsOneOrNull()
                ?.let { row ->
                    CachedArtifact(
                        value = json.decodeFromString(SessionCalendar.serializer(), row.payload),
                        source = source,
                        generatedAt = row.generated_at,
                        fetchedAtMillis = row.fetched_at,
                    )
                }
        }

    override suspend fun loadFreshest(): CachedArtifact<SessionCalendar>? =
        withContext(Dispatchers.IO) {
            q.selectFreshestSessionCalendar()
                .executeAsOneOrNull()
                ?.let { row ->
                    val source = BillSource.fromWire(row.source) ?: return@let null
                    CachedArtifact(
                        value = json.decodeFromString(SessionCalendar.serializer(), row.payload),
                        source = source,
                        generatedAt = row.generated_at,
                        fetchedAtMillis = row.fetched_at,
                    )
                }
        }

    override suspend fun clearSource(source: BillSource) {
        withContext(Dispatchers.IO) {
            q.clearSessionCalendarForSource(source = source.wireString)
        }
    }
}
