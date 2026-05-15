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
import com.informedcitizen.pipeline.model.BillsManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    fun observeAll(): Flow<List<Bill>> = _bills.asStateFlow()

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
            val now = System.currentTimeMillis()
            dataStore.edit { it[LAST_FETCHED_KEY] = now }
            writeThroughCache(manifest, fetchedAtMillis = now)
            manifest.bills
        }.onFailure { crashReporter.recordNonFatal(it, "manifest fetch failed") }
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
