package com.informedcitizen.data.byok

import com.informedcitizen.testutil.InMemoryPreferencesDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class ByokFetchTrackerTest {

    @Test
    fun `artifact with no recorded success is due`() = runTest {
        val tracker = ByokFetchTracker(InMemoryPreferencesDataStore())
        assertTrue(tracker.isDue(ByokArtifact.BILLS, nowMillis = 1_000L))
    }

    @Test
    fun `bills due again after a day, not before`() = runTest {
        val tracker = ByokFetchTracker(InMemoryPreferencesDataStore())
        val t0 = 1_000_000L
        tracker.recordSuccess(ByokArtifact.BILLS, t0)

        assertFalse(tracker.isDue(ByokArtifact.BILLS, t0 + 12.hours.inWholeMilliseconds))
        assertTrue(tracker.isDue(ByokArtifact.BILLS, t0 + 1.days.inWholeMilliseconds))
    }

    @Test
    fun `members and calendar are weekly`() = runTest {
        val tracker = ByokFetchTracker(InMemoryPreferencesDataStore())
        val t0 = 1_000_000L
        tracker.recordSuccess(ByokArtifact.MEMBERS, t0)
        tracker.recordSuccess(ByokArtifact.CALENDAR, t0)

        val sixDays = t0 + 6.days.inWholeMilliseconds
        val sevenDays = t0 + 7.days.inWholeMilliseconds
        assertFalse(tracker.isDue(ByokArtifact.MEMBERS, sixDays))
        assertFalse(tracker.isDue(ByokArtifact.CALENDAR, sixDays))
        assertTrue(tracker.isDue(ByokArtifact.MEMBERS, sevenDays))
        assertTrue(tracker.isDue(ByokArtifact.CALENDAR, sevenDays))
    }

    @Test
    fun `successes are tracked per artifact`() = runTest {
        val tracker = ByokFetchTracker(InMemoryPreferencesDataStore())
        tracker.recordSuccess(ByokArtifact.BILLS, 1_000_000L)
        assertTrue(tracker.isDue(ByokArtifact.MEMBERS, 1_000_001L))
    }
}
