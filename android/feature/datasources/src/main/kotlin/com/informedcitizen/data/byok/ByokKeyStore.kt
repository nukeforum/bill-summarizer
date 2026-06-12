package com.informedcitizen.data.byok

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's Congress.gov API key, encrypted at rest via
 * [ByokCipher] and stored as base64 in the app's DataStore. A
 * credential, not a preference — hence the cipher; DataStore is just
 * the durable bytes container.
 *
 * Decode failures (e.g. the Keystore key was lost to a backup/restore
 * across devices) surface as `null` — the user re-enters the key.
 */
@Singleton
class ByokKeyStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: ByokCipher,
) {
    /** Null when unset or undecodable. */
    val congressApiKey: Flow<String?> = dataStore.data.map { prefs ->
        prefs[CONGRESS_KEY]?.let { decode(it) }
    }

    suspend fun currentCongressApiKey(): String? = congressApiKey.firstOrNull()

    /** Pass null to clear the stored key. */
    suspend fun setCongressApiKey(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) {
                prefs.remove(CONGRESS_KEY)
            } else {
                prefs[CONGRESS_KEY] = encode(value)
            }
        }
    }

    private fun encode(value: String): String =
        Base64.encodeToString(cipher.encrypt(value.encodeToByteArray()), Base64.NO_WRAP)

    private fun decode(stored: String): String? = runCatching {
        cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP)).decodeToString()
    }.getOrNull()

    private companion object {
        val CONGRESS_KEY = stringPreferencesKey("byok_congress_api_key")
    }
}
