package com.informedcitizen.ui.reps

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.CongressEntry
import com.informedcitizen.pipeline.model.CongressesIndex
import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.pipeline.model.MemberLegislation
import com.informedcitizen.pipeline.model.MemberLegislationItem
import com.informedcitizen.pipeline.model.MemberVoteRow
import com.informedcitizen.pipeline.model.MemberVotes
import com.informedcitizen.pipeline.model.MembersIndex
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.VotePosition
import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.ElectionEvent
import com.informedcitizen.pipeline.model.ElectionType
import com.informedcitizen.pipeline.model.SessionCalendar
import com.informedcitizen.pipeline.model.Sponsor
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.ElectionCalendarRepository
import com.informedcitizen.data.repository.MemberRepository
import com.informedcitizen.data.repository.RepsForLocation
import com.informedcitizen.testutil.FakeBillCache
import com.informedcitizen.testutil.FakeElectionCalendarCache
import com.informedcitizen.testutil.InMemoryPreferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class StubBillsApi(
    private val bills: List<Bill>,
    private val election: ElectionCalendar? = null,
) : BillsApi {
    override suspend fun getCongressesIndex(): CongressesIndex = CongressesIndex(
        currentCongress = 119,
        congresses = listOf(CongressEntry(congress = 119, manifestPath = "congress119_bills.json", isCurrent = true)),
    )
    override suspend fun getBillsManifest(url: String): BillsManifest =
        BillsManifest(generatedAt = "x", congress = 119, bills = bills)
    override suspend fun getSessionCalendar(): SessionCalendar = error("not used")
    override suspend fun getElectionCalendar(): ElectionCalendar = election ?: error("no calendar")
}

private fun electionRepo(election: ElectionCalendar? = null) =
    ElectionCalendarRepository(StubBillsApi(emptyList(), election), FakeCrashReporter(), FakeElectionCalendarCache())

private class DetailStubMemberRepository(
    private val memberById: Map<String, Member?> = emptyMap(),
    private val sponsoredById: Map<String, MemberLegislation?> = emptyMap(),
    private val cosponsoredById: Map<String, MemberLegislation?> = emptyMap(),
    private val votesById: Map<String, MemberVotes?> = emptyMap(),
) : MemberRepository {
    override suspend fun findRepsForLocation(
        congress: Int,
        stateCode: String,
        district: Int?,
    ): RepsForLocation = error("unused")
    override suspend fun findRepsByIds(
        congress: Int,
        bioguideIds: Set<String>,
    ): RepsForLocation = error("unused")
    override suspend fun getMember(bioguideId: String, congress: Int): Member? =
        memberById[bioguideId]
    override suspend fun getSponsored(bioguideId: String): MemberLegislation? =
        sponsoredById.getOrDefault(bioguideId, MemberLegislation(bioguideId, 119, "sponsored", "x", emptyList()))
    override suspend fun getCosponsored(bioguideId: String): MemberLegislation? =
        cosponsoredById.getOrDefault(bioguideId, MemberLegislation(bioguideId, 119, "cosponsored", "x", emptyList()))
    override suspend fun getVotes(bioguideId: String): MemberVotes? =
        votesById.getOrDefault(bioguideId, MemberVotes("x", bioguideId, 0, emptyList()))
    override suspend fun getIndex(congress: Int): MembersIndex? = null
}

private fun aVoteRow(
    voteId: String,
    billId: String? = "hr1-119",
    position: VotePosition = VotePosition.YEA,
) = MemberVoteRow(
    voteId = voteId,
    congress = 119,
    date = "2026-07-22",
    question = "On Passage",
    result = "Passed",
    position = position,
    billId = billId,
    type = billId?.let { "hr" },
    number = billId?.let { "1" },
    shortTitle = billId?.let { "Sample Act" },
)

private fun anItem(
    id: String,
    title: String = "Title $id",
    policyArea: String? = null,
) = MemberLegislationItem(
    id = id,
    type = "hr",
    number = id.removePrefix("hr").substringBefore("-"),
    congress = 119,
    title = title,
    introducedDate = "2026-01-01",
    latestAction = Action(date = "2026-04-01", text = "Referred."),
    policyArea = policyArea,
)

private fun aBill(id: String) = Bill(
    id = id,
    congress = 119,
    type = "hr",
    number = id.removePrefix("hr").substringBefore("-"),
    title = "Sample",
    sponsor = Sponsor(name = "Test", party = "D", state = "CA"),
    introducedDate = "2026-01-01",
    latestAction = Action(date = "2026-01-02", text = "Test"),
    outcome = Outcome.PASSED_HOUSE,
    congressGovUrl = "https://example.com/$id",
)

private fun aMember(bid: String) =
    Member(bid, "Name $bid", "D", "TX", 21, "house", null, null, 1, 1, null, null)

@OptIn(ExperimentalCoroutinesApi::class)
class MemberDetailViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `load fills member sponsored cosponsored`() = runTest {
        val members = DetailStubMemberRepository(
            memberById = mapOf("A1" to aMember("A1")),
            sponsoredById = mapOf("A1" to MemberLegislation("A1", 119, "sponsored", "x", listOf(anItem("hr1-119")))),
            cosponsoredById = mapOf("A1" to MemberLegislation("A1", 119, "cosponsored", "x", listOf(anItem("hr2-119"), anItem("hr3-119")))),
        )
        val bills = BillRepository(StubBillsApi(emptyList()), InMemoryPreferencesDataStore(), FakeCrashReporter(), FakeBillCache())
        val vm = MemberDetailViewModel(members, bills, electionRepo()).also { it.congressProvider = { 119 } }
        vm.load("A1")
        val s = vm.uiState.first { !it.isLoading }
        assertNotNull(s.member)
        assertEquals(listOf("hr1-119"), s.sponsored.map { it.id })
        assertEquals(listOf("hr2-119", "hr3-119"), s.cosponsored.map { it.id })
        assertNull(s.errorMessage)
    }

    @Test
    fun `load empty lists when repository returns null`() = runTest {
        val members = DetailStubMemberRepository(
            memberById = mapOf("A1" to aMember("A1")),
            sponsoredById = mapOf("A1" to null),
            cosponsoredById = mapOf("A1" to null),
        )
        val bills = BillRepository(StubBillsApi(emptyList()), InMemoryPreferencesDataStore(), FakeCrashReporter(), FakeBillCache())
        val vm = MemberDetailViewModel(members, bills, electionRepo()).also { it.congressProvider = { 119 } }
        vm.load("A1")
        val s = vm.uiState.first { !it.isLoading }
        assertNotNull(s.member)
        assertEquals(emptyList<MemberLegislationItem>(), s.sponsored)
        assertEquals(emptyList<MemberLegislationItem>(), s.cosponsored)
    }

    @Test
    fun `search query filters sponsored and cosponsored lists`() = runTest {
        val members = DetailStubMemberRepository(
            memberById = mapOf("A1" to aMember("A1")),
            sponsoredById = mapOf(
                "A1" to MemberLegislation(
                    "A1", 119, "sponsored", "x",
                    listOf(
                        anItem("hr1-119", title = "Student Loan Relief Act"),
                        anItem("hr2-119", title = "Defense Authorization"),
                        anItem("hr3-119", title = "Post Office Naming", policyArea = "Education"),
                    ),
                ),
            ),
            cosponsoredById = mapOf(
                "A1" to MemberLegislation(
                    "A1", 119, "cosponsored", "x",
                    listOf(anItem("hr4-119", title = "Water Rights")),
                ),
            ),
        )
        val bills = BillRepository(StubBillsApi(emptyList()), InMemoryPreferencesDataStore(), FakeCrashReporter(), FakeBillCache())
        val vm = MemberDetailViewModel(members, bills, electionRepo()).also { it.congressProvider = { 119 } }
        vm.load("A1")
        vm.uiState.first { !it.isLoading }

        vm.setSearchQuery("education")
        val filtered = vm.uiState.value
        assertEquals(listOf("hr3-119"), filtered.filteredSponsored.map { it.id })
        assertEquals(emptyList<MemberLegislationItem>(), filtered.filteredCosponsored)
        // The raw lists stay intact so clearing the query restores everything.
        assertEquals(3, filtered.sponsored.size)

        vm.setSearchQuery("")
        val cleared = vm.uiState.value
        assertEquals(3, cleared.filteredSponsored.size)
        assertEquals(1, cleared.filteredCosponsored.size)
    }

    @Test
    fun `load surfaces recent votes newest first`() = runTest {
        val members = DetailStubMemberRepository(
            memberById = mapOf("A1" to aMember("A1")),
            votesById = mapOf(
                "A1" to MemberVotes(
                    "x", "A1", 2,
                    listOf(aVoteRow("v2"), aVoteRow("v1", position = VotePosition.NAY)),
                ),
            ),
        )
        val bills = BillRepository(StubBillsApi(emptyList()), InMemoryPreferencesDataStore(), FakeCrashReporter(), FakeBillCache())
        val vm = MemberDetailViewModel(members, bills, electionRepo()).also { it.congressProvider = { 119 } }
        vm.load("A1")
        val s = vm.uiState.first { !it.isLoading }
        assertEquals(listOf("v2", "v1"), s.recentVotes.map { it.voteId })
    }

    @Test
    fun `load caps recent votes at 25`() = runTest {
        val rows = (1..40).map { aVoteRow("v$it") }
        val members = DetailStubMemberRepository(
            memberById = mapOf("A1" to aMember("A1")),
            votesById = mapOf("A1" to MemberVotes("x", "A1", rows.size, rows)),
        )
        val bills = BillRepository(StubBillsApi(emptyList()), InMemoryPreferencesDataStore(), FakeCrashReporter(), FakeBillCache())
        val vm = MemberDetailViewModel(members, bills, electionRepo()).also { it.congressProvider = { 119 } }
        vm.load("A1")
        val s = vm.uiState.first { !it.isLoading }
        assertEquals(25, s.recentVotes.size)
        assertEquals("v1", s.recentVotes.first().voteId)
    }

    @Test
    fun `load leaves votes empty when member has none`() = runTest {
        val members = DetailStubMemberRepository(
            memberById = mapOf("A1" to aMember("A1")),
            votesById = mapOf("A1" to null),
        )
        val bills = BillRepository(StubBillsApi(emptyList()), InMemoryPreferencesDataStore(), FakeCrashReporter(), FakeBillCache())
        val vm = MemberDetailViewModel(members, bills, electionRepo()).also { it.congressProvider = { 119 } }
        vm.load("A1")
        val s = vm.uiState.first { !it.isLoading }
        assertTrue(s.recentVotes.isEmpty())
    }

    @Test
    fun `isInLocalCache reflects bill repository`() = runTest {
        val members = DetailStubMemberRepository(
            memberById = mapOf("A1" to aMember("A1")),
            sponsoredById = mapOf("A1" to MemberLegislation("A1", 119, "sponsored", "x", listOf(anItem("hr1-119")))),
        )
        // BillRepository must be loaded first so its cached list is populated.
        val bills = BillRepository(
            StubBillsApi(listOf(aBill("hr1-119"))),
            InMemoryPreferencesDataStore(),
            FakeCrashReporter(),
            FakeBillCache(),
        )
        val getResult = bills.getBills(forceRefresh = true)
        assertTrue(getResult.isSuccess)

        val vm = MemberDetailViewModel(members, bills, electionRepo()).also { it.congressProvider = { 119 } }
        vm.load("A1")
        vm.uiState.first { !it.isLoading }
        assertTrue(vm.isInLocalCache("hr1-119"))
        assertFalse(vm.isInLocalCache("hr999-119"))
    }

    @Test
    fun `load fills the up-for-election badge when the member faces an upcoming general`() = runTest {
        val onBallot = Member("A1", "Name A1", "D", "TX", 21, "house", null, null, 1, 1, null, null, nextElectionYear = 2026)
        val members = DetailStubMemberRepository(memberById = mapOf("A1" to onBallot))
        val bills = BillRepository(StubBillsApi(emptyList()), InMemoryPreferencesDataStore(), FakeCrashReporter(), FakeBillCache())
        val calendar = ElectionCalendar(
            generatedAt = "2026-01-01T00:00:00Z",
            source = "test",
            elections = listOf(ElectionEvent(ElectionEvent.NATIONWIDE, "2026-11-03", ElectionType.GENERAL, 2026)),
        )
        val vm = MemberDetailViewModel(members, bills, electionRepo(calendar)).also {
            it.congressProvider = { 119 }
            it.todayProvider = { LocalDate.of(2026, 6, 1) }
        }
        vm.load("A1")
        val s = vm.uiState.first { !it.isLoading }
        assertEquals("Up for election · Nov 3, 2026", s.ballotBadge)
    }

    @Test
    fun `load leaves the ballot badge null when the election calendar is unavailable`() = runTest {
        val onBallot = Member("A1", "Name A1", "D", "TX", 21, "house", null, null, 1, 1, null, null, nextElectionYear = 2026)
        val members = DetailStubMemberRepository(memberById = mapOf("A1" to onBallot))
        val bills = BillRepository(StubBillsApi(emptyList()), InMemoryPreferencesDataStore(), FakeCrashReporter(), FakeBillCache())
        // electionRepo(null) → getElectionCalendar errors → null calendar → no badge.
        val vm = MemberDetailViewModel(members, bills, electionRepo(null)).also { it.congressProvider = { 119 } }
        vm.load("A1")
        val s = vm.uiState.first { !it.isLoading }
        assertNull(s.ballotBadge)
    }
}
