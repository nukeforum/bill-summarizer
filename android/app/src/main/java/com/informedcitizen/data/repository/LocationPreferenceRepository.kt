package com.informedcitizen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class SavedLocation(val stateCode: String?, val district: Int?)

@Singleton
class LocationPreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val location: Flow<SavedLocation> = dataStore.data
        .map { prefs ->
            SavedLocation(
                stateCode = prefs[STATE_KEY],
                district = prefs[DISTRICT_KEY],
            )
        }
        .catch { emit(SavedLocation(null, null)) }

    suspend fun set(stateCode: String, district: Int?) {
        dataStore.edit { prefs ->
            prefs[STATE_KEY] = stateCode
            if (district == null) {
                prefs.remove(DISTRICT_KEY)
            } else {
                prefs[DISTRICT_KEY] = district
            }
        }
    }

    suspend fun forget() {
        dataStore.edit { prefs ->
            prefs.remove(STATE_KEY)
            prefs.remove(DISTRICT_KEY)
        }
    }

    private companion object {
        val STATE_KEY = stringPreferencesKey("rep_finder_state_code")
        val DISTRICT_KEY = intPreferencesKey("rep_finder_district")
    }
}
