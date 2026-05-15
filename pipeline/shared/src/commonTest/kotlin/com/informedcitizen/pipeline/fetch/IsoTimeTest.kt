package com.informedcitizen.pipeline.fetch

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IsoTimeTest {
    @Test fun now_iso_produces_seconds_precision_with_Z() {
        // Match Python `_common.now_iso()` shape: `YYYY-MM-DDTHH:MM:SSZ`.
        val fixed = Instant.parse("2026-05-15T14:22:09.987654321Z")
        assertEquals("2026-05-15T14:22:09Z", nowIso(fixed))
    }

    @Test fun now_iso_already_seconds_precise_passes_through() {
        val fixed = Instant.parse("2026-05-15T00:00:00Z")
        assertEquals("2026-05-15T00:00:00Z", nowIso(fixed))
    }

    @Test fun parse_iso_instant_full_iso_with_Z() {
        val parsed = parseIsoInstant("2026-04-20T13:45:00Z")
        assertNotNull(parsed)
        assertEquals(Instant.parse("2026-04-20T13:45:00Z"), parsed)
    }

    @Test fun parse_iso_instant_bare_date_anchored_at_utc_midnight() {
        val parsed = parseIsoInstant("2026-04-20")
        assertNotNull(parsed)
        assertEquals(Instant.parse("2026-04-20T00:00:00Z"), parsed)
    }

    @Test fun parse_iso_instant_null_returns_null() {
        assertNull(parseIsoInstant(null))
    }

    @Test fun parse_iso_instant_empty_returns_null() {
        assertNull(parseIsoInstant(""))
    }

    @Test fun parse_iso_instant_garbage_returns_null() {
        assertNull(parseIsoInstant("not a date"))
    }
}
