package com.informedcitizen.data.byok

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.ui.datasources.fetchFailureText
import com.informedcitizen.ui.datasources.redactionSecret
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * A key-shaped string that is not a key. Congress.gov keys are 40
 * alphanumeric characters; nothing here is or ever was live.
 */
private const val FAKE_KEY = "zzzTESTKEYzzzNOTAREALCREDENTIAL000000000"

/**
 * The exact shape Ktor 3 gives a request timeout — the message that
 * started this: it interpolates `request.url.buildString()`, so a key in
 * the query string rides along into whatever consumes the message.
 */
private fun ktorTimeoutLookalike(url: String): Throwable =
    IOException("Request timeout has expired [url=$url, request_timeout=30000 ms]")

/**
 * A [DataStore] whose backing file cannot be read — the shape a corrupt
 * preferences file takes: `data` throws rather than emitting.
 */
private class UnreadableDataStore : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw IOException("datastore file corrupt") }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw IOException("datastore file corrupt")
}

/** Never reached: the store fails before it has bytes to decrypt. */
private class UnusedCipher : ByokCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray = error("not reached")
    override fun decrypt(blob: ByteArray): ByteArray = error("not reached")
}

/** Every string that could reach Crashlytics from [throwable] and its causes. */
private fun messageChain(throwable: Throwable): String = buildString {
    var current: Throwable? = throwable
    var depth = 0
    while (current != null && depth < 16) {
        append(current.message).append('\n')
        val next = current.cause
        current = if (next === current) null else next
        depth++
    }
}

/**
 * Regression guard for the BYOK key leak: the user's Congress.gov key
 * must not appear in anything reported to Crashlytics or rendered on
 * screen. The primary fix is that the key never enters the URL at all
 * (see `CongressClientTest.get_never_puts_the_api_key_in_the_url`);
 * these cover the scrub that backs it up on all three egress paths.
 */
class ApiKeyLeakTest {

    // --- Path 1: Crashlytics (ByokFetchOrchestrator.runReported) ---

    @Test
    fun `crash report never carries the api key`() {
        val reporter = FakeCrashReporter()
        val leaky = ktorTimeoutLookalike(
            "https://api.congress.gov/v3/bill?limit=1&api_key=$FAKE_KEY",
        )

        reportRedactedNonFatal(
            crashReporter = reporter,
            throwable = leaky,
            apiKey = FAKE_KEY,
            nonFatalMessage = "byok fetch failed",
        )

        val recorded = reporter.recorded.single()
        val reported = messageChain(recorded.throwable)
        assertFalse("api key leaked to Crashlytics: $reported", reported.contains(FAKE_KEY))
        assertFalse(reported.contains("api_key=$FAKE_KEY"))
        assertEquals("byok fetch failed", recorded.message)
    }

    @Test
    fun `crash report keeps the key out of nested causes`() {
        val reporter = FakeCrashReporter()
        val leaky = IllegalStateException(
            "byok bills step failed",
            ktorTimeoutLookalike("https://api.congress.gov/v3/bill?api_key=$FAKE_KEY"),
        )

        reportRedactedNonFatal(reporter, leaky, FAKE_KEY, "byok fetch failed")

        val reported = messageChain(reporter.recorded.single().throwable)
        assertFalse("api key leaked via cause: $reported", reported.contains(FAKE_KEY))
        // The diagnostic value survives the scrub.
        assertTrue(reported, reported.contains("byok bills step failed"))
        assertTrue(reported, reported.contains("java.io.IOException"))
        assertTrue(reported, reported.contains("Request timeout has expired"))
    }

    @Test
    fun `an ordinary failure is reported unchanged`() {
        val reporter = FakeCrashReporter()
        val boring = IllegalStateException("members index missing after Phase 1 run")

        reportRedactedNonFatal(reporter, boring, FAKE_KEY, "byok fetch failed")

        // Same instance, so Crashlytics still groups by the real type.
        assertSame(boring, reporter.recorded.single().throwable)
    }

    @Test
    fun `an unrelated credential in the query string is scrubbed too`() {
        val reporter = FakeCrashReporter()
        val leaky = ktorTimeoutLookalike("https://api.congress.gov/v3/bill?api_key=someoneelseskey")

        // No stored key at all — the query-parameter backstop still fires.
        reportRedactedNonFatal(reporter, leaky, apiKey = null, nonFatalMessage = "byok fetch failed")

        val reported = messageChain(reporter.recorded.single().throwable)
        assertFalse(reported, reported.contains("someoneelseskey"))
        assertTrue(reported, reported.contains("request_timeout=30000 ms"))
    }

    // --- Path 2: the "Fetch now" result line (DataSourcesViewModel) ---

    @Test
    fun `fetch failure text never carries the api key`() {
        val leaky = ktorTimeoutLookalike(
            "https://api.congress.gov/v3/bill?limit=1&api_key=$FAKE_KEY",
        )

        val shown = fetchFailureText("Bills", leaky, FAKE_KEY)

        assertFalse("api key leaked to the screen: $shown", shown.contains(FAKE_KEY))
        assertTrue(shown, shown.startsWith("Bills failed: "))
        assertTrue(shown, shown.contains("Request timeout has expired"))
    }

    @Test
    fun `fetch failure text falls back to the exception type when there is no message`() {
        val shown = fetchFailureText("Votes", IOException(), apiKey = FAKE_KEY)
        assertEquals("Votes failed: IOException", shown)
    }

    @Test
    fun `a corrupt keystore degrades to a null secret instead of throwing`() = runTest {
        val keyStore = ByokKeyStore(UnreadableDataStore(), UnusedCipher())

        // Unguarded, this read escapes onFetchNow's coroutine and strands
        // `fetching = true`; guarded, it costs only scrub precision.
        assertNull(redactionSecret(keyStore))
    }

    @Test
    fun `a null secret still scrubs the query parameter on screen`() {
        val leaky = ktorTimeoutLookalike(
            "https://api.congress.gov/v3/bill?limit=1&api_key=$FAKE_KEY",
        )

        val shown = fetchFailureText("Bills", leaky, apiKey = null)

        assertFalse("api key leaked to the screen: $shown", shown.contains(FAKE_KEY))
        assertTrue(shown, shown.contains("api_key=***"))
    }

    // --- Path 3: the key-entry supporting text (ByokKeyValidator) ---

    @Test
    fun `validator sends the key as a header, never in the url`() = runTest {
        var capturedUrl: String? = null
        var capturedHeader: String? = null
        var capturedQueryKey: String? = null
        val validator = ByokKeyValidator(httpClientFactory = {
            HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        capturedUrl = request.url.toString()
                        capturedHeader = request.headers["X-Api-Key"]
                        capturedQueryKey = request.url.parameters["api_key"]
                        respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            }
        })

        validator.validateCongressKey(FAKE_KEY)

        val url = capturedUrl ?: error("no request captured")
        assertEquals(FAKE_KEY, capturedHeader)
        assertNull("api_key query parameter must not be emitted", capturedQueryKey)
        assertFalse("api key leaked into the URL: $url", url.contains(FAKE_KEY))
        assertTrue(url, url.contains("limit=1"))
    }

    @Test
    fun `validator unreachable message never carries the api key`() = runTest {
        val validator = ByokKeyValidator(httpClientFactory = {
            HttpClient(MockEngine) {
                engine {
                    addHandler {
                        throw ktorTimeoutLookalike(
                            "https://api.congress.gov/v3/bill?limit=1&api_key=$FAKE_KEY",
                        )
                    }
                }
            }
        })

        val result = validator.validateCongressKey(FAKE_KEY)

        val message = (result as KeyValidationResult.Unreachable).message
        assertFalse("api key leaked to the screen: $message", message.contains(FAKE_KEY))
        assertTrue(message, message.contains("Request timeout has expired"))
    }

    // --- The scrub itself ---

    @Test
    fun `a secret below the rebuild depth is still caught and dropped`() {
        val reporter = FakeCrashReporter()
        // Deeper than the rebuild cap, so detection must not share that cap.
        var chain: Throwable = ktorTimeoutLookalike(
            "https://api.congress.gov/v3/bill?api_key=$FAKE_KEY",
        )
        repeat(20) { level -> chain = IllegalStateException("wrapper $level", chain) }

        reportRedactedNonFatal(reporter, chain, FAKE_KEY, "byok fetch failed")

        val recorded = reporter.recorded.single().throwable
        assertNotSame("deep chain was uploaded unscanned", chain, recorded)
        val reported = messageChain(recorded)
        assertFalse("api key leaked from a deep cause: $reported", reported.contains(FAKE_KEY))
    }

    @Test
    fun `a cyclic cause chain terminates`() {
        val reporter = FakeCrashReporter()
        val inner = IllegalStateException("inner")
        val outer = IllegalStateException("outer: api_key=$FAKE_KEY", inner)
        inner.initCause(outer)

        reportRedactedNonFatal(reporter, outer, FAKE_KEY, "byok fetch failed")

        val reported = messageChain(reporter.recorded.single().throwable)
        assertFalse(reported, reported.contains(FAKE_KEY))
    }

    @Test
    fun `redactSecret replaces every occurrence`() {
        val text = "$FAKE_KEY then $FAKE_KEY again"
        assertEquals("*** then *** again", redactSecret(text, FAKE_KEY))
    }

    @Test
    fun `redactSecret leaves unrelated text alone`() {
        val text = "Congress.gov returned HTTP 503"
        assertEquals(text, redactSecret(text, FAKE_KEY))
    }

    @Test
    fun `redactSecret is a no-op on null text`() {
        assertNull(redactSecret(null, FAKE_KEY))
    }

    @Test
    fun `redactSecret tolerates a null or blank secret`() {
        assertEquals("nothing to hide", redactSecret("nothing to hide", null))
        assertEquals("nothing to hide", redactSecret("nothing to hide", "   "))
    }

    @Test
    fun `redactSecret keeps the rest of a ktor timeout message intact`() {
        val scrubbed = redactSecret(
            "Request timeout has expired [url=https://api.congress.gov/v3/bill?limit=1&api_key=$FAKE_KEY, request_timeout=30000 ms]",
            FAKE_KEY,
        )
        assertEquals(
            "Request timeout has expired [url=https://api.congress.gov/v3/bill?limit=1&api_key=***, request_timeout=30000 ms]",
            scrubbed,
        )
    }
}
