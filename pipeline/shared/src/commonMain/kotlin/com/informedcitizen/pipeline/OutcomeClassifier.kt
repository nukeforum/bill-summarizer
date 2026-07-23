package com.informedcitizen.pipeline

import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.Chamber
import com.informedcitizen.pipeline.model.LifecycleStatus
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.VoteRef

const val OUTCOME_PASSED_HOUSE: String = "passed_house"
const val OUTCOME_PASSED_SENATE: String = "passed_senate"
const val OUTCOME_ENACTED: String = "enacted"
const val OUTCOME_VETOED: String = "vetoed"
const val OUTCOME_FAILED: String = "failed"

private val OUTCOME_RULES: List<Pair<String, List<String>>> = listOf(
    OUTCOME_ENACTED to listOf("became public law", "became law"),
    OUTCOME_VETOED to listOf("vetoed by president"),
    OUTCOME_FAILED to listOf(
        "failed of passage",
        "motion to table agreed to",
        "failed to pass",
        "rejected",
    ),
    OUTCOME_PASSED_HOUSE to listOf(
        "passed/agreed to in house",
        "passed house",
        "on passage passed by the house",
        "agreed to in house",
    ),
    OUTCOME_PASSED_SENATE to listOf(
        "passed/agreed to in senate",
        "passed senate",
        "on passage passed by the senate",
        "agreed to in senate",
    ),
)

fun classifyOutcome(actionText: String): String? {
    val needle = actionText.lowercase()
    for ((outcome, patterns) in OUTCOME_RULES) {
        if (patterns.any { it in needle }) return outcome
    }
    return null
}

/**
 * Map the wire-format outcome string (the one [classifyOutcome] and
 * Python's `_OUTCOME_RULES` return) to the typed [Outcome] enum.
 * Returns null on unknown input. Kept here (alongside the constants
 * and the classifier) so callers don't have to import both packages
 * to round-trip an outcome.
 */
fun outcomeFromWireString(value: String): Outcome? =
    when (value) {
        OUTCOME_PASSED_HOUSE -> Outcome.PASSED_HOUSE
        OUTCOME_PASSED_SENATE -> Outcome.PASSED_SENATE
        OUTCOME_ENACTED -> Outcome.ENACTED
        OUTCOME_VETOED -> Outcome.VETOED
        OUTCOME_FAILED -> Outcome.FAILED
        else -> null
    }

// ---------- pre-floor lifecycle classification (backlog #39) ---------------
//
// The vast majority of bills never reach a floor vote — they are introduced,
// referred to committee, and often reported out — but [classifyOutcome] only
// recognizes the 5 *terminal* outcomes, so pre-floor bills historically got
// dropped as `no_outcome_match`. These lifecycle statuses let those bills carry
// a real, derived status instead of being silently dropped. Precedence: a
// terminal floor outcome ALWAYS wins over a lifecycle status (see
// [classifyBillStatus]). Mirrors Python `_common`'s `classify_lifecycle_status`
// / `classify_bill_status` field-for-field for pipeline parity.

const val LIFECYCLE_INTRODUCED: String = "introduced"
const val LIFECYCLE_IN_COMMITTEE: String = "in_committee"
const val LIFECYCLE_REPORTED: String = "reported"

// Ordered most-advanced-first so a bill's latest action maps to the furthest
// stage it has reached: reported (out of committee, floor-ready) beats
// in_committee (referred) beats introduced. Matched case-insensitively as
// substrings over the latest-action text, mirroring [OUTCOME_RULES].
private val LIFECYCLE_RULES: List<Pair<String, List<String>>> = listOf(
    LIFECYCLE_REPORTED to listOf(
        "reported by",
        "reported (",
        "reported to the house",
        "reported to the senate",
        "reported original measure",
        "reported, without amendment",
        "reported with amendment",
        "ordered to be reported",
        "placed on the union calendar",
        "placed on the house calendar",
        "placed on the senate legislative calendar",
        "placed on senate legislative calendar",
    ),
    LIFECYCLE_IN_COMMITTEE to listOf(
        "referred to the committee",
        "referred to the subcommittee",
        "referred to the house committee",
        "referred to the senate committee",
        "committee consideration and mark-up",
        "committee hearings held",
        "hearings held",
        "sponsor introductory remarks",
    ),
    LIFECYCLE_INTRODUCED to listOf(
        "introduced in house",
        "introduced in senate",
        "introduced in the house",
        "introduced in the senate",
    ),
)

/**
 * The pre-floor lifecycle status implied by a bill's latest-action text, or
 * null when no rule matches.
 *
 * Deliberately narrow and additive: this only recognizes the pre-floor stages
 * (introduced / in_committee / reported). Terminal floor outcomes are
 * [classifyOutcome]'s job and take precedence — see [classifyBillStatus].
 */
fun classifyLifecycleStatus(actionText: String): String? {
    val needle = actionText.lowercase()
    for ((status, patterns) in LIFECYCLE_RULES) {
        if (patterns.any { it in needle }) return status
    }
    return null
}

/**
 * Classify a bill's status from its latest-action text as `(status, isOutcome)`.
 *
 * Precedence, defined once here (backlog #39): a terminal floor outcome always
 * wins over a pre-floor lifecycle status. [BillStatus.isOutcome] is true when
 * the status is one of the terminal [OUTCOME_RULES] values, false for a
 * lifecycle status. Returns `BillStatus(null, false)` when neither matches.
 */
data class BillStatus(val status: String?, val isOutcome: Boolean)

fun classifyBillStatus(actionText: String): BillStatus {
    val outcome = classifyOutcome(actionText)
    if (outcome != null) return BillStatus(outcome, isOutcome = true)
    val lifecycle = classifyLifecycleStatus(actionText)
    if (lifecycle != null) return BillStatus(lifecycle, isOutcome = false)
    return BillStatus(null, isOutcome = false)
}

/**
 * Map a lifecycle-status wire string (the one [classifyLifecycleStatus] and
 * Python's `_LIFECYCLE_RULES` return) to the typed [LifecycleStatus] enum for
 * [Bill.lifecycleStatus]. Returns null on unknown input — kept alongside
 * [outcomeFromWireString] so callers round-trip a status without importing the
 * model package's `@SerialName` details. Never returns [LifecycleStatus.UNKNOWN]:
 * the pipeline emits only recognised statuses; UNKNOWN is a decode-side fallback.
 */
fun lifecycleStatusFromWireString(value: String): LifecycleStatus? =
    when (value) {
        LIFECYCLE_INTRODUCED -> LifecycleStatus.INTRODUCED
        LIFECYCLE_IN_COMMITTEE -> LifecycleStatus.IN_COMMITTEE
        LIFECYCLE_REPORTED -> LifecycleStatus.REPORTED
        else -> null
    }

/**
 * The wire-format string for a typed [LifecycleStatus] — the inverse of
 * [lifecycleStatusFromWireString]. Kept here (not on the model enum) so
 * callers round-trip a status through the same `@SerialName` contract without
 * importing the model package's serialization details. Used by the app's
 * SQLDelight cache (backlog #41) to store a bill's lifecycle status in an
 * extracted, DB-filterable column that matches the values the pipeline emits.
 * [LifecycleStatus.UNKNOWN] round-trips to its `@SerialName` `"unknown"`.
 */
fun lifecycleStatusToWireString(status: LifecycleStatus): String =
    when (status) {
        LifecycleStatus.INTRODUCED -> LIFECYCLE_INTRODUCED
        LifecycleStatus.IN_COMMITTEE -> LIFECYCLE_IN_COMMITTEE
        LifecycleStatus.REPORTED -> LIFECYCLE_REPORTED
        LifecycleStatus.UNKNOWN -> "unknown"
    }

// ---------- outcome from roll-call votes (backlog #30) --------------------

/**
 * Question-text markers that identify a *passage*-type roll call — the
 * only votes that decide a bill. Mirrors Python `_common`'s
 * `_PASSAGE_QUESTION_MARKERS`. Amendment, table, cloture and other
 * procedural questions are deliberately excluded so they can never
 * override a bill's outcome (the substring text classifier's bug: a
 * rejected amendment read as a failed bill).
 */
private val PASSAGE_QUESTION_MARKERS: List<String> = listOf(
    "on passage", // House "On Passage"; Senate "On Passage of the Bill"
    "suspend the rules and pass", // House passage under suspension of the rules
    "suspend the rules and agree", // House resolution adoption under suspension
    "on agreeing to the resolution", // simple / concurrent resolution adoption
    "on the conference report", // final adoption of the conference report
    "on concurring", // concur in the other chamber's amendment (final passage)
    "on the motion to concur",
    "on the joint resolution",
    "on overriding the veto", // veto override
)

private fun voteQuestionIsPassage(question: String): Boolean {
    val q = question.lowercase()
    return PASSAGE_QUESTION_MARKERS.any { it in q }
}

/**
 * True the measure prevailed, false it lost, null unrecognised. Failure
 * markers are tested first so "Not Agreed to" / "Bill Defeated" are never
 * read as a pass by a stray "agreed"/"pass" substring. Mirrors Python
 * `_common._vote_result_prevailed`.
 */
private fun voteResultPrevailed(result: String): Boolean? {
    val r = result.lowercase()
    if (listOf("fail", "reject", "defeat", "negativ", "not agreed", "not passed").any { it in r }) {
        return false
    }
    if (listOf("pass", "agreed to", "adopted", "concurred").any { it in r }) {
        return true
    }
    return null
}

/**
 * The [Outcome] a single roll call implies, or null if it doesn't decide
 * the bill. Only passage-type questions with a recognised result yield an
 * outcome; amendment and procedural votes return null so they can never
 * override a bill's outcome. Mirrors Python `_common.outcome_from_vote`.
 */
fun outcomeFromVote(chamber: Chamber, question: String, result: String): Outcome? {
    if (!voteQuestionIsPassage(question)) return null
    return when (voteResultPrevailed(result)) {
        false -> Outcome.FAILED
        true -> when (chamber) {
            Chamber.HOUSE -> Outcome.PASSED_HOUSE
            Chamber.SENATE -> Outcome.PASSED_SENATE
        }
        null -> null
    }
}

/**
 * Derive a bill's [Outcome] from its linked roll-call votes, or null.
 *
 * Considers only passage-type roll calls ([outcomeFromVote]); the most
 * recent decisive one by date wins (roll number then chamber break ties),
 * mirroring the latest-action semantics of [classifyOutcome] but grounded
 * in the actual vote rather than action text. Returns null when no linked
 * roll call is a decisive passage vote (the bill moved by voice vote, or
 * every roll call was an amendment) — the caller then keeps the text
 * classifier's outcome. Mirrors Python `_common.outcome_from_votes`.
 */
fun outcomeFromVotes(votes: List<VoteRef>): Outcome? =
    votes
        .mapNotNull { vote ->
            outcomeFromVote(vote.chamber, vote.question, vote.result)?.let { vote to it }
        }
        .maxWithOrNull(
            // Same (date, roll_number, chamber) order as buildVotesIndex and
            // Python's reverse sort; Chamber's ordinal (HOUSE, SENATE) matches
            // the wire strings' lexical order ("house" < "senate").
            compareBy<Pair<VoteRef, Outcome>>({ it.first.date })
                .thenBy { it.first.rollNumber }
                .thenBy { it.first.chamber },
        )
        ?.second

/** Result of [reconcileVoteOutcomes]: the corrected bills and how many changed. */
data class ReconcileResult(val bills: List<Bill>, val overrides: Int)

/**
 * Override each bill's text-derived [Bill.outcome] with the outcome its
 * linked roll-call votes imply, when a passage vote decides the measure.
 *
 * Bills are classified at build time from latest-action text
 * ([classifyOutcome]), which can misread an amendment rejection or a
 * motion to table as a *failed bill*. Once roll calls are linked
 * ([com.informedcitizen.pipeline.fetch.attachVoteRefs] sets each bill's
 * [Bill.votes]), a passage vote is the authoritative signal: where
 * [outcomeFromVotes] yields an outcome that differs from the stored one,
 * this replaces it and counts the correction. Bills with no decisive
 * passage roll call keep their text outcome. Returns a copy of [bills]
 * with corrections applied plus the override count (a discrepancy signal
 * the caller can surface). Mirrors Python `_common.reconcile_vote_outcomes`.
 */
fun reconcileVoteOutcomes(bills: List<Bill>): ReconcileResult {
    var overrides = 0
    val reconciled = bills.map { bill ->
        val derived = outcomeFromVotes(bill.votes)
        if (derived != null && derived != bill.outcome) {
            overrides++
            bill.copy(outcome = derived)
        } else {
            bill
        }
    }
    return ReconcileResult(reconciled, overrides)
}
