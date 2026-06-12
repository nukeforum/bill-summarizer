package com.informedcitizen.data.byok

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    }
}
