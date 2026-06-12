package com.informedcitizen.testutil

import com.informedcitizen.data.cache.BillCache
import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.data.cache.CachedManifestMeta
import com.informedcitizen.data.cache.FreshestBills
import com.informedcitizen.pipeline.model.Bill

/**
 * In-memory fake of [BillCache] for repository / view-model tests.
 * Records every write so tests can assert what was persisted under
 * which [BillSource] without spinning up SQLDelight.
 */
class FakeBillCache : BillCache {
    data class WriteCall(
        val congress: Int,
        val source: BillSource,
        val bills: List<Bill>,
        val generatedAt: String,
        val fetchedAtMillis: Long,
    )

    val writes: MutableList<WriteCall> = mutableListOf()
    private val byKey: MutableMap<Pair<Int, BillSource>, List<Bill>> = mutableMapOf()
    private val manifestByKey: MutableMap<Pair<Int, BillSource>, CachedManifestMeta> = mutableMapOf()

    override suspend fun replaceForSource(
        congress: Int,
        source: BillSource,
        bills: List<Bill>,
        generatedAt: String,
        fetchedAtMillis: Long,
    ) {
        writes += WriteCall(congress, source, bills, generatedAt, fetchedAtMillis)
        byKey[congress to source] = bills
        manifestByKey[congress to source] = CachedManifestMeta(generatedAt, fetchedAtMillis)
    }

    override suspend fun loadBills(congress: Int, source: BillSource): List<Bill> =
        byKey[congress to source].orEmpty()

    override suspend fun loadAllForCongress(congress: Int): List<Bill> =
        byKey.entries.filter { it.key.first == congress }.flatMap { it.value }

    override suspend fun loadManifest(congress: Int, source: BillSource): CachedManifestMeta? =
        manifestByKey[congress to source]

    override suspend fun loadFreshest(): FreshestBills? =
        manifestByKey.entries.maxByOrNull { it.value.fetchedAtMillis }?.let { (key, meta) ->
            FreshestBills(
                congress = key.first,
                source = key.second,
                bills = byKey[key].orEmpty(),
                generatedAt = meta.generatedAt,
                fetchedAtMillis = meta.fetchedAtMillis,
            )
        }

    override suspend fun clearSource(congress: Int, source: BillSource) {
        byKey.remove(congress to source)
        manifestByKey.remove(congress to source)
    }

    override suspend fun clearAll() {
        byKey.clear()
        manifestByKey.clear()
        writes.clear()
    }
}
