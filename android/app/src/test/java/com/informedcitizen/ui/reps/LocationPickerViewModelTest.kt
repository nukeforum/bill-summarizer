package com.informedcitizen.ui.reps

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.api.MembersApi
import com.informedcitizen.data.model.Member
import com.informedcitizen.data.model.MemberLegislation
import com.informedcitizen.data.model.MembersIndex
import com.informedcitizen.data.repository.LocationPreferenceRepository
import com.informedcitizen.data.repository.MemberRepository
import com.informedcitizen.data.zipcrosswalk.ZipDistrictLookup
import com.informedcitizen.data.zipcrosswalk.ZipDistrictResult
import com.informedcitizen.testutil.InMemoryPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class StubZipLookup(
    var nextResult: ZipDistrictResult = ZipDistrictResult.Miss,
    private val available: Boolean = true,
) : ZipDistrictLookup(loader = { "" }) {
    override suspend fun lookup(zip: String): ZipDistrictResult = nextResult
    override suspend fun isAvailable(): Boolean = available
}

private class PickerStubMemberRepository(
    private val index: MembersIndex? = null,
) : MemberRepository(api = PickerNoopMembersApi(), crashReporter = FakeCrashReporter()) {
    override suspend fun getIndex(congress: Int): MembersIndex? = index
}

private class PickerNoopMembersApi : MembersApi {
    override suspend fun getMembersIndex(congress: String): MembersIndex = error("unused")
    override suspend fun getSponsored(bioguideId: String): MemberLegislation = error("unused")
    override suspend fun getCosponsored(bioguideId: String): MemberLegislation = error("unused")
}

@OptIn(ExperimentalCoroutinesApi::class)
class LocationPickerViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newRepo() = LocationPreferenceRepository(InMemoryPreferencesDataStore())
    private fun newMembers(index: MembersIndex? = null) = PickerStubMemberRepository(index)

    @Test
    fun `selecting a state populates districts`() = runTest {
        val repo = newRepo()
        val vm = LocationPickerViewModel(repo, StubZipLookup(), newMembers())
        vm.selectState("TX")
        val s = vm.uiState.first()
        assertEquals("TX", s.selectedState)
        assertTrue(s.districtsForState.isNotEmpty())
        assertFalse(s.isAtLargeOrDelegate)
        assertFalse(s.canSave)  // need a district pick before save
    }

    @Test
    fun `selecting an at-large state marks atLarge and enables save`() = runTest {
        val vm = LocationPickerViewModel(newRepo(), StubZipLookup(), newMembers())
        vm.selectState("VT")
        val s = vm.uiState.first()
        assertTrue(s.isAtLargeOrDelegate)
        assertTrue(s.canSave)
    }

    @Test
    fun `selecting a delegate jurisdiction marks atLarge`() = runTest {
        val vm = LocationPickerViewModel(newRepo(), StubZipLookup(), newMembers())
        vm.selectState("DC")
        assertTrue(vm.uiState.first().isAtLargeOrDelegate)
    }

    @Test
    fun `selectDistrict enables save when state is set`() = runTest {
        val vm = LocationPickerViewModel(newRepo(), StubZipLookup(), newMembers())
        vm.selectState("TX")
        vm.selectDistrict(21)
        assertTrue(vm.uiState.first().canSave)
    }

    @Test
    fun `zip lookup single sets state and district hint`() = runTest {
        val zip = StubZipLookup(nextResult = ZipDistrictResult.Single("NY", 12))
        val vm = LocationPickerViewModel(newRepo(), zip, newMembers())
        vm.onZipChanged("10001")
        vm.lookupZip()
        val s = vm.uiState.first()
        assertEquals("NY", s.selectedState)
        assertEquals(DistrictHint.Single(12), s.districtHint)
        assertTrue(s.canSave)
    }

    @Test
    fun `zip lookup multiple sets candidates without enabling save`() = runTest {
        val zip = StubZipLookup(nextResult = ZipDistrictResult.Multiple("TX", listOf(21, 25, 35)))
        val vm = LocationPickerViewModel(newRepo(), zip, newMembers())
        vm.onZipChanged("78701")
        vm.lookupZip()
        val s = vm.uiState.first()
        assertEquals("TX", s.selectedState)
        assertEquals(DistrictHint.Multiple(listOf(21, 25, 35)), s.districtHint)
        assertFalse(s.canSave)
    }

    @Test
    fun `zip miss shows Miss hint`() = runTest {
        val zip = StubZipLookup(nextResult = ZipDistrictResult.Miss)
        val vm = LocationPickerViewModel(newRepo(), zip, newMembers())
        vm.onZipChanged("00000")
        vm.lookupZip()
        assertEquals(DistrictHint.Miss, vm.uiState.first().districtHint)
    }

    @Test
    fun `save writes to prefs`() = runTest {
        val repo = newRepo()
        val vm = LocationPickerViewModel(repo, StubZipLookup(), newMembers())
        vm.selectState("TX")
        vm.selectDistrict(21)
        vm.save()
        val saved = repo.location.first()
        assertEquals("TX", saved.stateCode)
        assertEquals(21, saved.district)
    }

    @Test
    fun `zip lookup hidden when asset unavailable`() = runTest {
        val vm = LocationPickerViewModel(newRepo(), StubZipLookup(available = false), newMembers())
        // The flag defaults to true; wait until the init coroutine flips it.
        val s = vm.uiState.first { !it.isZipLookupAvailable }
        assertFalse(s.isZipLookupAvailable)
    }

    @Test
    fun `save at-large state writes district 0`() = runTest {
        val repo = newRepo()
        val vm = LocationPickerViewModel(repo, StubZipLookup(), newMembers())
        vm.selectState("DC")
        vm.save()
        val saved = repo.location.first()
        assertEquals("DC", saved.stateCode)
        assertEquals(0, saved.district)
    }

    @Test
    fun `state districts come from loaded members index when available`() = runTest {
        // Simulated NE delegation: only 2 districts in the index even though
        // the hardcoded fallback says 3, proving the live index wins.
        val index = MembersIndex(
            congress = 119,
            generatedAt = "x",
            members = listOf(
                Member("M1", "M1", "R", "NE", 1, "house", null, null, 0, 0, null, null),
                Member("M2", "M2", "D", "NE", 2, "house", null, null, 0, 0, null, null),
            ),
        )
        val vm = LocationPickerViewModel(newRepo(), StubZipLookup(), newMembers(index))
        advanceUntilIdle()
        vm.selectState("NE")
        val s = vm.uiState.first()
        assertEquals(listOf(1, 2), s.districtsForState)
    }

    @Test
    fun `falls back to hardcoded counts when index unavailable`() = runTest {
        // getIndex returns null → fallback path uses HOUSE_DISTRICT_COUNTS.
        val vm = LocationPickerViewModel(newRepo(), StubZipLookup(), newMembers(index = null))
        advanceUntilIdle()
        vm.selectState("TX")
        val s = vm.uiState.first()
        assertEquals(38, s.districtsForState.size)  // 119th Congress: 38 districts
    }
}
