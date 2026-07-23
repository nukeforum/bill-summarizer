package com.informedcitizen.ui.billslist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DataFreshnessFormatterTest {

    private val base = Instant.parse("2026-07-22T12:00:00Z")

    @Test fun `parses ISO UTC manifest timestamp`() {
        assertEquals(
            Instant.parse("2026-07-22T07:55:09Z"),
            parseGeneratedAt("2026-07-22T07:55:09Z"),
        )
    }

    @Test fun `unparseable, blank, and null values yield null`() {
        assertNull(parseGeneratedAt("x"))
        assertNull(parseGeneratedAt("2026-07-22"))
        assertNull(parseGeneratedAt(""))
        assertNull(parseGeneratedAt("   "))
        assertNull(parseGeneratedAt(null))
    }

    @Test fun `sub-minute age reads as just now`() {
        assertEquals("Updated just now", formatDataFreshness(base.minusSeconds(30), base))
    }

    @Test fun `future timestamp from clock skew reads as just now`() {
        assertEquals("Updated just now", formatDataFreshness(base.plusSeconds(120), base))
    }

    @Test fun `minutes boundary singular and plural`() {
        assertEquals("Updated 1 minute ago", formatDataFreshness(base.minus(Duration.ofMinutes(1)), base))
        assertEquals("Updated 5 minutes ago", formatDataFreshness(base.minus(Duration.ofMinutes(5)), base))
        assertEquals("Updated 59 minutes ago", formatDataFreshness(base.minus(Duration.ofMinutes(59)), base))
    }

    @Test fun `hours boundary singular and plural`() {
        assertEquals("Updated 1 hour ago", formatDataFreshness(base.minus(Duration.ofHours(1)), base))
        assertEquals("Updated 23 hours ago", formatDataFreshness(base.minus(Duration.ofHours(23)), base))
    }

    @Test fun `days boundary singular and plural`() {
        assertEquals("Updated 1 day ago", formatDataFreshness(base.minus(Duration.ofDays(1)), base))
        assertEquals("Updated 5 days ago", formatDataFreshness(base.minus(Duration.ofDays(5)), base))
    }

    @Test fun `staleness flips at the two-day threshold`() {
        assertFalse(isDataStale(base.minus(Duration.ofDays(2)).plusSeconds(1), base))
        assertTrue(isDataStale(base.minus(Duration.ofDays(2)), base))
        assertTrue(isDataStale(base.minus(Duration.ofDays(3)), base))
    }

    @Test fun `relative label is independent of device time zone`() {
        // generated_at is UTC; the same wall-clock gap must render identically
        // regardless of the device's local offset. We express "now" as two
        // instants 6 hours apart that each sit 3 hours after the artifact in
        // their own frame — the relative output depends only on the gap.
        val generated = Instant.parse("2026-03-08T09:00:00Z") // US DST spring-forward day
        val now = generated.plus(Duration.ofHours(3))
        assertEquals("Updated 3 hours ago", formatDataFreshness(generated, now))
    }
}
