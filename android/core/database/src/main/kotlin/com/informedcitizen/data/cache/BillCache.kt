package com.informedcitizen.data.cache

import com.informedcitizen.pipeline.model.Bill

/**
 * Provenance tag stored on each cached row. The UI reads one source of
 * truth regardless of which fetch path populated it; this lets a cache
 * clear target one path without nuking the other.
 *
 * - PUBLISHED: Retrofit reading the bot-published JSON manifests.
 * - BYOK: in-app pipeline (Ktor) hitting upstream APIs with the user's keys.
 */
enum class BillSource(val wireString: String) {
    PUBLISHED("published"),
    BYOK("byok"),
    ;

    companion object {
        fun fromWire(value: String): BillSource? = entries.firstOrNull { it.wireString == value }
    }
}

/** Manifest-level freshness metadata. Mirrors `BillsManifest.generatedAt` plus a local fetch timestamp. */
data class CachedManifestMeta(
    val generatedAt: String,
    val fetchedAtMillis: Long,
)

/**
 * Append cursor for the sharded bills read path (backlog #41). Mirrors
 * one `cached_shard_cursor` row: [nextShardIndex] is the next #40 shard
 * the paging mediator should fetch; [totalShards] (from the published
 * `BillShardIndex`) lets the UI show an honest "showing N of ~M" footer
 * when the network is down mid-append; [pageSize] records the shard
 * contract's page size. A Congress still on the single
 * `congressNNN_bills.json` is treated as exactly one shard, so the same
 * cursor drives the no-regression whole-manifest case.
 */
data class ShardCursor(
    val nextShardIndex: Int,
    val totalShards: Int,
    val pageSize: Int,
) {
    /** True once every published shard has been appended into the cache. */
    val isComplete: Boolean get() = nextShardIndex >= totalShards
}

/**
 * Persistent output cache for bill records. Backed by SQLDelight in
 * production; tests should use a fake or an in-memory driver.
 *
 * Reads return [Bill] domain objects directly — the cache deserializes
 * the payload column. Writes accept [Bill] objects and persist as a
 * JSON blob with extracted columns for the common query paths
 * (congress filter + latest_action_date sort).
 */
interface BillCache {
    suspend fun replaceForSource(
        congress: Int,
        source: BillSource,
        bills: List<Bill>,
        generatedAt: String,
        fetchedAtMillis: Long,
    )

    suspend fun loadBills(congress: Int, source: BillSource): List<Bill>

    suspend fun loadAllForCongress(congress: Int): List<Bill>

    suspend fun loadManifest(congress: Int, source: BillSource): CachedManifestMeta?

    /**
     * The most recently written (congress, source) snapshot — the
     * offline cold-start fallback. Returns null when the cache has
     * never been populated.
     */
    suspend fun loadFreshest(): FreshestBills?

    suspend fun clearSource(congress: Int, source: BillSource)

    suspend fun clearAll()

    // ---- sharded paging (backlog #41) ------------------------------------
    //
    // The methods above operate on a whole Congress at once (the single
    // `congressNNN_bills.json` path). The methods below drive the sharded
    // read path: the paging mediator appends one #40 shard at a time and
    // the UI reads DB-filtered pages, so the ~10k-bill broadened manifest
    // (#39) never has to be held in memory as one list.

    /**
     * Persist one #40 shard's [bills] under [source], tagged with
     * [shardIndex], and advance the [ShardCursor] to `shardIndex + 1`.
     * Transactional: the shard's prior rows are pruned first (via
     * `clearShardForSource`) so a re-fetched shard drops its own vanished
     * bills without touching shards the mediator never re-requested. The
     * extracted `status` / `policy_area` columns are populated so
     * [loadBillsPaged] can filter in SQL. [totalShards] and [pageSize]
     * come from the published `BillShardIndex`; [generatedAt] /
     * [fetchedAtMillis] refresh the manifest freshness metadata.
     */
    suspend fun appendShard(
        congress: Int,
        source: BillSource,
        shardIndex: Int,
        bills: List<Bill>,
        generatedAt: String,
        fetchedAtMillis: Long,
        totalShards: Int,
        pageSize: Int,
    )

    /** The append cursor for [congress]/[source], or null before the first append. */
    suspend fun loadShardCursor(congress: Int, source: BillSource): ShardCursor?

    /**
     * A DB-filtered, recency-ordered page of cached bills. [status] (the
     * #39 lifecycle-status wire string) and [policyArea] are optional
     * filters — a null matches every row. Ordering is
     * `latest_action_date DESC, bill_id` so pages are stable across
     * [limit]/[offset] boundaries.
     */
    suspend fun loadBillsPaged(
        congress: Int,
        source: BillSource,
        status: String?,
        policyArea: String?,
        limit: Int,
        offset: Int,
    ): List<Bill>

    /** Total cached bills matching the same optional [status]/[policyArea] filters as [loadBillsPaged]. */
    suspend fun countBills(
        congress: Int,
        source: BillSource,
        status: String?,
        policyArea: String?,
    ): Int

    /** Distinct non-null policy areas cached for [congress]/[source], sorted — the #10 filter dropdown source. */
    suspend fun distinctPolicyAreas(congress: Int, source: BillSource): List<String>
}
