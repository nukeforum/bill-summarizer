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

    suspend fun clearSource(congress: Int, source: BillSource)

    suspend fun clearAll()
}
