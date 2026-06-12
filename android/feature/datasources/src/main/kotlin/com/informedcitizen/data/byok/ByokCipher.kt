package com.informedcitizen.data.byok

import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/**
 * Symmetric encryption for the user's API keys at rest. Abstracted so
 * unit tests can substitute a passthrough — the production impl is
 * backed by AndroidKeyStore, which doesn't exist under Robolectric.
 */
interface ByokCipher {
    fun encrypt(plaintext: ByteArray): ByteArray

    /** Inverse of [encrypt]. Throws on tamper or key loss. */
    fun decrypt(blob: ByteArray): ByteArray
}

/**
 * AES/GCM cipher whose key lives in AndroidKeyStore (never readable by
 * app code, survives app restarts, lost on uninstall — exactly the
 * lifecycle API credentials want).
 *
 * Hand-rolled rather than Jetpack `EncryptedSharedPreferences`: the
 * androidx.security:security-crypto artifact is deprecated upstream,
 * and this project builds with warnings-as-errors.
 *
 * Blob layout: `[1 byte iv length][iv][ciphertext + GCM tag]`.
 */
class KeystoreByokCipher @Inject constructor() : ByokCipher {

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.isNotEmpty()) { "empty blob" }
        val ivLength = blob[0].toInt()
        require(ivLength > 0 && 1 + ivLength < blob.size) { "malformed blob" }
        val iv = blob.copyOfRange(1, 1 + ivLength)
        val ciphertext = blob.copyOfRange(1 + ivLength, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE,
        )
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "byok_api_keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
