package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Chamber
import com.informedcitizen.pipeline.model.MemberVote
import com.informedcitizen.pipeline.model.RollCallVote
import com.informedcitizen.pipeline.model.VotePosition
import com.informedcitizen.pipeline.model.VoteRef
import com.informedcitizen.pipeline.model.VoteTotals
import com.informedcitizen.pipeline.model.VotesIndex

/**
 * Pure parsing and record building for Senate roll-call votes. Direct
 * port of Python `_votes.py`; network I/O lives in the fetch-votes
 * driver. Output matches the authoritative wire models
 * [RollCallVote] / [VotesIndex].
 *
 * Senate source: senate.gov LIS XML (no API key required). The vote
 * menu (`vote_menu_<congress>_<session>.xml`) lists every roll call of
 * a session; per-vote detail XML carries per-member positions keyed by
 * `lis_member_id`, which we map to bioguide ids via
 * unitedstates/congress-legislators (`id.lis` -> `id.bioguide`), since
 * bioguide is the member key used across the rest of the published
 * JSON. The map must union legislators-current.yaml with
 * legislators-historical.yaml: a member who left the Senate
 * mid-Congress moves to the historical file while their roll calls
 * remain published.
 *
 * The LIS documents are flat, machine-generated XML, so extraction is
 * regex-based rather than pulling in a KMP XML library (same call as
 * [parseSenateXml] for the schedule feed). Tag names are matched
 * exactly (`<congress>` never matches `<congress_year>` or
 * `<document_congress>`), and the first occurrence of each top-level
 * tag is the same element ElementTree's `findtext` returns on these
 * documents.
 */

/** Wire value for a [Chamber], used in vote ids and file paths. */
internal val Chamber.wireName: String
    get() = when (this) {
        Chamber.HOUSE -> "house"
        Chamber.SENATE -> "senate"
    }

// Upstream label -> normalized wire value. House uses Aye/No on
// motions and Yea/Nay on passage; Senate uses Guilty/Not Guilty on
// impeachment articles.
private val POSITION_LABELS: Map<String, VotePosition> = mapOf(
    "yea" to VotePosition.YEA,
    "aye" to VotePosition.YEA,
    "guilty" to VotePosition.YEA,
    "nay" to VotePosition.NAY,
    "no" to VotePosition.NAY,
    "not guilty" to VotePosition.NAY,
    "present" to VotePosition.PRESENT,
    "present, giving live pair" to VotePosition.PRESENT,
    "not voting" to VotePosition.NOT_VOTING,
    "absent" to VotePosition.NOT_VOTING,
)

// Matches Python _common._BILL_TYPE_TO_SLUG — the eight document types
// that map to a Bill.id. Anything else (PN nominations, treaty
// documents, amendments without a document) yields billId = null.
private val BILL_TYPES = setOf("hr", "s", "hjres", "sjres", "hconres", "sconres", "hres", "sres")

private val MONTHS = mapOf(
    "january" to 1, "february" to 2, "march" to 3, "april" to 4,
    "may" to 5, "june" to 6, "july" to 7, "august" to 8,
    "september" to 9, "october" to 10, "november" to 11, "december" to 12,
)

// "November 10, 2025,  08:58 PM" (Senate detail XML vote_date)
private val SENATE_DATE_RE = Regex("([A-Za-z]+)\\s+(\\d{1,2}),\\s*(\\d{4})")

private fun tagRegex(tag: String) = Regex("<$tag>([^<]*)</$tag>")

private val CONGRESS_RE = tagRegex("congress")
private val SESSION_RE = tagRegex("session")
private val VOTE_NUMBER_RE = tagRegex("vote_number")
private val VOTE_DATE_RE = tagRegex("vote_date")
private val QUESTION_RE = tagRegex("question")
private val VOTE_RESULT_RE = tagRegex("vote_result")
private val DOCUMENT_TYPE_RE = tagRegex("document_type")
private val DOCUMENT_NUMBER_RE = tagRegex("document_number")
private val LIS_MEMBER_ID_RE = tagRegex("lis_member_id")
private val VOTE_CAST_RE = tagRegex("vote_cast")
private val PARTY_RE = tagRegex("party")
private val STATE_RE = tagRegex("state")
private val DOCUMENT_BLOCK_RE = Regex("<document>(.*?)</document>", RegexOption.DOT_MATCHES_ALL)
private val MEMBER_BLOCK_RE = Regex("<member>(.*?)</member>", RegexOption.DOT_MATCHES_ALL)

// "    bioguide: A000382" inside a legislators-YAML id block.
private val YAML_ID_KEY_RE = Regex("(\\w+):\\s*(.*)")

private fun Regex.findText(text: String): String? = find(text)?.groupValues?.get(1)?.trim()

private fun Regex.findInt(text: String, what: String): Int {
    val value = findText(text)
    require(!value.isNullOrEmpty()) { "Senate vote XML: missing <$what>" }
    return value.toInt()
}

/**
 * Map an upstream vote label to one of the four wire values. Throws on
 * an unrecognized label so callers surface new upstream vocabulary
 * instead of silently miscounting.
 */
fun normalizeVotePosition(raw: String): VotePosition {
    return requireNotNull(POSITION_LABELS[raw.trim().lowercase()]) { "unknown vote position: '$raw'" }
}

/** `<chamber>-<congress>-<session>-<roll>`, e.g. `senate-119-1-618`. */
fun voteId(chamber: Chamber, congress: Int, session: Int, rollNumber: Int): String =
    "${chamber.wireName}-$congress-$session-$rollNumber"

/** Vote-file location relative to docs/data/, e.g. `votes/congress119/house-1-17.json`. */
fun voteFileRelPath(congress: Int, chamber: Chamber, session: Int, rollNumber: Int): String =
    "votes/congress$congress/${chamber.wireName}-$session-$rollNumber.json"

fun senateVoteSourceUrl(congress: Int, session: Int, rollNumber: Int): String {
    val padded = rollNumber.toString().padStart(5, '0')
    return "https://www.senate.gov/legislative/LIS/roll_call_votes/" +
        "vote$congress$session/vote_${congress}_${session}_$padded.xml"
}

fun senateVoteMenuUrl(congress: Int, session: Int): String =
    "https://www.senate.gov/legislative/LIS/roll_call_lists/vote_menu_${congress}_$session.xml"

/**
 * `"November 10, 2025,  08:58 PM"` -> `"2025-11-10"`. Month names are
 * mapped explicitly so the result does not depend on process locale.
 */
internal fun parseSenateDate(raw: String): String {
    val m = SENATE_DATE_RE.find(raw)
    val month = m?.let { MONTHS[it.groupValues[1].lowercase()] }
    requireNotNull(month) { "unparseable Senate vote date: '$raw'" }
    val day = m.groupValues[2].toInt()
    val year = m.groupValues[3].toInt()
    return "$year-" + month.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
}

/**
 * Derive a [com.informedcitizen.pipeline.model.Bill.id] (`hr5371-119`)
 * from a Senate XML document block. Returns null for non-bill
 * documents (nominations, treaties) — the corresponding roll call is
 * published with `bill_id: null`.
 */
fun billIdFromDocument(docType: String?, docNumber: String?, congress: Int): String? {
    if (docType.isNullOrEmpty() || docNumber.isNullOrEmpty()) return null
    val billType = docType.replace(".", "").replace(" ", "").lowercase()
    val number = docNumber.trim()
    if (billType !in BILL_TYPES || number.isEmpty() || !number.all { it.isDigit() }) return null
    return "$billType$number-$congress"
}

/** Parsed Senate vote-menu XML: which roll calls exist in a session. */
data class SenateVoteMenu(
    val congress: Int,
    val session: Int,
    /** Sorted ascending. */
    val voteNumbers: List<Int>,
)

/**
 * Parse a Senate vote-menu XML. The menu carries tallies and questions
 * too, but the fetch driver only needs to know which roll calls exist;
 * everything else comes from the per-vote detail XML.
 */
fun parseSenateVoteMenu(text: String): SenateVoteMenu {
    val numbers = VOTE_NUMBER_RE.findAll(text)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotEmpty() && it.all { c -> c.isDigit() } }
        .map { it.toInt() }
        .toList()
        .sorted()
    return SenateVoteMenu(
        congress = CONGRESS_RE.findInt(text, "congress"),
        session = SESSION_RE.findInt(text, "session"),
        voteNumbers = numbers,
    )
}

/**
 * Parse a Senate roll-call detail XML into a [RollCallVote].
 *
 * Totals are derived from the parsed positions rather than the XML
 * `<count>` block so the published tallies always agree with the
 * published positions. Throws on an unknown vote label or an
 * `lis_member_id` missing from [lisToBioguide] (a senator not yet in
 * congress-legislators, or a departed one when the caller forgot the
 * historical file) — the fetch driver skips that vote and records the
 * error rather than publishing incomplete positions.
 */
fun parseSenateVote(text: String, lisToBioguide: Map<String, String>): RollCallVote {
    val congress = CONGRESS_RE.findInt(text, "congress")
    val session = SESSION_RE.findInt(text, "session")
    val rollNumber = VOTE_NUMBER_RE.findInt(text, "vote_number")

    val positions = mutableListOf<MemberVote>()
    val totals = mutableMapOf<VotePosition, Int>()
    for (block in MEMBER_BLOCK_RE.findAll(text)) {
        val body = block.groupValues[1]
        val lisId = LIS_MEMBER_ID_RE.findText(body).orEmpty()
        val bioguide = lisToBioguide[lisId]
        requireNotNull(bioguide) { "no bioguide mapping for lis_member_id '$lisId'" }
        val position = normalizeVotePosition(VOTE_CAST_RE.findText(body).orEmpty())
        totals[position] = (totals[position] ?: 0) + 1
        positions.add(
            MemberVote(
                bioguideId = bioguide,
                position = position,
                party = PARTY_RE.findText(body)?.ifEmpty { null },
                state = STATE_RE.findText(body)?.ifEmpty { null },
            ),
        )
    }
    positions.sortBy { it.bioguideId }

    val documentBody = DOCUMENT_BLOCK_RE.find(text)?.groupValues?.get(1)
    val billId = documentBody?.let {
        billIdFromDocument(
            DOCUMENT_TYPE_RE.findText(it),
            DOCUMENT_NUMBER_RE.findText(it),
            congress,
        )
    }

    return RollCallVote(
        id = voteId(Chamber.SENATE, congress, session, rollNumber),
        congress = congress,
        chamber = Chamber.SENATE,
        session = session,
        rollNumber = rollNumber,
        date = parseSenateDate(VOTE_DATE_RE.findText(text).orEmpty()),
        question = QUESTION_RE.findText(text).orEmpty(),
        result = VOTE_RESULT_RE.findText(text).orEmpty(),
        billId = billId,
        totals = VoteTotals(
            yea = totals[VotePosition.YEA] ?: 0,
            nay = totals[VotePosition.NAY] ?: 0,
            present = totals[VotePosition.PRESENT] ?: 0,
            notVoting = totals[VotePosition.NOT_VOTING] ?: 0,
        ),
        positions = positions,
        sourceUrl = senateVoteSourceUrl(congress, session, rollNumber),
    )
}

/**
 * Extract `{lis id -> bioguide id}` from a congress-legislators YAML
 * file. Only senators carry an `id.lis` key, so the result covers
 * exactly the members that appear in Senate roll-call XML.
 *
 * No KMP YAML library is in the dependency set, so this is a narrow
 * line scanner for the fixed shape the legislators files actually use:
 * each list entry opens with `- id:` at column 0 and the id sub-keys
 * sit on their own indented `key: value` lines. Anything outside an id
 * block is ignored, matching the Python parser's behavior of only
 * reading `entry["id"]["lis"/"bioguide"]`.
 */
fun parseLisToBioguideYaml(text: String): Map<String, String> {
    val out = mutableMapOf<String, String>()
    var inIdBlock = false
    var lis: String? = null
    var bioguide: String? = null

    fun flush() {
        val l = lis
        val b = bioguide
        if (l != null && b != null) out[l] = b
        lis = null
        bioguide = null
    }

    for (raw in text.lineSequence()) {
        val line = raw.trimEnd()
        if (line.isEmpty()) continue
        val content = line.trimStart()
        val indent = line.length - content.length
        when {
            indent == 0 -> {
                flush()
                inIdBlock = content.startsWith("- ") && content.removePrefix("- ").trim() == "id:"
            }
            inIdBlock && indent >= 4 -> {
                val m = YAML_ID_KEY_RE.matchEntire(content) ?: continue
                val value = m.groupValues[2].trim().trim('\'', '"').ifEmpty { null }
                when (m.groupValues[1]) {
                    "bioguide" -> bioguide = value
                    "lis" -> lis = value
                }
            }
            inIdBlock -> {
                // An entry-level key (name:, terms:, ...) ends the id block.
                flush()
                inIdBlock = false
            }
        }
    }
    flush()
    return out
}

/** A [VotesIndex] row: the vote minus positions, plus its file path. */
fun buildVoteRef(vote: RollCallVote): VoteRef = VoteRef(
    id = vote.id,
    chamber = vote.chamber,
    session = vote.session,
    rollNumber = vote.rollNumber,
    date = vote.date,
    question = vote.question,
    result = vote.result,
    billId = vote.billId,
    totals = vote.totals,
    path = voteFileRelPath(vote.congress, vote.chamber, vote.session, vote.rollNumber),
)

/**
 * Assemble the per-Congress `congress<N>_votes.json` payload. Newest
 * first (matching the bills manifests); ties broken by roll number
 * then chamber for a deterministic file.
 */
fun buildVotesIndex(congress: Int, refs: List<VoteRef>, generatedAt: String): VotesIndex {
    val ordered = refs.sortedWith(
        compareByDescending<VoteRef> { it.date }
            .thenByDescending { it.rollNumber }
            .thenByDescending { it.chamber.wireName },
    )
    return VotesIndex(
        generatedAt = generatedAt,
        congress = congress,
        voteCount = ordered.size,
        votes = ordered,
    )
}
