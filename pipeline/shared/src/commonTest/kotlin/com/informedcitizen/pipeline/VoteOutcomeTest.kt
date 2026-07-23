package com.informedcitizen.pipeline

import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.Chamber
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.Sponsor
import com.informedcitizen.pipeline.model.VoteRef
import com.informedcitizen.pipeline.model.VoteTotals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * KMP parity shadow for Python `_common.outcome_from_vote(s)` /
 * `reconcile_vote_outcomes` (backlog #30): a bill's Outcome is derived
 * from its actual passage roll calls, so the substring text classifier's
 * misreads (an amendment rejection read as a failed bill) get corrected.
 */
private fun voteRef(
    chamber: Chamber = Chamber.HOUSE,
    rollNumber: Int = 1,
    date: String = "2025-01-23",
    question: String = "On Passage",
    result: String = "Passed",
    billId: String = "hr1234-119",
): VoteRef = VoteRef(
    id = "${chamber.name.lowercase()}-119-1-$rollNumber",
    chamber = chamber,
    session = 1,
    rollNumber = rollNumber,
    date = date,
    question = question,
    result = result,
    billId = billId,
    totals = VoteTotals(yea = 217, nay = 215, present = 0, notVoting = 3),
    path = "votes/congress119/${chamber.name.lowercase()}-1-$rollNumber.json",
)

private fun bill(
    outcome: Outcome = Outcome.PASSED_HOUSE,
    votes: List<VoteRef> = emptyList(),
    id: String = "hr1234-119",
): Bill = Bill(
    id = id,
    congress = 119,
    type = "hr",
    number = "1234",
    title = "An Act",
    sponsor = Sponsor(name = "Test Sponsor", party = "D", state = "XX"),
    introducedDate = "2025-01-15",
    latestAction = Action(date = "2025-01-23", text = "Latest action."),
    outcome = outcome,
    congressGovUrl = "https://www.congress.gov/bill/119th-congress/house-bill/1234",
    votes = votes,
)

class VoteOutcomeTest {
    // ---------- outcomeFromVote ----------

    @Test fun house_passage_passed_is_passed_house() {
        assertEquals(Outcome.PASSED_HOUSE, outcomeFromVote(Chamber.HOUSE, "On Passage", "Passed"))
    }

    @Test fun senate_passage_passed_is_passed_senate() {
        assertEquals(
            Outcome.PASSED_SENATE,
            outcomeFromVote(Chamber.SENATE, "On Passage of the Bill", "Bill Passed"),
        )
    }

    @Test fun house_passage_failed_is_failed() {
        assertEquals(Outcome.FAILED, outcomeFromVote(Chamber.HOUSE, "On Passage", "Failed"))
    }

    @Test fun senate_passage_defeated_is_failed() {
        assertEquals(
            Outcome.FAILED,
            outcomeFromVote(Chamber.SENATE, "On Passage of the Bill", "Bill Defeated"),
        )
    }

    @Test fun rejected_amendment_is_not_an_outcome() {
        // The #30 headline: an amendment rejection is not a passage vote, so
        // it can never imply a failed bill even though its result is "Rejected".
        assertNull(outcomeFromVote(Chamber.HOUSE, "On Agreeing to the Amendment", "Rejected"))
    }

    @Test fun motion_to_table_is_not_an_outcome() {
        assertNull(outcomeFromVote(Chamber.SENATE, "On the Motion to Table", "Agreed to"))
    }

    @Test fun cloture_is_not_an_outcome() {
        assertNull(outcomeFromVote(Chamber.SENATE, "On Cloture on the Motion", "Agreed to"))
    }

    @Test fun suspension_passage_is_passed_house() {
        assertEquals(
            Outcome.PASSED_HOUSE,
            outcomeFromVote(Chamber.HOUSE, "On Motion to Suspend the Rules and Pass", "Passed"),
        )
    }

    @Test fun passage_with_unrecognized_result_is_null() {
        assertNull(outcomeFromVote(Chamber.HOUSE, "On Passage", "Something Weird"))
    }

    // ---------- outcomeFromVotes ----------

    @Test fun no_votes_is_null() {
        assertNull(outcomeFromVotes(emptyList()))
    }

    @Test fun only_amendment_votes_is_null() {
        assertNull(
            outcomeFromVotes(
                listOf(voteRef(question = "On Agreeing to the Amendment", result = "Rejected")),
            ),
        )
    }

    @Test fun latest_decisive_passage_vote_wins() {
        // An earlier chamber passage, then final passage in the other chamber:
        // the most recent decisive vote by date is the bill's outcome.
        val votes = listOf(
            voteRef(chamber = Chamber.HOUSE, date = "2025-01-10", result = "Passed"),
            voteRef(chamber = Chamber.SENATE, date = "2025-02-01", question = "On Passage of the Bill", result = "Bill Passed"),
        )
        assertEquals(Outcome.PASSED_SENATE, outcomeFromVotes(votes))
    }

    @Test fun amendment_votes_ignored_when_a_passage_vote_exists() {
        val votes = listOf(
            voteRef(rollNumber = 5, date = "2025-02-05", question = "On Agreeing to the Amendment", result = "Rejected"),
            voteRef(rollNumber = 3, date = "2025-01-30", question = "On Passage", result = "Passed"),
        )
        assertEquals(Outcome.PASSED_HOUSE, outcomeFromVotes(votes))
    }

    // ---------- reconcileVoteOutcomes ----------

    @Test fun overrides_misclassified_failed_bill_that_actually_passed() {
        // The #30 headline at the bill level: latest action was a rejected
        // amendment, so classifyOutcome stored FAILED — but the bill has a
        // House passage roll call. Reconciliation corrects it.
        val misclassified = bill(
            outcome = Outcome.FAILED,
            votes = listOf(voteRef(question = "On Passage", result = "Passed")),
        )
        val (bills, overrides) = reconcileVoteOutcomes(listOf(misclassified))
        assertEquals(1, overrides)
        assertEquals(Outcome.PASSED_HOUSE, bills.single().outcome)
    }

    @Test fun leaves_matching_outcome_untouched() {
        val correct = bill(
            outcome = Outcome.PASSED_HOUSE,
            votes = listOf(voteRef(question = "On Passage", result = "Passed")),
        )
        val (bills, overrides) = reconcileVoteOutcomes(listOf(correct))
        assertEquals(0, overrides)
        assertEquals(correct, bills.single())
    }

    @Test fun keeps_text_outcome_when_no_passage_vote() {
        // A voice-vote bill (only an amendment roll call) keeps whatever the
        // text classifier decided — reconciliation has no authority here.
        val voiceVote = bill(
            outcome = Outcome.PASSED_SENATE,
            votes = listOf(voteRef(question = "On Agreeing to the Amendment", result = "Rejected")),
        )
        val (bills, overrides) = reconcileVoteOutcomes(listOf(voiceVote))
        assertEquals(0, overrides)
        assertEquals(voiceVote, bills.single())
    }

    @Test fun counts_overrides_across_multiple_bills() {
        val a = bill(
            id = "hr1-119",
            outcome = Outcome.FAILED,
            votes = listOf(voteRef(billId = "hr1-119", question = "On Passage", result = "Passed")),
        )
        val b = bill(id = "hr2-119", outcome = Outcome.PASSED_HOUSE, votes = emptyList())
        val c = bill(
            id = "hr3-119",
            outcome = Outcome.PASSED_HOUSE,
            votes = listOf(
                voteRef(
                    billId = "hr3-119",
                    chamber = Chamber.SENATE,
                    question = "On Passage of the Bill",
                    result = "Bill Passed",
                ),
            ),
        )
        val (bills, overrides) = reconcileVoteOutcomes(listOf(a, b, c))
        assertEquals(2, overrides)
        assertEquals(Outcome.PASSED_HOUSE, bills[0].outcome)
        assertEquals(Outcome.PASSED_HOUSE, bills[1].outcome)
        assertEquals(Outcome.PASSED_SENATE, bills[2].outcome)
    }
}
