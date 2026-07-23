package com.informedcitizen.data.cache

import com.informedcitizen.cache.BillSummaryDatabase
import com.informedcitizen.pipeline.lifecycleStatusToWireString
import com.informedcitizen.pipeline.model.Bill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class SqlDelightBillCache(
    private val db: BillSummaryDatabase,
    private val json: Json = DefaultJson,
) : BillCache {

    private val q = db.billCacheQueries

    override suspend fun replaceForSource(
        congress: Int,
        source: BillSource,
        bills: List<Bill>,
        generatedAt: String,
        fetchedAtMillis: Long,
    ) {
        withContext(Dispatchers.IO) {
            db.transaction {
                q.clearCongressForSource(congress = congress.toLong(), source = source.wireString)
                for (bill in bills) {
                    q.upsertBill(
                        bill_id = bill.id,
                        congress = bill.congress.toLong(),
                        source = source.wireString,
                        latest_action_date = bill.latestAction.date,
                        payload = json.encodeToString(Bill.serializer(), bill),
                        fetched_at = fetchedAtMillis,
                        // Whole-manifest / BYOK writes are a single logical
                        // shard 0; the sharded read path (backlog #41) will set
                        // real shard indices when it lands. status/policy_area
                        // are extracted into their own columns for DB-side
                        // filtering; payload stays authoritative for the rest.
                        shard_index = 0,
                        status = bill.lifecycleStatus?.let(::lifecycleStatusToWireString),
                        policy_area = bill.policyArea,
                    )
                }
                q.upsertManifest(
                    congress = congress.toLong(),
                    source = source.wireString,
                    generated_at = generatedAt,
                    fetched_at = fetchedAtMillis,
                )
            }
        }
    }

    override suspend fun loadBills(congress: Int, source: BillSource): List<Bill> =
        withContext(Dispatchers.IO) {
            q.selectBillsByCongressAndSource(
                congress = congress.toLong(),
                source = source.wireString,
            ).executeAsList().map { json.decodeFromString(Bill.serializer(), it) }
        }

    override suspend fun loadAllForCongress(congress: Int): List<Bill> =
        withContext(Dispatchers.IO) {
            q.selectBillsByCongress(congress = congress.toLong())
                .executeAsList()
                .map { json.decodeFromString(Bill.serializer(), it) }
        }

    override suspend fun loadManifest(congress: Int, source: BillSource): CachedManifestMeta? =
        withContext(Dispatchers.IO) {
            q.selectManifest(
                congress = congress.toLong(),
                source = source.wireString,
            ).executeAsOneOrNull()?.let {
                CachedManifestMeta(generatedAt = it.generated_at, fetchedAtMillis = it.fetched_at)
            }
        }

    override suspend fun loadFreshest(): FreshestBills? =
        withContext(Dispatchers.IO) {
            val row = q.selectFreshestManifest().executeAsOneOrNull()
                ?: return@withContext null
            val source = BillSource.fromWire(row.source) ?: return@withContext null
            val bills = q.selectBillsByCongressAndSource(
                congress = row.congress,
                source = row.source,
            ).executeAsList().map { json.decodeFromString(Bill.serializer(), it) }
            FreshestBills(
                congress = row.congress.toInt(),
                source = source,
                bills = bills,
                generatedAt = row.generated_at,
                fetchedAtMillis = row.fetched_at,
            )
        }

    override suspend fun clearSource(congress: Int, source: BillSource) {
        withContext(Dispatchers.IO) {
            db.transaction {
                q.clearCongressForSource(congress = congress.toLong(), source = source.wireString)
                q.clearShardCursorForSource(congress = congress.toLong(), source = source.wireString)
                // Manifest meta clear is row-scoped to (congress, source) so
                // hand-rolled rather than via a query (no per-row delete query
                // defined — the bulk clearAll path is rare).
            }
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            db.transaction {
                q.clearAllCachedBills()
                q.clearAllManifestMeta()
                q.clearAllShardCursors()
            }
        }
    }

    override suspend fun appendShard(
        congress: Int,
        source: BillSource,
        shardIndex: Int,
        bills: List<Bill>,
        generatedAt: String,
        fetchedAtMillis: Long,
        totalShards: Int,
        pageSize: Int,
    ) {
        withContext(Dispatchers.IO) {
            db.transaction {
                // Prune this shard's prior rows so a re-fetched shard drops
                // its own vanished bills without touching other shards.
                q.clearShardForSource(
                    congress = congress.toLong(),
                    source = source.wireString,
                    shardIndex = shardIndex.toLong(),
                )
                for (bill in bills) {
                    q.upsertBill(
                        bill_id = bill.id,
                        congress = bill.congress.toLong(),
                        source = source.wireString,
                        latest_action_date = bill.latestAction.date,
                        payload = json.encodeToString(Bill.serializer(), bill),
                        fetched_at = fetchedAtMillis,
                        shard_index = shardIndex.toLong(),
                        status = bill.lifecycleStatus?.let(::lifecycleStatusToWireString),
                        policy_area = bill.policyArea,
                    )
                }
                q.upsertShardCursor(
                    congress = congress.toLong(),
                    source = source.wireString,
                    next_shard_index = (shardIndex + 1).toLong(),
                    total_shards = totalShards.toLong(),
                    page_size = pageSize.toLong(),
                )
                q.upsertManifest(
                    congress = congress.toLong(),
                    source = source.wireString,
                    generated_at = generatedAt,
                    fetched_at = fetchedAtMillis,
                )
            }
        }
    }

    override suspend fun loadShardCursor(congress: Int, source: BillSource): ShardCursor? =
        withContext(Dispatchers.IO) {
            q.selectShardCursor(
                congress = congress.toLong(),
                source = source.wireString,
            ).executeAsOneOrNull()?.let {
                ShardCursor(
                    nextShardIndex = it.next_shard_index.toInt(),
                    totalShards = it.total_shards.toInt(),
                    pageSize = it.page_size.toInt(),
                )
            }
        }

    override suspend fun loadBillsPaged(
        congress: Int,
        source: BillSource,
        status: String?,
        policyArea: String?,
        limit: Int,
        offset: Int,
    ): List<Bill> =
        withContext(Dispatchers.IO) {
            q.selectBillsPaged(
                congress = congress.toLong(),
                source = source.wireString,
                status = status,
                policyArea = policyArea,
                limit = limit.toLong(),
                offset = offset.toLong(),
            ).executeAsList().map { json.decodeFromString(Bill.serializer(), it) }
        }

    override suspend fun countBills(
        congress: Int,
        source: BillSource,
        status: String?,
        policyArea: String?,
    ): Int =
        withContext(Dispatchers.IO) {
            q.countBillsPaged(
                congress = congress.toLong(),
                source = source.wireString,
                status = status,
                policyArea = policyArea,
            ).executeAsOne().toInt()
        }

    override suspend fun distinctPolicyAreas(congress: Int, source: BillSource): List<String> =
        withContext(Dispatchers.IO) {
            q.selectDistinctPolicyAreas(
                congress = congress.toLong(),
                source = source.wireString,
            ).executeAsList()
        }

    private companion object {
        val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
