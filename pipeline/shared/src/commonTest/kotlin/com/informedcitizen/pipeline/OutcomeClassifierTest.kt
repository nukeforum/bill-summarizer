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

    @Test
    fun became_law_classifies_as_enacted() {
        assertEquals(OUTCOME_ENACTED, classifyOutcome("Became Law without President's signature."))
    }

    @Test
    fun motion_to_table_classifies_as_failed() {
        assertEquals(OUTCOME_FAILED, classifyOutcome("Motion to table agreed to in Senate."))
    }

    @Test
    fun rejected_classifies_as_failed() {
        assertEquals(OUTCOME_FAILED, classifyOutcome("Rejected by recorded vote."))
    }

    @Test
    fun passed_agreed_to_in_house_classifies_as_passed_house() {
        assertEquals(
            OUTCOME_PASSED_HOUSE,
            classifyOutcome("Passed/agreed to in House: passed under suspension of the rules"),
        )
    }

    @Test
    fun agreed_to_in_senate_classifies_as_passed_senate() {
        assertEquals(
            OUTCOME_PASSED_SENATE,
            classifyOutcome("Agreed to in Senate without amendment by Voice Vote."),
        )
    }

    @Test
    fun uppercase_text_still_classifies() {
        // Real action text always uses title case, but the function
        // lowercases before matching — guard the contract.
        assertEquals(OUTCOME_VETOED, classifyOutcome("VETOED BY PRESIDENT."))
    }

    @Test
    fun empty_string_returns_null() {
        assertNull(classifyOutcome(""))
    }

    @Test
    fun whitespace_only_returns_null() {
        assertNull(classifyOutcome("   "))
    }

    @Test
    fun rule_order_pins_vetoed_above_passage() {
        // A bill the President vetoed has previously passed both chambers.
        // The rule-order contract: vetoed wins over passed_*.
        assertEquals(
            OUTCOME_VETOED,
            classifyOutcome("Passed House and Senate. Vetoed by President."),
        )
    }
}
