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
class RepsContactPreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val hasSeenWebsiteFallbackDialog: Flow<Boolean> = dataStore.data
        .map { it[KEY_SEEN_WEBSITE_FALLBACK_DIALOG] ?: false }
        .catch { emit(false) }

    suspend fun markWebsiteFallbackDialogSeen() {
        dataStore.edit { it[KEY_SEEN_WEBSITE_FALLBACK_DIALOG] = true }
    }

    private companion object {
        val KEY_SEEN_WEBSITE_FALLBACK_DIALOG =
            booleanPreferencesKey("seen_website_fallback_dialog")
    }
}
