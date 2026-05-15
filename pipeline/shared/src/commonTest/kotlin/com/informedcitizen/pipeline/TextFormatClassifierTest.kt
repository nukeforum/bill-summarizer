package com.informedcitizen.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextFormatClassifierTest {
    @Test
    fun htm_extension_classifies_as_html() {
        assertEquals(
            "html",
            classifyTextFormatUrl("https://www.congress.gov/119/bills/s4465/BILLS-119s4465es.htm"),
        )
    }

    @Test
    fun html_extension_classifies_as_html() {
        assertEquals("html", classifyTextFormatUrl("https://example.com/x.html"))
    }

    @Test
    fun xml_extension_classifies_as_xml() {
        assertEquals(
            "xml",
            classifyTextFormatUrl("https://www.congress.gov/119/bills/s4465/BILLS-119s4465es.xml"),
        )
    }

    @Test
    fun pdf_extension_classifies_as_pdf() {
        assertEquals(
            "pdf",
            classifyTextFormatUrl("https://www.congress.gov/119/bills/s4465/BILLS-119s4465es.pdf"),
        )
    }

    @Test
    fun query_string_is_ignored_for_classification() {
        assertEquals("html", classifyTextFormatUrl("https://example.com/x.htm?source=feed"))
    }

    @Test
    fun no_known_extension_returns_null() {
        assertNull(classifyTextFormatUrl("https://example.com/bill"))
    }

    @Test
    fun mixed_case_extension_classifies_correctly() {
        assertEquals("html", classifyTextFormatUrl("https://example.com/x.HTM"))
        assertEquals("html", classifyTextFormatUrl("https://example.com/x.Html"))
        assertEquals("xml", classifyTextFormatUrl("https://example.com/x.XML"))
        assertEquals("pdf", classifyTextFormatUrl("https://example.com/x.PDF"))
    }

    @Test
    fun double_dotted_extension_still_picks_outermost() {
        // E.g. `BILLS-119s4465es.tar.htm` — endsWith(".htm") is enough.
        assertEquals("html", classifyTextFormatUrl("https://example.com/x.tar.htm"))
    }

    @Test
    fun url_with_trailing_question_mark_and_no_query() {
        assertEquals("html", classifyTextFormatUrl("https://example.com/x.htm?"))
    }

    @Test
    fun bare_path_without_scheme_still_classifies() {
        assertEquals("xml", classifyTextFormatUrl("BILLS-119s4465es.xml"))
    }

    @Test
    fun empty_string_returns_null() {
        assertNull(classifyTextFormatUrl(""))
    }

    @Test
    fun unknown_extension_returns_null() {
        assertNull(classifyTextFormatUrl("https://example.com/x.docx"))
        assertNull(classifyTextFormatUrl("https://example.com/x.txt"))
    }
}
