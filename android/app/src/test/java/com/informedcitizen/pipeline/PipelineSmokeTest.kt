package com.informedcitizen.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sanity check that the shared KMP pipeline module is resolved and callable
 * from Android via the composite build wired in `android/settings.gradle.kts`.
 * Calls one real ported function; the module's own kotlin-test suite covers
 * behaviour exhaustively.
 */
class PipelineSmokeTest {
    @Test
    fun shared_pipeline_module_is_consumable_from_android() {
        assertEquals(
            "Rep. Smith, Adrian",
            cleanSponsorName("Rep. Smith, Adrian [R-NE-3]"),
        )
    }
}
