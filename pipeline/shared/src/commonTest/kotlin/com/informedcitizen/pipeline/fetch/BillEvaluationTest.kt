package com.informedcitizen.pipeline.fetch

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val CUTOFF = Instant.parse("2026-03-15T00:00:00Z")

private fun parse(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

class BillEvaluationTest {
    @Test fun missing_type_is_rejected() {
        val summary = parse("""{"number": "123", "latestAction": {"text": "Became Public Law No: 119-1.", "actionDate": "2026-04-01"}}""")
        val result = evaluateBill(summary, CUTOFF)
        assertIs<BillEvaluationResult.Rejected>(result)
        assertEquals(RejectionReasons.MISSING_TYPE_OR_NUMBER, result.reason)
    }

    @Test fun missing_number_is_rejected() {
        val summary = parse("""{"type": "hr", "latestAction": {"text": "Became Public Law", "actionDate": "2026-04-01"}}""")
        val result = evaluateBill(summary, CUTOFF)
        assertIs<BillEvaluationResult.Rejected>(result)
        assertEquals(RejectionReasons.MISSING_TYPE_OR_NUMBER, result.reason)
    }

    @Test fun action_matching_no_outcome_rule_is_rejected_with_no_outcome_match() {
        val summary = parse("""{"type": "hr", "number": "1", "latestAction": {"text": "Referred to the Subcommittee on X.", "actionDate": "2026-04-01"}}""")
        val result = evaluateBill(summary, CUTOFF)
        assertIs<BillEvaluationResult.Rejected>(result)
        assertEquals(RejectionReasons.NO_OUTCOME_MATCH, result.reason)
    }

    @Test fun unparseable_action_date_is_rejected() {
        val summary = parse("""{"type": "hr", "number": "1", "latestAction": {"text": "Became Public Law", "actionDate": "not a date"}}""")
        val result = evaluateBill(summary, CUTOFF)
        assertIs<BillEvaluationResult.Rejected>(result)
        assertEquals(RejectionReasons.UNPARSEABLE_ACTION_DATE, result.reason)
    }

    @Test fun action_before_cutoff_is_rejected_as_too_old() {
        val summary = parse("""{"type": "hr", "number": "1", "latestAction": {"text": "Became Public Law", "actionDate": "2026-01-01"}}""")
        val result = evaluateBill(summary, CUTOFF)
        assertIs<BillEvaluationResult.Rejected>(result)
        assertEquals(RejectionReasons.ACTION_TOO_OLD, result.reason)
    }

    @Test fun action_after_cutoff_with_matching_outcome_is_kept() {
        val summary = parse("""{"type": "hr", "number": "1", "latestAction": {"text": "Became Public Law No: 119-1.", "actionDate": "2026-04-01"}}""")
        val result = evaluateBill(summary, CUTOFF)
        assertIs<BillEvaluationResult.Kept>(result)
        assertEquals("enacted", result.outcome)
    }

    @Test fun fallback_to_date_when_actionDate_missing() {
        val summary = parse("""{"type": "hr", "number": "1", "latestAction": {"text": "Passed House", "date": "2026-04-01"}}""")
        val result = evaluateBill(summary, CUTOFF)
        assertIs<BillEvaluationResult.Kept>(result)
        assertEquals("passed_house", result.outcome)
    }

    @Test fun numeric_bill_number_is_accepted_via_permissive_string_field() {
        // Congress.gov occasionally returns `number` as a JSON number.
        val summary = parse("""{"type": "hr", "number": 1234, "latestAction": {"text": "Became Public Law", "actionDate": "2026-04-01"}}""")
        val result = evaluateBill(summary, CUTOFF)
        assertIs<BillEvaluationResult.Kept>(result)
    }

    @Test fun bill_type_is_lowercased() {
        val summary = parse("""{"type": "HR", "number": "1", "latestAction": {"text": "Became Public Law", "actionDate": "2026-04-01"}}""")
        val result = evaluateBill(summary, CUTOFF)
        assertIs<BillEvaluationResult.Kept>(result)
    }
}
