package com.informedcitizen.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ErrorCollectorTest {
    @Test
    fun empty_collector_is_empty_and_produces_no_summary_lines() {
        val ec = ErrorCollector()
        assertEquals(0, ec.size)
        assertFalse(ec.hasErrors)
        assertEquals(emptyList(), ec.summaryLines())
    }

    @Test
    fun record_captures_kind_identifier_error_class_and_message() {
        val ec = ErrorCollector()
        ec.record(
            kind = "build_bill_record",
            identifier = "hr1234",
            errorClass = "JSONDecodeError",
            message = "Expecting value",
        )
        assertEquals(1, ec.size)
        val rec = ec.records().single()
        assertEquals("build_bill_record", rec.kind)
        assertEquals("hr1234", rec.identifier)
        assertEquals("JSONDecodeError", rec.errorClass)
        assertEquals("Expecting value", rec.message)
        assertNull(rec.url)
        assertNull(rec.params)
    }

    @Test
    fun optional_url_and_params_are_preserved() {
        val ec = ErrorCollector()
        ec.record(
            kind = "hud_get",
            identifier = "CA",
            errorClass = "RuntimeError",
            message = "HUD API 503",
            url = "https://www.huduser.gov/hudapi/public/usps",
            params = mapOf("type" to "5", "query" to "CA"),
        )
        val rec = ec.records().single()
        assertEquals("https://www.huduser.gov/hudapi/public/usps", rec.url)
        assertEquals(mapOf("type" to "5", "query" to "CA"), rec.params)
    }

    @Test
    fun summary_groups_by_kind_and_error_class() {
        val ec = ErrorCollector().apply {
            record("hud_get", "CA", "RuntimeError", "503")
            record("hud_get", "TX", "RuntimeError", "504")
            record("member_detail", "A001", "RuntimeError", "missing field")
        }
        val blob = ec.summaryLines().joinToString("\n")
        assertTrue("hud_get" in blob, blob)
        assertTrue("member_detail" in blob, blob)
        assertTrue("RuntimeError × 2" in blob, blob)
        assertTrue("RuntimeError × 1" in blob, blob)
    }

    @Test
    fun summary_caps_examples_per_group_with_more_tail() {
        val ec = ErrorCollector()
        repeat(8) { i ->
            ec.record("build_bill_record", "hr$i", "ValueError", "bad bill $i")
        }
        val lines = ec.summaryLines(examplesPerClass = 5)
        val blob = lines.joinToString("\n")
        assertTrue("ValueError × 8" in blob, blob)
        val enumerated = lines.count { "hr" in it && ":" in it }
        assertEquals(5, enumerated, "expected 5 enumerated examples\n$blob")
        assertTrue("more" in blob.lowercase(), blob)
    }

    @Test
    fun summary_includes_url_and_params_when_present() {
        val ec = ErrorCollector()
        ec.record(
            kind = "hud_get",
            identifier = "CA",
            errorClass = "RuntimeError",
            message = "503",
            url = "https://www.huduser.gov/hudapi/public/usps",
            params = mapOf("type" to "5", "query" to "CA"),
        )
        val blob = ec.summaryLines().joinToString("\n")
        assertTrue("huduser.gov" in blob, blob)
        assertTrue("query" in blob, blob)
        assertTrue("CA" in blob, blob)
    }

    @Test
    fun summary_total_count_appears_in_header_line() {
        val ec = ErrorCollector().apply {
            record("a", "1", "ValueError", "x")
            record("b", "2", "RuntimeError", "y")
        }
        val header = ec.summaryLines().first()
        assertTrue("2" in header, header)
        assertTrue("error" in header.lowercase(), header)
    }

    @Test
    fun render_summary_returns_empty_when_no_errors() {
        val ec = ErrorCollector()
        assertEquals("", ec.renderSummary(label = "Phase 1"))
    }

    @Test
    fun render_summary_includes_label_and_records() {
        val ec = ErrorCollector().apply {
            record("build_bill_record", "hr1", "ValueError", "oops")
        }
        val rendered = ec.renderSummary(label = "Phase 1")
        assertTrue("Phase 1" in rendered, rendered)
        assertTrue("ValueError" in rendered, rendered)
        assertTrue("hr1" in rendered, rendered)
    }

    @Test
    fun duplicate_records_in_same_group_share_a_count() {
        val ec = ErrorCollector().apply {
            record("a", "1", "ValueError", "x")
            record("a", "1", "ValueError", "x")
            record("a", "1", "ValueError", "x")
        }
        val blob = ec.summaryLines().joinToString("\n")
        assertTrue("ValueError × 3" in blob, blob)
    }

    @Test
    fun single_record_with_examples_per_class_one_has_no_more_tail() {
        val ec = ErrorCollector().apply {
            record("a", "1", "ValueError", "x")
        }
        val lines = ec.summaryLines(examplesPerClass = 1)
        val blob = lines.joinToString("\n")
        assertEquals(3, lines.size, blob) // total + group line + 1 detail
        assertTrue("more" !in blob, blob)
    }

    @Test
    fun examples_per_class_zero_emits_no_detail_lines_but_keeps_count() {
        val ec = ErrorCollector()
        repeat(3) { ec.record("a", "id$it", "ValueError", "boom") }
        val blob = ec.summaryLines(examplesPerClass = 0).joinToString("\n")
        assertTrue("ValueError × 3" in blob, blob)
        assertTrue("… 3 more" in blob, blob)
        // No `id0`/`id1`/`id2` lines should appear in the body.
        assertFalse("id0" in blob, blob)
    }

    @Test
    fun records_preserve_insertion_order_in_records_list() {
        val ec = ErrorCollector().apply {
            record("a", "1", "ValueError", "x")
            record("b", "2", "RuntimeError", "y")
            record("c", "3", "TypeError", "z")
        }
        val ids = ec.records().map { it.identifier }
        assertEquals(listOf("1", "2", "3"), ids)
    }

    @Test
    fun special_characters_in_identifier_are_preserved() {
        val ec = ErrorCollector().apply {
            record("fetch", "hr1234/v2", "ValueError", "bad ref")
        }
        val blob = ec.summaryLines().joinToString("\n")
        assertTrue("hr1234/v2" in blob, blob)
    }

    @Test
    fun two_distinct_error_classes_in_same_kind_produce_two_groups() {
        val ec = ErrorCollector().apply {
            record("fetch", "1", "ValueError", "x")
            record("fetch", "2", "RuntimeError", "y")
        }
        val blob = ec.summaryLines().joinToString("\n")
        assertTrue("ValueError × 1" in blob, blob)
        assertTrue("RuntimeError × 1" in blob, blob)
    }

    @Test
    fun render_summary_with_no_label_omits_dashes_block() {
        val ec = ErrorCollector().apply {
            record("a", "1", "ValueError", "x")
        }
        val rendered = ec.renderSummary()
        assertFalse(rendered.startsWith("---"), rendered)
        assertTrue(rendered.startsWith("1 error"), rendered)
    }

    @Test
    fun assigning_to_records_does_not_mutate_collector_state() {
        // records() returns a snapshot — callers can sort/filter freely.
        val ec = ErrorCollector().apply {
            record("a", "1", "ValueError", "x")
        }
        val snapshot = ec.records().toMutableList()
        snapshot.clear()
        assertEquals(1, ec.size)
    }
}
