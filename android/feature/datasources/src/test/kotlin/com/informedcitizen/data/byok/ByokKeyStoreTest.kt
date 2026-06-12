package com.informedcitizen.data.byok

import com.informedcitizen.testutil.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * XOR-with-constant stand-in for the Keystore cipher (which doesn't
 * exist under Robolectric). Enough to prove the store round-trips
 * through encrypt/decrypt rather than persisting plaintext.
 */
private class FakeCipher : ByokCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray =
        plaintext.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()

    override fun decrypt(blob: ByteArray): ByteArray =
        blob.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
}

private class BrokenCipher : ByokCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
    override fun decrypt(blob: ByteArray): ByteArray = error("keystore key lost")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ByokKeyStoreTest {

    @Test
    fun `set and read round-trips through the cipher`() = runTest {
        val store = ByokKeyStore(InMemoryPreferencesDataStore(), FakeCipher())
        store.setCongressApiKey("my-secret-key")
        assertEquals("my-secret-key", store.congressApiKey.first())
    }

    @Test
    fun `unset key reads as null`() = runTest {
        val store = ByokKeyStore(InMemoryPreferencesDataStore(), FakeCipher())
        assertNull(store.congressApiKey.first())
    }

    @Test
    fun `clearing removes the stored key`() = runTest {
        val store = ByokKeyStore(InMemoryPreferencesDataStore(), FakeCipher())
        store.setCongressApiKey("my-secret-key")
        store.setCongressApiKey(null)
        assertNull(store.congressApiKey.first())
    }

    @Test
    fun `undecodable stored value surfaces as null rather than crashing`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        ByokKeyStore(dataStore, FakeCipher()).setCongressApiKey("k")
        // Same prefs, but the Keystore key is gone (backup/restore to a
        // new device) — decryption fails, the user re-enters the key.
        val store = ByokKeyStore(dataStore, BrokenCipher())
        assertNull(store.congressApiKey.first())
    }
}
