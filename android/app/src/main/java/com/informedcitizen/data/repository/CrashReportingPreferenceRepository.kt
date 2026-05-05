package com.informedcitizen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashReportingPreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val enabled: Flow<Boolean> = dataStore.data
        .map { it[KEY] ?: false }
        .catch { emit(false) }

    suspend fun set(enabled: Boolean) {
        dataStore.edit { it[KEY] = enabled }
    }

    private companion object {
        val KEY = booleanPreferencesKey("crash_reporting_enabled")
    }
}
