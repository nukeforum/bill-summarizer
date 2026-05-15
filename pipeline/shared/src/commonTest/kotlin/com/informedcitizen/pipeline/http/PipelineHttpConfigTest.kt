package com.informedcitizen.pipeline.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PipelineHttpConfigTest {
    @Test
    fun defaults_match_python_pipeline_constants() {
        val config = PipelineHttpConfig()
        assertEquals(
            "bill-summarizer-pipeline/1.0 (+https://github.com/nukeforum/bill-summarizer)",
            config.userAgent,
        )
        assertEquals(30_000L, config.requestTimeoutMillis)
        assertEquals(3, config.retryCount)
        assertEquals(2_000L, config.retryBaseDelayMillis)
    }

    @Test
    fun retry_count_below_one_is_rejected() {
        assertFailsWith<IllegalArgumentException> { PipelineHttpConfig(retryCount = 0) }
        assertFailsWith<IllegalArgumentException> { PipelineHttpConfig(retryCount = -1) }
    }

    @Test
    fun non_positive_timeout_is_rejected() {
        assertFailsWith<IllegalArgumentException> { PipelineHttpConfig(requestTimeoutMillis = 0) }
        assertFailsWith<IllegalArgumentException> { PipelineHttpConfig(requestTimeoutMillis = -1) }
    }

    @Test
    fun negative_retry_delay_is_rejected() {
        assertFailsWith<IllegalArgumentException> { PipelineHttpConfig(retryBaseDelayMillis = -1) }
    }

    @Test
    fun zero_retry_delay_is_allowed_for_tests() {
        // Tests pass `retryBaseDelayMillis = 0` to avoid sleeping seconds
        // across retry attempts. Must remain valid.
        val config = PipelineHttpConfig(retryBaseDelayMillis = 0)
        assertEquals(0L, config.retryBaseDelayMillis)
    }
}
