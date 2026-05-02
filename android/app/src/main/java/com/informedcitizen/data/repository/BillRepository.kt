package com.informedcitizen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.model.Bill
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepository @Inject constructor(
    private val api: BillsApi,
    private val dataStore: DataStore<Preferences>,
) {
    private val mutex = Mutex()
    private var cached: List<Bill>? = null

    suspend fun getBills(forceRefresh: Boolean = false): Result<List<Bill>> = mutex.withLock {
        if (!forceRefresh) {
            cached?.let { return@withLock Result.success(it) }
        }
        runCatching {
            val manifest = api.getBills()
            cached = manifest.bills
            dataStore.edit { it[LAST_FETCHED_KEY] = System.currentTimeMillis() }
            manifest.bills
        }
    }

    fun getBillById(id: String): Bill? = cached?.firstOrNull { it.id == id }

    suspend fun lastFetchedAtMillis(): Long? =
        dataStore.data.firstOrNull()?.get(LAST_FETCHED_KEY)

    private companion object {
        val LAST_FETCHED_KEY = longPreferencesKey("last_fetched_at_millis")
    }
}
