package com.informedcitizen.pipeline.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Shared Ktor configuration applied to whichever platform engine is
 * selected (`OkHttp` on JVM/Android via the `jvmMain` actual,
 * `Darwin` on iOS via the `iosMain` actual). Keeps engine selection
 * isolated to one place per platform without duplicating
 * UserAgent / ContentNegotiation / timeout / retry settings.
 */
internal fun HttpClientConfig<*>.configurePipeline(config: PipelineHttpConfig) {
    install(UserAgent) { agent = config.userAgent }

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
        )
    }

    install(HttpTimeout) {
        requestTimeoutMillis = config.requestTimeoutMillis
    }

    install(HttpRequestRetry) {
        // Python's `RETRY_COUNT = 3` is the total attempt count, so
        // `maxRetries` (additional attempts beyond the initial) is one
        // less. retryCount=3 → 1 initial + 2 retries.
        val maxRetries = config.retryCount - 1
        retryOnExceptionIf(maxRetries = maxRetries) { _, cause ->
            cause !is CancellationException
        }
        // Retry transient 5xx server errors; do NOT retry on 4xx
        // (including 404, which CongressClient maps to empty payload).
        retryOnServerErrors(maxRetries = maxRetries)
        // Linear backoff matching Python's `time.sleep(BACKOFF * attempt)`.
        delayMillis { attempt -> config.retryBaseDelayMillis * attempt }
    }
}

/**
 * Returns an [HttpClient] backed by the platform's default engine
 * (`OkHttp` on JVM/Android, `Darwin` on iOS). For tests, construct
 * `HttpClient(MockEngine) { configurePipelineForTest(config) }`
 * directly with [configurePipelineForTest] (which wraps
 * [configurePipeline]).
 */
expect fun createPipelineHttpClient(
    config: PipelineHttpConfig = PipelineHttpConfig(),
): HttpClient

/**
 * Test-only accessor for the shared configuration so tests using
 * `MockEngine` can apply the same UserAgent / retry / timeout settings
 * without duplicating the production config.
 */
fun HttpClientConfig<*>.configurePipelineForTest(config: PipelineHttpConfig = PipelineHttpConfig()) {
    configurePipeline(config)
}
