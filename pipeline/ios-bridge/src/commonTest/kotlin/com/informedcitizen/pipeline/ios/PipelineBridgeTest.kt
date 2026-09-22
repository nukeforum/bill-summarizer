package com.informedcitizen.pipeline.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PipelineBridgeTest {
    private val bridge = PipelineBridge()

    @Test
    fun exposesStablePrimitiveContracts() {
        assertEquals(119, bridge.congressForYear(2026))
        assertEquals("passed_house", bridge.classifyOutcome("Passed House without objection."))
        assertEquals("in_committee", bridge.classifyLifecycleStatus("Referred to the Committee on Finance."))
        assertEquals("I", bridge.normalizePartyCode("Independent"))
        assertNull(bridge.classifyOutcome("Introduced in Senate"))
    }
}
