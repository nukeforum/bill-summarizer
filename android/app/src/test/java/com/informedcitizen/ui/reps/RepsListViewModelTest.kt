package com.informedcitizen.ui.reps

import com.informedcitizen.data.model.Member
import com.informedcitizen.data.model.MemberLegislation
import com.informedcitizen.data.model.MembersIndex
import com.informedcitizen.data.repository.LocationPreferenceRepository
import com.informedcitizen.data.repository.MemberRepository
import com.informedcitizen.data.repository.RepsForLocation
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

private fun aMember(bid: String) =
    Member(bid, "Name $bid", "D", "TX", 21, "house", null, null, 1, 1, null, null)

@OptIn(ExperimentalCoroutinesApi::class)
class RepsListViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newPrefsRepo() = LocationPreferenceRepository(InMemoryPreferencesDataStore())

    @Test
    fun `emits NoLocation when state code is null`() = runTest {
        val prefs = newPrefsRepo()  // default: no location
        val members = StubMemberRepository()
        val vm = RepsListViewModel(prefs, members).also { it.congressProvider = { 119 } }
        // Initial state is Loading; once flow emits the SavedLocation(null), we expect NoLocation.
        val firstNonLoading = vm.uiState.first { it !is RepsListUiState.Loading }
        assertEquals(RepsListUiState.NoLocation, firstNonLoading)
    }

    @Test
    fun `loads reps when location is saved`() = runTest {
        val prefs = newPrefsRepo()
        prefs.set(stateCode = "TX", district = 21)
        val members = StubMemberRepository(
            RepsForLocation(
                house = listOf(aMember("A1")),
                senators = listOf(aMember("S1"), aMember("S2")),
            ),
        )
        val vm = RepsListViewModel(prefs, members).also { it.congressProvider = { 119 } }
        val loaded = vm.uiState.first { it is RepsListUiState.Loaded } as RepsListUiState.Loaded
        assertEquals(listOf("A1"), loaded.house.map { it.bioguideId })
        assertEquals(setOf("S1", "S2"), loaded.senators.map { it.bioguideId }.toSet())
    }

    @Test
    fun `emits StaleDistrict when saved district has no house match`() = runTest {
        val prefs = newPrefsRepo()
        prefs.set(stateCode = "CA", district = 99)  // Hypothetical post-redistricting non-existent district
        val members = StubMemberRepository(
            RepsForLocation(
                house = emptyList(),  // No match for CA-99
                senators = listOf(aMember("S1")),  // Senators still found
            ),
        )
        val vm = RepsListViewModel(prefs, members).also { it.congressProvider = { 119 } }
        val s = vm.uiState.first { it is RepsListUiState.StaleDistrict } as RepsListUiState.StaleDistrict
        assertEquals("CA", s.stateCode)
        assertEquals(99, s.district)
    }

    @Test
    fun `still emits Loaded when district is null and house is empty`() = runTest {
        // E.g., user picked DC (delegate-only) — house may be empty by design.
        val prefs = newPrefsRepo()
        prefs.set(stateCode = "DC", district = null)
        val members = StubMemberRepository(
            RepsForLocation(house = emptyList(), senators = emptyList()),
        )
        val vm = RepsListViewModel(prefs, members).also { it.congressProvider = { 119 } }
        val s = vm.uiState.first { it is RepsListUiState.Loaded || it is RepsListUiState.StaleDistrict }
        // Expect Loaded, not StaleDistrict — the user didn't save a district.
        assertEquals(RepsListUiState.Loaded(emptyList(), emptyList()), s)
    }

    @Test
    fun `emits Error when repository throws`() = runTest {
        val prefs = newPrefsRepo()
        prefs.set(stateCode = "TX", district = 21)
        val members = StubMemberRepository().also { it.setError(RuntimeException("boom")) }
        val vm = RepsListViewModel(prefs, members).also { it.congressProvider = { 119 } }
        val err = vm.uiState.first { it is RepsListUiState.Error } as RepsListUiState.Error
        assertTrue("error message contains boom", err.message.contains("boom"))
    }
}
