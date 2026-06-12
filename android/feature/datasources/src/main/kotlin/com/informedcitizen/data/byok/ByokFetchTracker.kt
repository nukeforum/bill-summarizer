package com.informedcitizen.data.byok

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** The three artifacts BYOK refreshes, each on its own cadence. */
enum class ByokArtifact(
    val prefsKeySuffix: String,
    val cadence: Duration,
) {
    /** Daily — matches the canonical update-bills.yml schedule. */
    BILLS("bills", 1.days),

    /** Weekly — matches the canonical Sunday update-members.yml schedule. */
    MEMBERS("members", 7.days),

    /** Weekly — the upstream feeds change at most a few times a year. */
    CALENDAR("calendar", 7.days),
}

/**
 * Per-artifact last-success timestamps, used by [ByokFetchWorker] to
 * decide which artifacts are due on each daily tick.
 */
@Singleton
class ByokFetchTracker @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    fun lastSuccessMillis(artifact: ByokArtifact): Flow<Long?> =
        dataStore.data.map { it[key(artifact)] }

    suspend fun recordSuccess(artifact: ByokArtifact, nowMillis: Long) {
        dataStore.edit { it[key(artifact)] = nowMillis }
    }

    suspend fun isDue(artifact: ByokArtifact, nowMillis: Long): Boolean {
        val last = lastSuccessMillis(artifact).firstOrNull() ?: return true
        return nowMillis - last >= artifact.cadence.inWholeMilliseconds
    }

    private fun key(artifact: ByokArtifact) =
        longPreferencesKey("byok_last_success_${artifact.prefsKeySuffix}")
}
