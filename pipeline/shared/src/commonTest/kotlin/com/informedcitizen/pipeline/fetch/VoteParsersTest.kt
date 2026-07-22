package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.Chamber
import com.informedcitizen.pipeline.model.MemberVote
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.RollCallVote
import com.informedcitizen.pipeline.model.Sponsor
import com.informedcitizen.pipeline.model.VotePosition
import com.informedcitizen.pipeline.model.VoteRef
import com.informedcitizen.pipeline.model.VoteTotals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mirrors Python `test_votes.py` on the same real senate.gov and
 * clerk.house.gov fixtures (see [SENATE_VOTE_618_XML] and
 * [HOUSE_VOTE_ROLL17_XML]), keeping the KMP port in behavioral parity
 * with the canonical Python `_votes.py`.
 */
class VoteParsersTest {

    private fun vote618(): RollCallVote = parseSenateVote(SENATE_VOTE_618_XML, LIS_TO_BIOGUIDE)

    private fun houseVote17(): RollCallVote = parseHouseVote(HOUSE_VOTE_ROLL17_XML)

    // ------------------------------------------------------ normalization

    @Test
    fun positionLabelsNormalizeToWireValues() {
        assertEquals(VotePosition.YEA, normalizeVotePosition("Yea"))
        assertEquals(VotePosition.YEA, normalizeVotePosition("Aye"))
        assertEquals(VotePosition.YEA, normalizeVotePosition("Guilty"))
        assertEquals(VotePosition.NAY, normalizeVotePosition("Nay"))
        assertEquals(VotePosition.NAY, normalizeVotePosition("No"))
        assertEquals(VotePosition.NAY, normalizeVotePosition("Not Guilty"))
        assertEquals(VotePosition.PRESENT, normalizeVotePosition("Present"))
        assertEquals(VotePosition.PRESENT, normalizeVotePosition("Present, Giving Live Pair"))
        assertEquals(VotePosition.NOT_VOTING, normalizeVotePosition("Not Voting"))
        assertEquals(VotePosition.NOT_VOTING, normalizeVotePosition("Absent"))
    }

    @Test
    fun unknownPositionLabelThrows() {
        val e = assertFailsWith<IllegalArgumentException> { normalizeVotePosition("Abstained") }
        assertTrue(e.message.orEmpty().contains("unknown vote position"))
    }

    // ------------------------------------------------- ids, paths, urls

    @Test
    fun voteIdAndRelPathFollowKdocConventions() {
        assertEquals("house-119-1-17", voteId(Chamber.HOUSE, 119, 1, 17))
        assertEquals("votes/congress119/house-1-17.json", voteFileRelPath(119, Chamber.HOUSE, 1, 17))
    }

    @Test
    fun senateUrlsZeroPadRollNumberOnly() {
        assertEquals(
            "https://www.senate.gov/legislative/LIS/roll_call_votes/" +
                "vote1191/vote_119_1_00618.xml",
            senateVoteSourceUrl(119, 1, 618),
        )
        assertEquals(
            "https://www.senate.gov/legislative/LIS/roll_call_lists/vote_menu_119_1.xml",
            senateVoteMenuUrl(119, 1),
        )
    }

    @Test
    fun billIdFromDocumentCoversBillTypesAndNonBills() {
        assertEquals("hr5371-119", billIdFromDocument("H.R.", "5371", 119))
        assertEquals("sjres76-119", billIdFromDocument("S.J.Res.", "76", 119))
        assertEquals("s42-119", billIdFromDocument("S.", "42", 119))
        // Nominations, treaties, and absent documents are not bills.
        assertNull(billIdFromDocument("PN", "373", 119))
        assertNull(billIdFromDocument(null, null, 119))
        assertNull(billIdFromDocument("H.R.", "", 119))
    }

    // ------------------------------------------------------ vote menu

    @Test
    fun menuParsesCongressSessionAndSortedVoteNumbers() {
        val menu = parseSenateVoteMenu(SENATE_VOTE_MENU_119_1_XML)
        assertEquals(119, menu.congress)
        assertEquals(1, menu.session)
        assertEquals(menu.voteNumbers.sorted(), menu.voteNumbers)
        // 618 is the H.R. 5371 passage vote; 655 is an en-bloc nomination
        // vote whose <vote_number> sits alongside nested <en_bloc> blocks.
        assertTrue(618 in menu.voteNumbers)
        assertTrue(655 in menu.voteNumbers)
        assertEquals(6, menu.voteNumbers.size)
    }

    // ----------------------------------------------------- vote detail

    @Test
    fun senateVote618ParsesToWireShape() {
        val vote = vote618()
        assertEquals("senate-119-1-618", vote.id)
        assertEquals(119, vote.congress)
        assertEquals(Chamber.SENATE, vote.chamber)
        assertEquals(1, vote.session)
        assertEquals(618, vote.rollNumber)
        assertEquals("2025-11-10", vote.date)
        assertEquals("On Passage of the Bill", vote.question)
        assertEquals("Bill Passed", vote.result)
        assertEquals("hr5371-119", vote.billId)
        assertEquals(senateVoteSourceUrl(119, 1, 618), vote.sourceUrl)
    }

    @Test
    fun senateVote618TotalsMatchPositionsAndOfficialTally() {
        val vote = vote618()
        // Official tally: Bill Passed (60-40), all 100 senators voting.
        assertEquals(VoteTotals(yea = 60, nay = 40, present = 0, notVoting = 0), vote.totals)
        assertEquals(100, vote.positions.size)
        val counted = vote.positions.groupingBy { it.position }.eachCount()
        assertEquals(60, counted[VotePosition.YEA])
        assertEquals(40, counted[VotePosition.NAY])
        assertNull(counted[VotePosition.PRESENT])
        assertNull(counted[VotePosition.NOT_VOTING])
    }

    @Test
    fun senateVote618PositionsAreBioguideKeyedAndSorted() {
        val vote = vote618()
        val ids = vote.positions.map { it.bioguideId }
        assertEquals(ids.sorted(), ids)
        assertEquals(100, ids.toSet().size)
        // Alsobrooks (D-MD), lis S428 -> bioguide A000382, voted Nay.
        val alsobrooks = vote.positions.first { it.bioguideId == "A000382" }
        assertEquals(
            MemberVote(bioguideId = "A000382", position = VotePosition.NAY, party = "D", state = "MD"),
            alsobrooks,
        )
    }

    @Test
    fun unmappedLisMemberIdThrows() {
        val mapping = LIS_TO_BIOGUIDE - "S428"
        val e = assertFailsWith<IllegalArgumentException> {
            parseSenateVote(SENATE_VOTE_618_XML, mapping)
        }
        assertTrue(e.message.orEmpty().contains("S428"))
    }

    // ---------------------------------------------------- house votes

    @Test
    fun houseUrlsAreYearKeyedWithThreeDigitRolls() {
        assertEquals(2025, houseSessionYear(119, 1))
        assertEquals(2026, houseSessionYear(119, 2))
        assertEquals("https://clerk.house.gov/evs/2025/roll017.xml", houseVoteSourceUrl(119, 1, 17))
        // Rolls past 999 (seen in busy years) are not truncated by the padding.
        assertEquals("https://clerk.house.gov/evs/2026/roll1002.xml", houseVoteSourceUrl(119, 2, 1002))
    }

    @Test
    fun billIdFromLegisNumCoversBillTypesAndNonBills() {
        assertEquals("hr30-119", billIdFromLegisNum("H R 30", 119))
        assertEquals("hres5-119", billIdFromLegisNum("H RES 5", 119))
        assertEquals("hjres20-119", billIdFromLegisNum("H J RES 20", 119))
        assertEquals("hconres14-119", billIdFromLegisNum("H CON RES 14", 119))
        // The House also votes on Senate bills.
        assertEquals("s5-119", billIdFromLegisNum("S 5", 119))
        // Non-bill business.
        assertNull(billIdFromLegisNum("QUORUM", 119))
        assertNull(billIdFromLegisNum("MOTION", 119))
        assertNull(billIdFromLegisNum("", 119))
        assertNull(billIdFromLegisNum(null, 119))
    }

    @Test
    fun houseVote17ParsesToWireShape() {
        val vote = houseVote17()
        assertEquals("house-119-1-17", vote.id)
        assertEquals(119, vote.congress)
        assertEquals(Chamber.HOUSE, vote.chamber)
        assertEquals(1, vote.session)
        assertEquals(17, vote.rollNumber)
        assertEquals("2025-01-16", vote.date)
        assertEquals("On Passage", vote.question)
        assertEquals("Passed", vote.result)
        assertEquals("hr30-119", vote.billId)
        assertEquals(houseVoteSourceUrl(119, 1, 17), vote.sourceUrl)
    }

    @Test
    fun houseVote17TotalsMatchPositionsAndOfficialTally() {
        val vote = houseVote17()
        // Official tally: Passed 274-145, 15 not voting (one seat vacant).
        assertEquals(VoteTotals(yea = 274, nay = 145, present = 0, notVoting = 15), vote.totals)
        assertEquals(434, vote.positions.size)
        val counted = vote.positions.groupingBy { it.position }.eachCount()
        assertEquals(274, counted[VotePosition.YEA])
        assertEquals(145, counted[VotePosition.NAY])
        assertNull(counted[VotePosition.PRESENT])
        assertEquals(15, counted[VotePosition.NOT_VOTING])
    }

    @Test
    fun houseVote17PositionsAreBioguideKeyedAndSorted() {
        val vote = houseVote17()
        val ids = vote.positions.map { it.bioguideId }
        assertEquals(ids.sorted(), ids)
        assertEquals(434, ids.toSet().size)
        // Adams (D-NC), name-id A000370, voted Nay.
        val adams = vote.positions.first { it.bioguideId == "A000370" }
        assertEquals(
            MemberVote(bioguideId = "A000370", position = VotePosition.NAY, party = "D", state = "NC"),
            adams,
        )
    }

    @Test
    fun houseQuorumCallIsANonBillVoteWithPresentPositions() {
        val vote = parseHouseVote(HOUSE_VOTE_QUORUM_XML)
        assertEquals("house-119-1-1", vote.id)
        assertNull(vote.billId)
        assertEquals("Call by States", vote.question)
        // Fixture is trimmed to 3 Present + 1 Not Voting; totals derive
        // from the included positions, not the vote-totals block.
        assertEquals(VoteTotals(yea = 0, nay = 0, present = 3, notVoting = 1), vote.totals)
    }

    @Test
    fun houseSpeakerElectionThrowsUnsupported() {
        val e = assertFailsWith<UnsupportedVoteException> { parseHouseVote(HOUSE_VOTE_SPEAKER_XML) }
        assertTrue(e.message.orEmpty().contains("candidate-ballot"))
    }

    @Test
    fun houseVoteRefPathAndIndexInterop() {
        val ref = buildVoteRef(houseVote17())
        assertEquals("votes/congress119/house-1-17.json", ref.path)
        assertEquals("hr30-119", ref.billId)
    }

    // -------------------------------------------------------- yaml map

    @Test
    fun parseLisToBioguideYamlExtractsSenatorsOnly() {
        val text = """
            |- id:
            |    bioguide: A000382
            |    lis: S428
            |- id:
            |    bioguide: B001234
            |- not-a-dict
            |
        """.trimMargin()
        assertEquals(mapOf("S428" to "A000382"), parseLisToBioguideYaml(text))
    }

    // ----------------------------------------------------- party split

    @Test
    fun buildPartySplitOrdersPositionsAndSortsParties() {
        val positions = listOf(
            MemberVote("A1", VotePosition.NAY, party = "R"),
            MemberVote("A2", VotePosition.YEA, party = "R"),
            MemberVote("A3", VotePosition.YEA, party = "D"),
            MemberVote("A4", VotePosition.YEA, party = "R"),
            MemberVote("A5", VotePosition.NOT_VOTING, party = "I"),
            MemberVote("A6", VotePosition.YEA, party = null), // no party: left out
        )
        val split = buildPartySplit(positions)
        // Position keys in wire order (yea before nay), party keys sorted,
        // "present" absent because no member holds it.
        assertEquals(listOf("yea", "nay", "not_voting"), split.keys.toList())
        assertEquals(mapOf("D" to 1, "R" to 2), split["yea"])
        assertEquals(listOf("D", "R"), split.getValue("yea").keys.toList())
        assertEquals(mapOf("R" to 1), split["nay"])
        assertEquals(mapOf("I" to 1), split["not_voting"])
    }

    @Test
    fun senateVoteRefPartySplitMatchesOfficialBreakdown() {
        val ref = buildVoteRef(vote618())
        assertEquals(
            mapOf(
                "yea" to mapOf("D" to 7, "I" to 1, "R" to 52),
                "nay" to mapOf("D" to 38, "I" to 1, "R" to 1),
            ),
            ref.partySplit,
        )
    }

    @Test
    fun houseVoteRefPartySplitSumsToTotals() {
        val ref = buildVoteRef(houseVote17())
        assertEquals(
            mapOf(
                "yea" to mapOf("D" to 61, "R" to 213),
                "nay" to mapOf("D" to 145),
                "not_voting" to mapOf("D" to 9, "R" to 6),
            ),
            ref.partySplit,
        )
        val totals = mapOf(
            "yea" to ref.totals.yea,
            "nay" to ref.totals.nay,
            "present" to ref.totals.present,
            "not_voting" to ref.totals.notVoting,
        )
        for ((position, byParty) in ref.partySplit) {
            assertEquals(totals[position], byParty.values.sum())
        }
    }

    @Test
    fun partySplitSitsBetweenTotalsAndPathOnTheWire() {
        val text = ManifestJson.encodeToString(VoteRef.serializer(), buildVoteRef(vote618()))
        val totals = text.indexOf("\"totals\"")
        val split = text.indexOf("\"party_split\"")
        val path = text.indexOf("\"path\"")
        assertTrue(totals in 0 until split && split < path)
    }

    // ----------------------------------------------------------- index

    @Test
    fun voteRefDropsPositionsAndCarriesPath() {
        val ref = buildVoteRef(vote618())
        assertEquals("votes/congress119/senate-1-618.json", ref.path)
        assertEquals("hr5371-119", ref.billId)
        assertEquals(60, ref.totals.yea)
    }

    @Test
    fun votesIndexIsNewestFirstWithCount() {
        val newer = buildVoteRef(vote618())
        val older = newer.copy(date = "2025-01-03", rollNumber = 2)
        val index = buildVotesIndex(119, listOf(older, newer), generatedAt = "2026-07-21T00:00:00Z")
        assertEquals("2026-07-21T00:00:00Z", index.generatedAt)
        assertEquals(119, index.congress)
        assertEquals(2, index.voteCount)
        assertEquals(listOf("2025-11-10", "2025-01-03"), index.votes.map { it.date })
    }

    // ---------------------------------------------------- bill linkage

    private fun bill(id: String, votes: List<VoteRef> = emptyList()): Bill = Bill(
        id = id,
        congress = 119,
        type = "hr",
        number = "1",
        title = "A bill",
        sponsor = Sponsor("X", "D", "CA"),
        introducedDate = "2025-01-01",
        latestAction = Action("2025-11-10", "Passed"),
        outcome = Outcome.PASSED_HOUSE,
        congressGovUrl = "https://example.test",
        votes = votes,
    )

    @Test
    fun attachVoteRefsGroupsByBillNewestFirst() {
        val senate = buildVoteRef(vote618()) // hr5371-119, 2025-11-10
        val house = senate.copy(
            id = "house-119-1-2", chamber = Chamber.HOUSE, rollNumber = 2, date = "2025-01-03",
        )
        val quorum = senate.copy(
            id = "house-119-1-1", chamber = Chamber.HOUSE, rollNumber = 1, billId = null,
        )
        val out = attachVoteRefs(
            listOf(bill("hr5371-119"), bill("s42-119")),
            listOf(house, quorum, senate),
        )
        assertEquals(listOf("senate-119-1-618", "house-119-1-2"), out[0].votes.map { it.id })
        // Bills with no roll calls still carry the (empty) field — the
        // publisher's encodeDefaults always emits it.
        assertEquals(emptyList(), out[1].votes)
    }

    @Test
    fun attachVoteRefsRecomputesStaleRefs() {
        val stale = buildVoteRef(vote618()).copy(id = "stale-ref")
        val out = attachVoteRefs(
            listOf(bill("hr5371-119", votes = listOf(stale))),
            listOf(buildVoteRef(vote618())),
        )
        assertEquals(listOf("senate-119-1-618"), out.single().votes.map { it.id })
    }

    @Test
    fun stripVoteRefsClearsOnlyTheDerivedField() {
        val out = stripVoteRefs(
            listOf(bill("hr5371-119", votes = listOf(buildVoteRef(vote618()))), bill("s42-119")),
        )
        assertTrue(out[0].votes.isEmpty())
        assertEquals("A bill", out[0].title)
        assertEquals("hr5371-119", out[0].id)
        assertTrue(out[1].votes.isEmpty())
    }
}
