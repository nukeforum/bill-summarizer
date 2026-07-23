package com.informedcitizen.ui.billdetail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for [voteResultPassed], the affirmative-result detector behind
 * the bill-detail vote chip. Cases are the real Congress.gov `result`
 * strings observed across the live manifests — the earlier `startsWith`
 * check styled all but a bare "Passed" as neutral.
 */
class VoteResultChipTest {

    @Test
    fun `bare passed and agreed forms are affirmative`() {
        assertTrue(voteResultPassed("Passed"))
        assertTrue(voteResultPassed("Agreed to"))
    }

    @Test
    fun `qualified passage phrasings are affirmative`() {
        // These are the common forms the old startsWith check missed.
        assertTrue(voteResultPassed("Bill Passed"))
        assertTrue(voteResultPassed("Joint Resolution Passed"))
        assertTrue(voteResultPassed("Motion Agreed to"))
        assertTrue(voteResultPassed("Resolution Agreed to"))
        assertTrue(voteResultPassed("Concurrent Resolution Agreed to"))
        assertTrue(voteResultPassed("Motion to Proceed Agreed to"))
    }

    @Test
    fun `case is ignored`() {
        assertTrue(voteResultPassed("BILL PASSED"))
        assertTrue(voteResultPassed("motion agreed to"))
    }

    @Test
    fun `failure and rejection results are not affirmative`() {
        assertFalse(voteResultPassed("Failed"))
        assertFalse(voteResultPassed("Bill Defeated"))
        assertFalse(voteResultPassed("Joint Resolution Defeated"))
        assertFalse(voteResultPassed("Motion Rejected"))
        assertFalse(voteResultPassed("Motion to Proceed Rejected"))
        assertFalse(voteResultPassed("Cloture on the Motion to Proceed Rejected"))
        assertFalse(voteResultPassed("Motion to Discharge Rejected"))
    }

    @Test
    fun `blank result is not affirmative`() {
        assertFalse(voteResultPassed(""))
    }
}
