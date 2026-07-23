package com.informedcitizen.pipeline

import com.informedcitizen.pipeline.model.LifecycleStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * KMP parity shadow for Python `test_common_helpers`'s lifecycle/status tests
 * (backlog #39). Mirrors `classify_lifecycle_status` / `classify_bill_status`.
 */
class BillStatusTest {
    @Test
    fun classify_lifecycle_introduced() {
        assertEquals(LIFECYCLE_INTRODUCED, classifyLifecycleStatus("Introduced in House"))
    }

    @Test
    fun classify_lifecycle_in_committee_from_referral() {
        // The same string classifyOutcome returns null for is a real in_committee status.
        assertNull(classifyOutcome("Referred to the Subcommittee on Immigration."))
        assertEquals(
            LIFECYCLE_IN_COMMITTEE,
            classifyLifecycleStatus("Referred to the Subcommittee on Immigration."),
        )
    }

    @Test
    fun classify_lifecycle_reported_beats_in_committee() {
        // A reported action often still mentions the committee; reported must win.
        assertEquals(
            LIFECYCLE_REPORTED,
            classifyLifecycleStatus("Reported by the Committee on Finance. H. Rept. 119-42."),
        )
    }

    @Test
    fun classify_lifecycle_placed_on_calendar_is_reported() {
        assertEquals(
            LIFECYCLE_REPORTED,
            classifyLifecycleStatus("Placed on the Union Calendar, Calendar No. 88."),
        )
    }

    @Test
    fun classify_lifecycle_unknown_returns_none() {
        assertNull(classifyLifecycleStatus("Became Public Law No: 119-12."))
    }

    @Test
    fun classify_bill_status_outcome_wins_over_lifecycle() {
        // Enacted text mentions no committee, but a terminal outcome must be flagged isOutcome.
        val (status, isOutcome) = classifyBillStatus("Became Public Law No: 119-12.")
        assertEquals(OUTCOME_ENACTED, status)
        assertTrue(isOutcome)
    }

    @Test
    fun classify_bill_status_falls_back_to_lifecycle() {
        val (status, isOutcome) =
            classifyBillStatus("Referred to the House Committee on Ways and Means.")
        assertEquals(LIFECYCLE_IN_COMMITTEE, status)
        assertFalse(isOutcome)
    }

    @Test
    fun classify_bill_status_no_match() {
        val (status, isOutcome) = classifyBillStatus("Some unrecognized action text.")
        assertNull(status)
        assertFalse(isOutcome)
    }

    @Test
    fun lifecycle_wire_strings_map_to_typed_enum() {
        assertEquals(LifecycleStatus.INTRODUCED, lifecycleStatusFromWireString(LIFECYCLE_INTRODUCED))
        assertEquals(LifecycleStatus.IN_COMMITTEE, lifecycleStatusFromWireString(LIFECYCLE_IN_COMMITTEE))
        assertEquals(LifecycleStatus.REPORTED, lifecycleStatusFromWireString(LIFECYCLE_REPORTED))
    }

    @Test
    fun lifecycle_wire_mapping_returns_null_for_unknown() {
        // Terminal outcomes are not lifecycle statuses, and UNKNOWN is decode-side only.
        assertNull(lifecycleStatusFromWireString(OUTCOME_ENACTED))
        assertNull(lifecycleStatusFromWireString("unknown"))
        assertNull(lifecycleStatusFromWireString("nonsense"))
    }

    @Test
    fun lifecycle_typed_enum_maps_to_wire_string() {
        assertEquals(LIFECYCLE_INTRODUCED, lifecycleStatusToWireString(LifecycleStatus.INTRODUCED))
        assertEquals(LIFECYCLE_IN_COMMITTEE, lifecycleStatusToWireString(LifecycleStatus.IN_COMMITTEE))
        assertEquals(LIFECYCLE_REPORTED, lifecycleStatusToWireString(LifecycleStatus.REPORTED))
        assertEquals("unknown", lifecycleStatusToWireString(LifecycleStatus.UNKNOWN))
    }

    @Test
    fun lifecycle_wire_string_round_trips_the_pipeline_values() {
        // The three pipeline-emitted statuses survive a to->from round-trip, so
        // the app's extracted `status` column (backlog #41) matches the value the
        // filter query compares against.
        for (status in listOf(LifecycleStatus.INTRODUCED, LifecycleStatus.IN_COMMITTEE, LifecycleStatus.REPORTED)) {
            assertEquals(status, lifecycleStatusFromWireString(lifecycleStatusToWireString(status)))
        }
    }
}
