package com.informedcitizen.testutil

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [DataStore] of [Preferences] for unit tests. Avoids the
 * file-locking issues with `PreferenceDataStoreFactory.create` +
 * `TemporaryFolder` on Windows, while still honouring the contract that
 * the production code consumes ([data] flow + [updateData] suspend).
 */
class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val _state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> get() = _state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        var updated: Preferences = emptyPreferences()
        _state.update { current -> transform(current).also { updated = it } }
        return updated
    }
}
