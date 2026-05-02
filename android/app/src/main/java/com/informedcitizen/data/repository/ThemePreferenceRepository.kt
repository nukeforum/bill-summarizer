package com.informedcitizen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.informedcitizen.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preference: Flow<ThemePreference> = dataStore.data
        .map { ThemePreference.fromStored(it[KEY]) }
        .catch { emit(ThemePreference.DEFAULT) }

    suspend fun set(pref: ThemePreference) {
        dataStore.edit { it[KEY] = pref.name }
    }

    private companion object {
        val KEY = stringPreferencesKey("theme_preference")
    }
}
