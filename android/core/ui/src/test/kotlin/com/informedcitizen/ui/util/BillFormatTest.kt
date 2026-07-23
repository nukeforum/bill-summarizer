package com.informedcitizen.ui.util

import com.informedcitizen.pipeline.model.LifecycleStatus
import com.informedcitizen.pipeline.model.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class BillFormatTest {

    @Test
    fun `outcome displayName maps every terminal outcome`() {
        assertEquals("Passed House", Outcome.PASSED_HOUSE.displayName())
        assertEquals("Passed Senate", Outcome.PASSED_SENATE.displayName())
        assertEquals("Enacted", Outcome.ENACTED.displayName())
        assertEquals("Vetoed", Outcome.VETOED.displayName())
        assertEquals("Failed", Outcome.FAILED.displayName())
        assertEquals("Unknown", Outcome.UNKNOWN.displayName())
    }

    @Test
    fun `lifecycle status displayName maps every pre-floor status`() {
        assertEquals("Introduced", LifecycleStatus.INTRODUCED.displayName())
        assertEquals("In committee", LifecycleStatus.IN_COMMITTEE.displayName())
        assertEquals("Reported", LifecycleStatus.REPORTED.displayName())
        assertEquals("Unknown", LifecycleStatus.UNKNOWN.displayName())
    }
}
