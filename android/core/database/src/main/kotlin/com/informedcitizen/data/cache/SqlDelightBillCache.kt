package com.informedcitizen.data.cache

import com.informedcitizen.cache.BillSummaryDatabase
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

    override suspend fun clearSource(congress: Int, source: BillSource) {
        withContext(Dispatchers.IO) {
            db.transaction {
                q.clearCongressForSource(congress = congress.toLong(), source = source.wireString)
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
            }
        }
    }

    private companion object {
        val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
