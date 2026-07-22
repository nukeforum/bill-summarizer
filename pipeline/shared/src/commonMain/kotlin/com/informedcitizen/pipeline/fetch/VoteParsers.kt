package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.Chamber
import com.informedcitizen.pipeline.model.MemberVote
import com.informedcitizen.pipeline.model.RollCallVote
import com.informedcitizen.pipeline.model.VotePosition
import com.informedcitizen.pipeline.model.VoteRef
import com.informedcitizen.pipeline.model.VoteTotals
import com.informedcitizen.pipeline.model.VotesIndex

/**
 * Pure parsing and record building for roll-call votes. Direct port of
 * Python `_votes.py`; network I/O lives in the fetch-votes driver.
 * Output matches the authoritative wire models
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
 * House source: clerk.house.gov EVS XML (no API key required), one
 * document per roll call at `evs/<year>/roll<NNN>.xml`. Positions are
 * keyed by the `name-id` attribute, which is already the bioguide id —
 * no mapping step. There is no machine-readable vote menu; roll
 * numbers are dense and sequential within a calendar year, so the
 * fetch driver discovers new votes by probing past the highest roll on
 * disk. Speaker elections are published in the same format but record
 * candidate *names* as votes — those throw [UnsupportedVoteException]
 * since they cannot be expressed as yea/nay positions.
 *
 * Both feeds are flat, machine-generated XML, so extraction is
 * regex-based rather than pulling in a KMP XML library (same call as
 * [parseSenateXml] for the schedule feed). Tag names are matched
 * exactly (`<congress>` never matches `<congress_year>` or
 * `<document_congress>`, `<vote>` never matches `<vote-question>`),
 * and the first occurrence of each top-level tag is the same element
 * ElementTree's `findtext` returns on these documents.
 */

/**
 * A well-formed roll call that cannot be expressed as yea/nay
 * positions. Deliberately not an [IllegalArgumentException]: parse
 * failures are retried on the next run (upstream may fix the
 * document), whereas unsupported votes are permanent — the fetch
 * driver should skip them without recording an error. Mirrors Python
 * `_votes.UnsupportedVoteError`.
 */
class UnsupportedVoteException(message: String) : Exception(message)

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

// "16-Jan-2025" (House clerk XML action-date)
private val HOUSE_DATE_RE = Regex("(\\d{1,2})-([A-Za-z]{3})-(\\d{4})")
private val MONTH_ABBREVS = MONTHS.mapKeys { (name, _) -> name.take(3) }

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

// House clerk EVS tags (vote-metadata block + recorded-vote entries).
private val VOTE_METADATA_BLOCK_RE =
    Regex("<vote-metadata>(.*?)</vote-metadata>", RegexOption.DOT_MATCHES_ALL)
private val RECORDED_VOTE_BLOCK_RE =
    Regex("<recorded-vote>(.*?)</recorded-vote>", RegexOption.DOT_MATCHES_ALL)
private val ROLLCALL_NUM_RE = tagRegex("rollcall-num")
private val ACTION_DATE_RE = tagRegex("action-date")
private val VOTE_QUESTION_RE = tagRegex("vote-question")
private val HOUSE_VOTE_RESULT_RE = tagRegex("vote-result")
private val LEGIS_NUM_RE = tagRegex("legis-num")
private val HOUSE_VOTE_RE = tagRegex("vote")
// Attribute extraction from the <legislator ...> tag; the leading
// whitespace keeps `name-id` from matching inside `unaccented-name`
// and friends.
private val NAME_ID_ATTR_RE = Regex("\\sname-id=\"([^\"]*)\"")
private val PARTY_ATTR_RE = Regex("\\sparty=\"([^\"]*)\"")
private val STATE_ATTR_RE = Regex("\\sstate=\"([^\"]*)\"")
// "1st" / "2nd" (House clerk XML session)
private val HOUSE_SESSION_RE = Regex("\\s*(\\d+)")

// "    bioguide: A000382" inside a legislators-YAML id block.
private val YAML_ID_KEY_RE = Regex("(\\w+):\\s*(.*)")

private fun Regex.findText(text: String): String? = find(text)?.groupValues?.get(1)?.trim()

private fun Regex.findInt(text: String, what: String): Int {
    val value = findText(text)
    require(!value.isNullOrEmpty()) { "vote XML: missing <$what>" }
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
 * Calendar year of a House session (session 1 = the Congress's first
 * year). Clerk EVS URLs are year-keyed while our ids are
 * congress/session-keyed.
 */
fun houseSessionYear(congress: Int, session: Int): Int = 1789 + (congress - 1) * 2 + (session - 1)

fun houseVoteSourceUrl(congress: Int, session: Int, rollNumber: Int): String {
    val year = houseSessionYear(congress, session)
    val padded = rollNumber.toString().padStart(3, '0')
    return "https://clerk.house.gov/evs/$year/roll$padded.xml"
}

/**
 * Derive a [com.informedcitizen.pipeline.model.Bill.id] from a House
 * clerk `legis-num` (`"H R 30"`). Returns null for non-bill business
 * (`QUORUM`, `MOTION`, blank — Speaker elections omit the element
 * entirely).
 */
fun billIdFromLegisNum(legisNum: String?, congress: Int): String? {
    val tokens = legisNum.orEmpty().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.size < 2) return null
    return billIdFromDocument(tokens.dropLast(1).joinToString(" "), tokens.last(), congress)
}

/** `"16-Jan-2025"` -> `"2025-01-16"`, locale-independent. */
internal fun parseHouseDate(raw: String): String {
    val m = HOUSE_DATE_RE.find(raw)
    val month = m?.let { MONTH_ABBREVS[it.groupValues[2].lowercase()] }
    requireNotNull(month) { "unparseable House vote date: '$raw'" }
    val day = m.groupValues[1].toInt()
    val year = m.groupValues[3].toInt()
    return "$year-" + month.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
}

/** `"1st"` / `"2nd"` -> 1 / 2. */
internal fun parseHouseSession(raw: String): Int {
    val m = HOUSE_SESSION_RE.matchAt(raw, 0)
    requireNotNull(m) { "unparseable House session: '$raw'" }
    return m.groupValues[1].toInt()
}

/**
 * Parse a House clerk EVS XML into a [RollCallVote].
 *
 * Same policies as [parseSenateVote]: totals are derived from the
 * parsed positions (not the `vote-totals` block) so published tallies
 * always agree with published positions, and an unknown vote label
 * throws so the driver records the vote instead of miscounting.
 * Candidate-ballot votes (Speaker elections, identified by their
 * `totals-by-candidate` blocks) throw [UnsupportedVoteException].
 */
fun parseHouseVote(text: String): RollCallVote {
    val meta = VOTE_METADATA_BLOCK_RE.find(text)?.groupValues?.get(1)
    requireNotNull(meta) { "missing vote-metadata block" }
    val congress = CONGRESS_RE.findInt(meta, "congress")
    val session = parseHouseSession(SESSION_RE.findText(meta).orEmpty())
    val rollNumber = ROLLCALL_NUM_RE.findInt(meta, "rollcall-num")

    if ("<totals-by-candidate>" in meta) {
        throw UnsupportedVoteException(
            "candidate-ballot vote (roll $rollNumber): positions are " +
                "candidate names, not yea/nay",
        )
    }

    val positions = mutableListOf<MemberVote>()
    val totals = mutableMapOf<VotePosition, Int>()
    for (block in RECORDED_VOTE_BLOCK_RE.findAll(text)) {
        val body = block.groupValues[1]
        val bioguide = NAME_ID_ATTR_RE.findText(body).orEmpty()
        require(bioguide.isNotEmpty()) { "recorded-vote without a name-id (roll $rollNumber)" }
        val position = normalizeVotePosition(HOUSE_VOTE_RE.findText(body).orEmpty())
        totals[position] = (totals[position] ?: 0) + 1
        positions.add(
            MemberVote(
                bioguideId = bioguide,
                position = position,
                party = PARTY_ATTR_RE.findText(body)?.ifEmpty { null },
                state = STATE_ATTR_RE.findText(body)?.ifEmpty { null },
            ),
        )
    }
    positions.sortBy { it.bioguideId }

    return RollCallVote(
        id = voteId(Chamber.HOUSE, congress, session, rollNumber),
        congress = congress,
        chamber = Chamber.HOUSE,
        session = session,
        rollNumber = rollNumber,
        date = parseHouseDate(ACTION_DATE_RE.findText(meta).orEmpty()),
        question = VOTE_QUESTION_RE.findText(meta).orEmpty(),
        result = HOUSE_VOTE_RESULT_RE.findText(meta).orEmpty(),
        billId = billIdFromLegisNum(LEGIS_NUM_RE.findText(meta), congress),
        totals = VoteTotals(
            yea = totals[VotePosition.YEA] ?: 0,
            nay = totals[VotePosition.NAY] ?: 0,
            present = totals[VotePosition.PRESENT] ?: 0,
            notVoting = totals[VotePosition.NOT_VOTING] ?: 0,
        ),
        positions = positions,
        sourceUrl = houseVoteSourceUrl(congress, session, rollNumber),
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

/** Wire value for a [VotePosition], used as [VoteRef.partySplit] keys. */
internal val VotePosition.wireName: String
    get() = when (this) {
        VotePosition.YEA -> "yea"
        VotePosition.NAY -> "nay"
        VotePosition.PRESENT -> "present"
        VotePosition.NOT_VOTING -> "not_voting"
    }

/**
 * Per-position party counts for [VoteRef.partySplit]: position keys in
 * wire order, only when at least one member with a known party holds
 * that position; party keys sorted; members with no party left out.
 * Mirrors Python `_votes.build_party_split` (byte-identical key order).
 */
fun buildPartySplit(positions: List<MemberVote>): Map<String, Map<String, Int>> {
    val counts = mutableMapOf<VotePosition, MutableMap<String, Int>>()
    for (member in positions) {
        val party = member.party
        if (party.isNullOrEmpty()) continue
        val byParty = counts.getOrPut(member.position) { mutableMapOf() }
        byParty[party] = (byParty[party] ?: 0) + 1
    }
    val out = LinkedHashMap<String, Map<String, Int>>()
    for (position in VotePosition.entries) {
        val byParty = counts[position] ?: continue
        out[position.wireName] = byParty.keys.sorted().associateWith { byParty.getValue(it) }
    }
    return out
}

/** A [VotesIndex] row: the vote minus positions, plus party split and file path. */
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
    partySplit = buildPartySplit(vote.positions),
    path = voteFileRelPath(vote.congress, vote.chamber, vote.session, vote.rollNumber),
)

/**
 * Newest first (matching the bills manifests); ties broken by roll
 * number then chamber for a deterministic order. Shared between the
 * index and the per-bill `votes` lists so both stay byte-identical
 * with the Python `(date, roll_number, chamber)` reverse sort.
 */
private val voteRefNewestFirst: Comparator<VoteRef> =
    compareByDescending<VoteRef> { it.date }
        .thenByDescending { it.rollNumber }
        .thenByDescending { it.chamber.wireName }

/**
 * Assemble the per-Congress `congress<N>_votes.json` payload. Newest
 * first (matching the bills manifests); ties broken by roll number
 * then chamber for a deterministic file.
 */
fun buildVotesIndex(congress: Int, refs: List<VoteRef>, generatedAt: String): VotesIndex {
    val ordered = refs.sortedWith(voteRefNewestFirst)
    return VotesIndex(
        generatedAt = generatedAt,
        congress = congress,
        voteCount = ordered.size,
        votes = ordered,
    )
}

/**
 * Copy of [bills] with the derived [Bill.votes] field recomputed from
 * the index [refs]: each bill gets the rows whose `bill_id` matches its
 * id, newest first with the same tiebreak as [buildVotesIndex] (empty
 * when no roll call touched the bill). Mirrors Python
 * `_votes.attach_vote_refs`.
 */
fun attachVoteRefs(bills: List<Bill>, refs: List<VoteRef>): List<Bill> {
    val byBill = mutableMapOf<String, MutableList<VoteRef>>()
    for (ref in refs.sortedWith(voteRefNewestFirst)) {
        val billId = ref.billId
        if (billId.isNullOrEmpty()) continue
        byBill.getOrPut(billId) { mutableListOf() } += ref
    }
    return bills.map { it.copy(votes = byBill[it.id].orEmpty()) }
}

/**
 * Copy of [bills] with the derived [Bill.votes] field cleared, so merge
 * comparisons see only bill data. Freshly fetched records carry no
 * votes; without stripping, every refetched bill would compare unequal
 * to its on-disk enriched copy and inflate the merge's `updated` count.
 * Callers re-attach after merging. Mirrors Python
 * `_votes.strip_vote_refs`.
 */
fun stripVoteRefs(bills: List<Bill>): List<Bill> =
    bills.map { if (it.votes.isEmpty()) it else it.copy(votes = emptyList()) }
