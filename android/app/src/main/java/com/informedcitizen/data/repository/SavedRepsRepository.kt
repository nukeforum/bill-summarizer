package com.informedcitizen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedRepsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val savedIds: Flow<Set<String>> = dataStore.data
        .map { prefs -> prefs[BIOGUIDE_IDS_KEY] ?: emptySet() }
        .catch { emit(emptySet()) }

    suspend fun set(bioguideIds: Set<String>) {
        dataStore.edit { prefs ->
            if (bioguideIds.isEmpty()) {
                prefs.remove(BIOGUIDE_IDS_KEY)
            } else {
                prefs[BIOGUIDE_IDS_KEY] = bioguideIds
            }
        }
    }

    suspend fun forget() {
        dataStore.edit { prefs -> prefs.remove(BIOGUIDE_IDS_KEY) }
    }

    private companion object {
        val BIOGUIDE_IDS_KEY = stringSetPreferencesKey("saved_rep_bioguide_ids")
    }
}
