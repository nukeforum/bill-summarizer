package com.informedcitizen.ui.reps

import com.informedcitizen.data.model.Member
import com.informedcitizen.data.model.MemberLegislation
import com.informedcitizen.data.model.MembersIndex
import com.informedcitizen.data.repository.MemberRepository
import com.informedcitizen.data.repository.RepsForLocation
import com.informedcitizen.data.repository.SavedRepsRepository
import com.informedcitizen.testutil.InMemoryPreferencesDataStore
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
    override suspend fun getIndex(congress: Int): MembersIndex? = null

    fun setResult(result: RepsForLocation) { nextResult = result }
    fun setError(t: Throwable) { throwOnNext = t }
}

private fun aMember(bid: String, chamber: String = "house") =
    Member(bid, "Name $bid", "D", "TX", 21, chamber, null, null, 1, 1, null, null)

@OptIn(ExperimentalCoroutinesApi::class)
class RepsListViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newPrefsRepo() = SavedRepsRepository(InMemoryPreferencesDataStore())

    @Test
    fun `emits NoLocation when no ids saved`() = runTest {
        val prefs = newPrefsRepo()  // default: empty
        val members = StubMemberRepository()
        val vm = RepsListViewModel(prefs, members).also { it.congressProvider = { 119 } }
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
        val vm = RepsListViewModel(prefs, members).also { it.congressProvider = { 119 } }
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
        val vm = RepsListViewModel(prefs, members).also { it.congressProvider = { 119 } }
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
        val vm = RepsListViewModel(prefs, members).also { it.congressProvider = { 119 } }
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
        val vm = RepsListViewModel(prefs, members).also { it.congressProvider = { 119 } }
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
        val vm = RepsListViewModel(prefs, members).also { it.congressProvider = { 119 } }
        val err = vm.uiState.first { it is RepsListUiState.Error } as RepsListUiState.Error
        assertTrue("error message contains boom", err.message.contains("boom"))
    }
}
