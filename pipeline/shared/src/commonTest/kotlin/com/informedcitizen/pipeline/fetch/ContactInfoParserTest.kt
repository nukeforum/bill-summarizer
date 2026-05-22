package com.informedcitizen.pipeline.fetch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactInfoParserTest {
    @Test fun reads_contact_form_from_current_term_only() {
        // contact_form on an older term must NOT bleed forward — 2026-05-13
        // investigation found 61% of stale forms 404. Current-term-only is
        // the load-bearing rule here.
        val text = """
            [{
              "id": {"bioguide": "A001"},
              "terms": [
                {"contact_form": "https://old-term.example/contact"},
                {"url": "https://current.example"}
              ]
            }]
        """.trimIndent()
        val info = parseContactInfoJson(text)
        val a = info["A001"]
        assertNull(a?.contactForm)
        assertEquals("https://current.example", a?.website)
    }

    @Test fun walks_url_reverse_until_non_empty() {
        // url is allowed to fall back to older terms (homepages stay live
        // across rotations). Reverse walk picks the most recent non-empty.
        val text = """
            [{
              "id": {"bioguide": "B001"},
              "terms": [
                {"url": "https://older.example"},
                {"url": "https://middle.example"},
                {}
              ]
            }]
        """.trimIndent()
        val info = parseContactInfoJson(text)
        assertEquals("https://middle.example", info["B001"]?.website)
    }

    @Test fun both_fields_null_when_no_term_carries_them() {
        val text = """[{"id":{"bioguide":"C001"},"terms":[{}]}]"""
        val info = parseContactInfoJson(text)
        val c = info["C001"]
        assertNull(c?.contactForm)
        assertNull(c?.website)
    }

    @Test fun skips_entries_with_missing_bioguide() {
        val text = """[
            {"id":{},"terms":[{"url":"https://x"}]},
            {"id":{"bioguide":""},"terms":[{"url":"https://y"}]},
            {"id":{"bioguide":"D001"},"terms":[{"url":"https://z"}]}
        ]"""
        val info = parseContactInfoJson(text)
        assertEquals(setOf("D001"), info.keys)
    }

    @Test fun reads_both_fields_when_present_on_current_term() {
        val text = """
            [{
              "id": {"bioguide": "E001"},
              "terms": [
                {"contact_form": "https://e.example/contact", "url": "https://e.example"}
              ]
            }]
        """.trimIndent()
        val info = parseContactInfoJson(text)
        assertEquals("https://e.example/contact", info["E001"]?.contactForm)
        assertEquals("https://e.example", info["E001"]?.website)
    }

    @Test fun returns_empty_when_root_is_not_array() {
        assertEquals(emptyMap(), parseContactInfoJson("""{"not":"array"}"""))
    }

    @Test fun ignores_unknown_extra_term_fields() {
        // Upstream occasionally adds fields (e.g. rss_url, address). The
        // parser must not fail on unknown keys.
        val text = """
            [{
              "id": {"bioguide": "F001"},
              "terms": [{
                "url": "https://f.example",
                "contact_form": "https://f.example/contact",
                "rss_url": "https://f.example/rss",
                "office": "100 Russell"
              }]
            }]
        """.trimIndent()
        val info = parseContactInfoJson(text)
        assertEquals("https://f.example/contact", info["F001"]?.contactForm)
    }
}
