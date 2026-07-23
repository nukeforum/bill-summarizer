package com.informedcitizen.ui.reps

import com.informedcitizen.pipeline.model.MemberVoteRow
import com.informedcitizen.pipeline.model.VotePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberVoteRowFormatTest {

    private fun row(
        billId: String? = "hr7008-119",
        type: String? = "hr",
        number: String? = "7008",
        question: String = "On Passage",
        result: String = "Passed",
        position: VotePosition = VotePosition.YEA,
        shortTitle: String? = "Sample Act",
        date: String = "2026-07-22",
    ) = MemberVoteRow(
        voteId = "house-119-2-1",
        congress = 119,
        date = date,
        question = question,
        result = result,
        position = position,
        billId = billId,
        type = type,
        number = number,
        shortTitle = shortTitle,
    )

    @Test
    fun `bill label uppercases type and number`() {
        assertEquals("HR 7008", voteBillLabel(row()))
    }

    @Test
    fun `bill label falls back to question when not tied to a bill`() {
        assertEquals(
            "On Motion to Adjourn",
            voteBillLabel(row(billId = null, type = null, number = null, question = "On Motion to Adjourn")),
        )
    }

    @Test
    fun `date formats to a readable form`() {
        assertEquals("Jul 22, 2026", formatVoteDate("2026-07-22"))
    }

    @Test
    fun `unparseable date passes through unchanged`() {
        assertEquals("", formatVoteDate(""))
        assertEquals("sometime", formatVoteDate("sometime"))
    }

    @Test
    fun `position labels are human readable`() {
        assertEquals("Yea", positionLabel(VotePosition.YEA))
        assertEquals("Nay", positionLabel(VotePosition.NAY))
        assertEquals("Present", positionLabel(VotePosition.PRESENT))
        assertEquals("Not voting", positionLabel(VotePosition.NOT_VOTING))
    }

    @Test
    fun `screen reader description names bill title result and position`() {
        val description = voteRowDescription(row(position = VotePosition.NAY))
        assertTrue(description, description.contains("HR 7008"))
        assertTrue(description, description.contains("Sample Act"))
        assertTrue(description, description.contains("Jul 22, 2026"))
        assertTrue(description, description.contains("Result: Passed"))
        assertTrue(description, description.contains("Voted nay"))
    }
}
