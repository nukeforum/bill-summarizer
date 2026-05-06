package com.informedcitizen.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UpcomingBlocksTest {

    @Test
    fun `single contiguous block`() {
        val days = listOf("2026-05-12", "2026-05-13", "2026-05-14", "2026-05-15")
        val blocks = upcomingBlocks(days, today = LocalDate.of(2026, 5, 9))
        assertEquals(1, blocks.size)
        assertEquals(LocalDate.of(2026, 5, 12), blocks[0].start)
        assertEquals(LocalDate.of(2026, 5, 15), blocks[0].end)
        assertEquals(4, blocks[0].dayCount)
    }

    @Test
    fun `weekend gap stays in same block`() {
        val days = listOf("2026-05-15", "2026-05-18")
        val blocks = upcomingBlocks(days, today = LocalDate.of(2026, 5, 9))
        assertEquals(1, blocks.size)
        assertEquals(2, blocks[0].dayCount)
    }

    @Test
    fun `four-plus-day gap starts a new block`() {
        val days = listOf("2026-05-15", "2026-05-22")
        val blocks = upcomingBlocks(days, today = LocalDate.of(2026, 5, 9))
        assertEquals(2, blocks.size)
    }

    @Test
    fun `past days excluded`() {
        val days = listOf("2026-04-01", "2026-05-12")
        val blocks = upcomingBlocks(days, today = LocalDate.of(2026, 5, 9))
        assertEquals(1, blocks.size)
        assertEquals(LocalDate.of(2026, 5, 12), blocks[0].start)
    }

    @Test
    fun `limit caps result`() {
        val days = listOf("2026-05-12", "2026-05-20", "2026-05-28", "2026-06-05")
        val blocks = upcomingBlocks(days, today = LocalDate.of(2026, 5, 9), limit = 2)
        assertEquals(2, blocks.size)
    }

    @Test
    fun `range text for single day`() {
        val days = listOf("2026-05-12")
        val text = upcomingBlocks(days, today = LocalDate.of(2026, 5, 9)).single().rangeText
        assertEquals("May 12 (1 session day)", text)
    }
}
