package com.informedcitizen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.cache.BillCache
import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillShard
import com.informedcitizen.pipeline.model.BillShardIndex
import com.informedcitizen.pipeline.model.BillsManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepository @Inject constructor(
    private val api: BillsApi,
    private val dataStore: DataStore<Preferences>,
    private val crashReporter: CrashReporter,
    private val billCache: BillCache,
) {
    private val mutex = Mutex()
    private val _bills = MutableStateFlow<List<Bill>>(emptyList())
    private val _votesCoverage = MutableStateFlow(false)
    private val _generatedAt = MutableStateFlow<String?>(null)

    fun observeAll(): Flow<List<Bill>> = _bills.asStateFlow()

    /**
     * The `generated_at` timestamp (ISO-8601 UTC) of the manifest behind
     * the current in-memory bills — the artifact's generation time, never
     * the local fetch time. Drives the user-visible freshness indicator.
     * Offline this reflects the cached artifact's own timestamp, so the
     * indicator stays honest ("updated N days ago") rather than resetting.
     */
    val generatedAt: StateFlow<String?> = _generatedAt.asStateFlow()

    /**
     * [BillsManifest.votesCoverage] from the manifest behind the
     * current in-memory bills — the rollout gate for vote surfaces.
     * Coverage is manifest-level and not cached, so the offline
     * cache fallback resets it to false: vote surfaces hide rather
     * than claim a bill had no roll call.
     */
    val votesCoverage: StateFlow<Boolean> = _votesCoverage.asStateFlow()

    suspend fun getBills(forceRefresh: Boolean = false): Result<List<Bill>> = mutex.withLock {
        if (!forceRefresh && _bills.value.isNotEmpty()) {
            return@withLock Result.success(_bills.value)
        }
        runCatching {
            val index = api.getCongressesIndex()
            val entry = index.congresses.firstOrNull { it.congress == index.currentCongress }
                ?: error("congresses.json has no entry for current_congress=${index.currentCongress}")
            val manifest = api.getBillsManifest("data/${entry.manifestPath}")
            _bills.value = manifest.bills
            _votesCoverage.value = manifest.votesCoverage
            _generatedAt.value = manifest.generatedAt
            val now = System.currentTimeMillis()
            dataStore.edit { it[LAST_FETCHED_KEY] = now }
            writeThroughCache(manifest, fetchedAtMillis = now)
            manifest.bills
        }.recoverCatching { networkError ->
            // Offline cold-start fallback: surface the freshest cached
            // snapshot (either source) instead of an empty error state.
            // The original failure is still reported as a non-fatal.
            crashReporter.recordNonFatal(networkError, "manifest fetch failed")
            val cached = billCache.loadFreshest()?.takeIf { it.bills.isNotEmpty() }
                ?: throw networkError
            _bills.value = cached.bills
            _votesCoverage.value = false
            _generatedAt.value = cached.generatedAt
            cached.bills
        }
    }

    /**
     * Accept a manifest the in-app BYOK pipeline just produced: update
     * the in-memory state the UI observes and persist under
     * [BillSource.BYOK]. Last-write-wins with the published path by
     * design — BYOK output for the same Congress is a superset of the
     * published data, fetched moments ago.
     */
    suspend fun publishByokBills(manifest: BillsManifest) = mutex.withLock {
        _bills.value = manifest.bills
        _votesCoverage.value = manifest.votesCoverage
        _generatedAt.value = manifest.generatedAt
        val now = System.currentTimeMillis()
        dataStore.edit { it[LAST_FETCHED_KEY] = now }
        runCatching {
            billCache.replaceForSource(
                congress = manifest.congress,
                source = BillSource.BYOK,
                bills = manifest.bills,
                generatedAt = manifest.generatedAt,
                fetchedAtMillis = now,
            )
        }.onFailure { crashReporter.recordNonFatal(it, "byok bill cache write failed") }
    }

    /**
     * Persist the freshly-fetched [manifest] into the SQLDelight output
     * cache under [BillSource.PUBLISHED]. Best-effort: cache write
     * failures are recorded but do NOT propagate — the in-memory state
     * is already updated and the user-visible bills list works without
     * the cache. Once BYOK lands, the Ktor path will write the same
     * shape under [BillSource.BYOK] and the UI will consume rows from
     * whichever source the user has selected.
     */
    private suspend fun writeThroughCache(manifest: BillsManifest, fetchedAtMillis: Long) {
        runCatching {
            billCache.replaceForSource(
                congress = manifest.congress,
                source = BillSource.PUBLISHED,
                bills = manifest.bills,
                generatedAt = manifest.generatedAt,
                fetchedAtMillis = fetchedAtMillis,
            )
        }.onFailure { crashReporter.recordNonFatal(it, "bill cache write-through failed") }
    }

    /**
     * Discover the sharded bills index (issue #40) for the current
     * Congress, or `null` when this Congress has not been sharded yet.
     *
     * During the dual-publish transition a Congress publishes both the
     * whole [BillsManifest] ([CongressEntry.manifestPath], unchanged) and,
     * once it grows past a single page, the shard set
     * ([CongressEntry.shardIndexPath]). A `null` return means "no shard
     * index published — page the whole manifest instead", so the #41
     * RemoteMediator degrades to the single-manifest path with no shard
     * fetches. This is a plain read and does not touch the in-memory
     * bills state, so it runs outside [mutex].
     */
    suspend fun fetchShardIndex(): BillShardIndex? {
        val index = api.getCongressesIndex()
        val entry = index.congresses.firstOrNull { it.congress == index.currentCongress }
            ?: error("congresses.json has no entry for current_congress=${index.currentCongress}")
        val shardIndexPath = entry.shardIndexPath ?: return null
        return api.getBillShardIndex("data/$shardIndexPath")
    }

    /**
     * Fetch a single [shard]'s page of bills. A shard file has the same
     * wire shape as [BillsManifest] and is resolved under the same
     * `data/` directory as the whole manifest, so it reuses
     * [BillsApi.getBillsManifest]. The #41 RemoteMediator calls this per
     * page and persists the result via [BillCache.appendShard].
     */
    suspend fun fetchShard(shard: BillShard): BillsManifest =
        api.getBillsManifest("data/${shard.path}")

    fun getBillById(id: String): Bill? = _bills.value.firstOrNull { it.id == id }

    suspend fun findById(id: String): Bill? {
        getBillById(id)?.let { return it }
        getBills(forceRefresh = false)
        return getBillById(id)
    }

    fun containsBillId(id: String): Boolean = _bills.value.any { it.id == id }

    suspend fun lastFetchedAtMillis(): Long? =
        dataStore.data.firstOrNull()?.get(LAST_FETCHED_KEY)

    private companion object {
        val LAST_FETCHED_KEY = longPreferencesKey("last_fetched_at_millis")
    }
}
