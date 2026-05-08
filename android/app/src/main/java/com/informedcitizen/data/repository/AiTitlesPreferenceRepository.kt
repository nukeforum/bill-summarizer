package com.informedcitizen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.informedcitizen.data.work.SummarizationScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiTitlesPreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val enabled: Flow<Boolean> = dataStore.data
        .map { it[KEY_ENABLED] ?: false }
        .catch { emit(false) }

    val scope: Flow<SummarizationScope> = dataStore.data
        .map { it.toScope() }
        .catch { emit(SummarizationScope.DEFAULT) }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setScope(scope: SummarizationScope) {
        dataStore.edit { prefs ->
            prefs[KEY_SCOPE_NAME] = scope.persistName()
            when (scope) {
                is SummarizationScope.Progressive -> prefs[KEY_PROGRESSIVE_CAP] = scope.capPerDay
                else -> prefs.remove(KEY_PROGRESSIVE_CAP)
            }
        }
    }

    private fun Preferences.toScope(): SummarizationScope {
        return when (this[KEY_SCOPE_NAME]) {
            null -> SummarizationScope.DEFAULT
            "FloorActionOnly" -> SummarizationScope.FloorActionOnly
            "Recent60Days" -> SummarizationScope.Recent60Days
            "All" -> SummarizationScope.All
            "Progressive" -> {
                val cap = this[KEY_PROGRESSIVE_CAP]
                if (cap == null || cap !in 1..500) {
                    SummarizationScope.DEFAULT
                } else {
                    SummarizationScope.Progressive(cap)
                }
            }
            else -> SummarizationScope.DEFAULT
        }
    }

    private fun SummarizationScope.persistName(): String = when (this) {
        is SummarizationScope.FloorActionOnly -> "FloorActionOnly"
        is SummarizationScope.Recent60Days -> "Recent60Days"
        is SummarizationScope.Progressive -> "Progressive"
        is SummarizationScope.All -> "All"
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("ai_titles_enabled")
        val KEY_SCOPE_NAME = stringPreferencesKey("ai_titles_scope")
        val KEY_PROGRESSIVE_CAP = intPreferencesKey("ai_titles_progressive_cap")
    }
}
