package com.informedcitizen.data.byok

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun validatorRespondingWith(
    status: HttpStatusCode,
    body: String = "{}",
): ByokKeyValidator = ByokKeyValidator(httpClientFactory = {
    HttpClient(MockEngine) {
        engine {
            addHandler {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
    }
})

class ByokKeyValidatorTest {

    @Test
    fun `200 means valid`() = runTest {
        val result = validatorRespondingWith(HttpStatusCode.OK).validateCongressKey("k")
        assertEquals(KeyValidationResult.Valid, result)
    }

    @Test
    fun `403 means the key was rejected`() = runTest {
        val result = validatorRespondingWith(HttpStatusCode.Forbidden).validateCongressKey("k")
        assertEquals(KeyValidationResult.Invalid(403), result)
    }

    @Test
    fun `5xx means unreachable, not invalid`() = runTest {
        val result = validatorRespondingWith(HttpStatusCode.InternalServerError)
            .validateCongressKey("k")
        assertTrue(result is KeyValidationResult.Unreachable)
        assertEquals(KeyValidationResult.Unreachable(500), result)
    }

    @Test
    fun `connection failure means unreachable`() = runTest {
        val validator = ByokKeyValidator(httpClientFactory = {
            HttpClient(MockEngine) {
                engine { addHandler { throw java.io.IOException("no network") } }
            }
        })
        val result = validator.validateCongressKey("k")
        assertTrue(result is KeyValidationResult.Unreachable)
        assertEquals(KeyValidationResult.Unreachable(), result)
    }

    @Test
    fun `a key carrying a line break is malformed and never sent`() = runTest {
        var requested = false
        val validator = ByokKeyValidator(httpClientFactory = {
            HttpClient(MockEngine) {
                engine {
                    addHandler {
                        requested = true
                        respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            }
        })

        val result = validator.validateCongressKey("abc\ndef")

        assertEquals(KeyValidationResult.Malformed, result)
        assertFalse(requested)
    }

    /**
     * Cancelling the save (the user leaves the screen mid-check) must tear
     * the coroutine down, not resolve to an outcome. Reporting
     * [KeyValidationResult.Unreachable] here would push a state update from
     * a dead screen and tell the user the check failed when they cancelled
     * it themselves.
     */
    @Test
    fun `cancelling the check propagates instead of reporting unreachable`() = runTest {
        val inFlight = CompletableDeferred<Unit>()
        val validator = ByokKeyValidator(httpClientFactory = {
            HttpClient(MockEngine) {
                engine {
                    addHandler {
                        inFlight.complete(Unit)
                        awaitCancellation()
                    }
                }
            }
        })

        var outcome: KeyValidationResult? = null
        val job = launch { outcome = validator.validateCongressKey("k") }
        inFlight.await()
        job.cancelAndJoin()

        assertNull(outcome)
        assertTrue(job.isCancelled)
    }
}
