package com.informedcitizen.pipeline.http

import okhttp3.Headers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pins [CongressClient.normalizeApiKey] against the validator that
 * actually writes the request on JVM/Android. `MockEngine` never reaches
 * it, so the commonTest suite alone cannot see this boundary.
 *
 * OkHttp is the real transport here (`jvmMain` installs
 * `ktor-client-okhttp`), and `Headers.Builder.add` runs the same
 * `checkValue` the engine runs — no socket, no network. Two properties
 * must hold, and the first is the security one: if we ever accept a key
 * OkHttp rejects, its `IllegalArgumentException` appends the whole value
 * (only Authorization / Cookie / Proxy-Authorization / Set-Cookie are
 * exempt, and `X-Api-Key` is not one of them) and the CLI prints it
 * straight to stderr and the CI log.
 */
class CongressClientOkHttpBoundaryTest {

    private fun okHttpAccepts(key: String): Boolean =
        try {
            Headers.Builder().add(CongressClient.API_KEY_HEADER, key)
            true
        } catch (rejected: IllegalArgumentException) {
            false
        }

    @Test
    fun okhttp_accepts_every_key_the_client_lets_through() {
        for (code in 0..0x2FFF) {
            val candidate = "KEYSTART${code.toChar()}KEYEND"
            val normalized = try {
                CongressClient.normalizeApiKey(candidate)
            } catch (rejected: IllegalArgumentException) {
                continue
            }
            assertTrue(
                okHttpAccepts(normalized),
                "normalizeApiKey accepted a key OkHttp rejects (char U+${
                    code.toString(16).uppercase().padStart(4, '0')
                })",
            )
        }
    }

    @Test
    fun okhttp_would_quote_the_whole_key_back_if_it_ever_saw_an_unsendable_one() {
        val key = "NOT_A_REAL\u00A0KEY_ABC123"
        assertFalse(okHttpAccepts(key), "expected OkHttp to reject a non-ASCII header value")

        val leak = try {
            Headers.Builder().add(CongressClient.API_KEY_HEADER, key)
            fail("expected OkHttp to reject a non-ASCII header value")
        } catch (rejected: IllegalArgumentException) {
            rejected.message ?: ""
        }
        assertTrue(key in leak, "OkHttp's rejection no longer quotes the value: $leak")

        val exc = try {
            CongressClient.normalizeApiKey(key)
            fail("normalizeApiKey must reject what OkHttp rejects")
        } catch (rejected: IllegalArgumentException) {
            rejected
        }
        assertEquals(CongressClient.UNSENDABLE_KEY_MESSAGE, exc.message)
        assertFalse("NOT_A_REAL" in (exc.message ?: ""))
        assertFalse("KEY_ABC123" in (exc.message ?: ""))
    }
}
