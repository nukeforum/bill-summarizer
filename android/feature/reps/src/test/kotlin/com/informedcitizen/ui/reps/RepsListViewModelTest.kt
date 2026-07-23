package com.informedcitizen.ui.reps

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.CongressesIndex
import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.ElectionEvent
import com.informedcitizen.pipeline.model.ElectionType
import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.pipeline.model.MemberLegislation
import com.informedcitizen.pipeline.model.MembersIndex
import com.informedcitizen.pipeline.model.SessionCalendar
import com.informedcitizen.data.repository.ElectionCalendarRepository
import com.informedcitizen.data.repository.MemberRepository
import com.informedcitizen.data.repository.RepsForLocation
import com.informedcitizen.data.repository.SavedRepsRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class StubMemberRepository(
    private var nextResult: RepsForLocation = RepsForLocation(emptyList(), emptyList()),
    private var throwOnNext: Throwable? = null,
) : MemberRepository {
    override suspend fun findRepsForLocation(
        congress: Int,
        stateCode: String,
        district: Int?,
    ): RepsForLocation = error("unused in this VM")

    override suspend fun findRepsByIds(
        congress: Int,
        bioguideIds: Set<String>,
    ): RepsForLocation {
        throwOnNext?.let { throw it }
        return nextResult
    }

    override suspend fun getMember(bioguideId: String, congress: Int): Member? = null
    override suspend fun getSponsored(bioguideId: String): MemberLegislation? = null
    override suspend fun getCosponsored(bioguideId: String): MemberLegislation? = null
    override suspend fun getVotes(bioguideId: String): com.informedcitizen.pipeline.model.MemberVotes? = null
    override suspend fun getIndex(congress: Int): MembersIndex? = null

    fun setResult(result: RepsForLocation) { nextResult = result }
    fun setError(t: Throwable) { throwOnNext = t }
}

private fun aMember(bid: String, chamber: String = "house", nextElectionYear: Int? = null) =
    Member(bid, "Name $bid", "D", "TX", 21, chamber, null, null, 1, 1, null, null, nextElectionYear = nextElectionYear)

/** BillsApi stub serving a fixed election calendar (all other endpoints unused). */
private class StubElectionApi(private val election: ElectionCalendar?) : BillsApi {
    override suspend fun getCongressesIndex(): CongressesIndex = error("unused")
    override suspend fun getBillsManifest(url: String): BillsManifest = error("unused")
    override suspend fun getSessionCalendar(): SessionCalendar = error("unused")
    override suspend fun getElectionCalendar(): ElectionCalendar = election ?: error("no calendar")
}

private fun electionRepo(election: ElectionCalendar? = null) =
    ElectionCalendarRepository(StubElectionApi(election), FakeCrashReporter(), FakeElectionCalendarCache())

@OptIn(ExperimentalCoroutinesApi::class)
class RepsListViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newPrefsRepo() = SavedRepsRepository(InMemoryPreferencesDataStore())

    @Test
    fun `emits NoLocation when no ids saved`() = runTest {
        val prefs = newPrefsRepo()  // default: empty
        val members = StubMemberRepository()
        val vm = RepsListViewModel(prefs, members, electionRepo()).also { it.congressProvider = { 119 } }
        val firstNonLoading = vm.uiState.first { it !is RepsListUiState.Loading }
        assertEquals(RepsListUiState.NoLocation, firstNonLoading)
    }

    @Test
    fun `loads reps when ids resolve in index`() = runTest {
        val prefs = newPrefsRepo()
        prefs.set(setOf("H1", "S1", "S2"))
        val members = StubMemberRepository(
            RepsForLocation(
                house = listOf(aMember("H1", "house")),
                senators = listOf(aMember("S1", "senate"), aMember("S2", "senate")),
            ),
        )
        val vm = RepsListViewModel(prefs, members, electionRepo()).also { it.congressProvider = { 119 } }
        val loaded = vm.uiState.first { it is RepsListUiState.Loaded } as RepsListUiState.Loaded
        assertEquals(listOf("H1"), loaded.house.map { it.bioguideId })
        assertEquals(setOf("S1", "S2"), loaded.senators.map { it.bioguideId }.toSet())
    }

    @Test
    fun `emits StaleSavedReps when any saved id is missing from index`() = runTest {
        val prefs = newPrefsRepo()
        prefs.set(setOf("H1", "S1", "S2"))
        // Index only resolves 2 of the 3 saved ids — H1 retired or redistricted.
        val members = StubMemberRepository(
            RepsForLocation(
                house = emptyList(),
                senators = listOf(aMember("S1", "senate"), aMember("S2", "senate")),
            ),
        )
        val vm = RepsListViewModel(prefs, members, electionRepo()).also { it.congressProvider = { 119 } }
        val s = vm.uiState.first { it is RepsListUiState.StaleSavedReps || it is RepsListUiState.Loaded }
        assertEquals(RepsListUiState.StaleSavedReps, s)
    }

    @Test
    fun `delegate save with single id resolves cleanly`() = runTest {
        // DC delegate has no senators — saving only the delegate's bioguide id is valid.
        val prefs = newPrefsRepo()
        prefs.set(setOf("D1"))
        val members = StubMemberRepository(
            RepsForLocation(
                house = listOf(aMember("D1", "house")),
                senators = emptyList(),
            ),
        )
        val vm = RepsListViewModel(prefs, members, electionRepo()).also { it.congressProvider = { 119 } }
        val s = vm.uiState.first { it is RepsListUiState.Loaded } as RepsListUiState.Loaded
        assertEquals(listOf("D1"), s.house.map { it.bioguideId })
        assertTrue(s.senators.isEmpty())
    }

    @Test
    fun `deleteSavedReps clears saved ids and transitions to NoLocation`() = runTest {
        val prefs = newPrefsRepo()
        prefs.set(setOf("H1", "S1"))
        val members = StubMemberRepository(
            RepsForLocation(
                house = listOf(aMember("H1", "house")),
                senators = listOf(aMember("S1", "senate")),
            ),
        )
        val vm = RepsListViewModel(prefs, members, electionRepo()).also { it.congressProvider = { 119 } }
        vm.uiState.first { it is RepsListUiState.Loaded }

        vm.deleteSavedReps()

        val after = vm.uiState.first { it == RepsListUiState.NoLocation }
        assertEquals(RepsListUiState.NoLocation, after)
        assertTrue(prefs.savedIds.first().isEmpty())
    }

    @Test
    fun `emits Error when repository throws`() = runTest {
        val prefs = newPrefsRepo()
        prefs.set(setOf("H1"))
        val members = StubMemberRepository().also { it.setError(RuntimeException("boom")) }
        val vm = RepsListViewModel(prefs, members, electionRepo()).also { it.congressProvider = { 119 } }
        val err = vm.uiState.first { it is RepsListUiState.Error } as RepsListUiState.Error
        assertTrue("error message contains boom", err.message.contains("boom"))
    }

    @Test
    fun `Loaded carries an up-for-election badge only for reps matching an upcoming general`() = runTest {
        val prefs = newPrefsRepo()
        prefs.set(setOf("H1", "S1"))
        val members = StubMemberRepository(
            RepsForLocation(
                // H1 faces the 2026 general; S1 (a senator mid-term) has no next-election year.
                house = listOf(aMember("H1", "house", nextElectionYear = 2026)),
                senators = listOf(aMember("S1", "senate", nextElectionYear = null)),
            ),
        )
        val calendar = ElectionCalendar(
            generatedAt = "2026-01-01T00:00:00Z",
            source = "test",
            elections = listOf(
                ElectionEvent(ElectionEvent.NATIONWIDE, "2026-11-03", ElectionType.GENERAL, 2026),
            ),
        )
        val vm = RepsListViewModel(prefs, members, electionRepo(calendar)).also {
            it.congressProvider = { 119 }
            it.todayProvider = { LocalDate.of(2026, 6, 1) }
        }
        val loaded = vm.uiState.first { it is RepsListUiState.Loaded } as RepsListUiState.Loaded
        assertEquals("Up for election · Nov 3, 2026", loaded.ballotBadges["H1"])
        assertTrue("senator with no next-election year gets no badge", "S1" !in loaded.ballotBadges)
    }

    @Test
    fun `Loaded has no badges when the election calendar is unavailable`() = runTest {
        val prefs = newPrefsRepo()
        prefs.set(setOf("H1"))
        val members = StubMemberRepository(
            RepsForLocation(
                house = listOf(aMember("H1", "house", nextElectionYear = 2026)),
                senators = emptyList(),
            ),
        )
        // electionRepo(null) → getElectionCalendar errors → calendar null → no badges.
        val vm = RepsListViewModel(prefs, members, electionRepo(null)).also { it.congressProvider = { 119 } }
        val loaded = vm.uiState.first { it is RepsListUiState.Loaded } as RepsListUiState.Loaded
        assertTrue("no badges without a calendar", loaded.ballotBadges.isEmpty())
    }

}
