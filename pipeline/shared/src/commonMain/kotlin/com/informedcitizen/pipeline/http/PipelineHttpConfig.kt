package com.informedcitizen.pipeline.http

/**
 * Configuration for the pipeline's Ktor [io.ktor.client.HttpClient]. The
 * defaults reproduce the Python `_common.py` constants (`USER_AGENT`,
 * `REQUEST_TIMEOUT`, `RETRY_COUNT`, `RETRY_BACKOFF_SECONDS`) so the
 * Kotlin and Python clients behave identically against Congress.gov and
 * HUD during the parallel-run period before CI cuts over.
 */
data class PipelineHttpConfig(
    val userAgent: String = DEFAULT_USER_AGENT,
    val requestTimeoutMillis: Long = 30_000L,
    val retryCount: Int = 3,
    val retryBaseDelayMillis: Long = 2_000L,
) {
    init {
        require(retryCount >= 1) { "retryCount must be at least 1" }
        require(requestTimeoutMillis > 0) { "requestTimeoutMillis must be positive" }
        require(retryBaseDelayMillis >= 0) { "retryBaseDelayMillis must be non-negative" }
    }

    companion object {
        const val DEFAULT_USER_AGENT: String =
            "bill-summarizer-pipeline/1.0 (+https://github.com/nukeforum/bill-summarizer)"
    }
}
