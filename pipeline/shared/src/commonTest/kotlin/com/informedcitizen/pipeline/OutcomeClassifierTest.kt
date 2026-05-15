package com.informedcitizen.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutcomeClassifierTest {
    @Test
    fun became_public_law_classifies_as_enacted() {
        assertEquals(
            OUTCOME_ENACTED,
            classifyOutcome("Became Public Law No: 119-12."),
        )
    }

    @Test
    fun on_passage_passed_by_house_classifies_as_passed_house() {
        assertEquals(
            OUTCOME_PASSED_HOUSE,
            classifyOutcome("On passage Passed by the House by recorded vote: 217 - 215"),
        )
    }

    @Test
    fun vetoed_classifies_as_vetoed() {
        assertEquals(OUTCOME_VETOED, classifyOutcome("Vetoed by President."))
    }

    @Test
    fun failed_of_passage_classifies_as_failed() {
        assertEquals(OUTCOME_FAILED, classifyOutcome("Failed of passage in the Senate."))
    }

    @Test
    fun unrelated_action_returns_null() {
        assertNull(classifyOutcome("Referred to the Subcommittee on Immigration."))
    }

    @Test
    fun enacted_dominates_over_passage() {
        assertEquals(
            OUTCOME_ENACTED,
            classifyOutcome("Passed Senate. Became Public Law No: 119-12."),
        )
    }
}
