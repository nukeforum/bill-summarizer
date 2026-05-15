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
}
