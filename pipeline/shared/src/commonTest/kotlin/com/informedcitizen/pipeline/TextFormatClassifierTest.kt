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
}
