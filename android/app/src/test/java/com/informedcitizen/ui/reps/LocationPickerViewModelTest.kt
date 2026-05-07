package com.informedcitizen.ui.reps

import com.informedcitizen.data.model.Member
import com.informedcitizen.data.model.MemberLegislation
import com.informedcitizen.data.model.MembersIndex
import com.informedcitizen.data.repository.MemberRepository
import com.informedcitizen.data.repository.RepsForLocation
import com.informedcitizen.data.repository.SavedRepsRepository
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
) : ZipDistrictLookup {
    override suspend fun lookup(zip: String): ZipDistrictResult = nextResult
    override suspend fun isAvailable(): Boolean = available
}

private class PickerStubMemberRepository(
    private val index: MembersIndex? = null,
    private var resolvedReps: RepsForLocation = RepsForLocation(emptyList(), emptyList()),
) : MemberRepository {
    override suspend fun findRepsForLocation(
        congress: Int,
        stateCode: String,
        district: Int?,
    ): RepsForLocation = resolvedReps

    override suspend fun findRepsByIds(
        congress: Int,
        bioguideIds: Set<String>,
    ): RepsForLocation = error("unused")

    override suspend fun getMember(bioguideId: String, congress: Int): Member? = null
    override suspend fun getSponsored(bioguideId: String): MemberLegislation? = null
    override suspend fun getCosponsored(bioguideId: String): MemberLegislation? = null
    override suspend fun getIndex(congress: Int): MembersIndex? = index

    fun setResolved(reps: RepsForLocation) { resolvedReps = reps }
}

private fun aMember(bid: String, chamber: String = "house") =
    Member(bid, "Name $bid", "D", "TX", 21, chamber, null, null, 1, 1, null, null)

@OptIn(ExperimentalCoroutinesApi::class)
class LocationPickerViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newRepo() = SavedRepsRepository(InMemoryPreferencesDataStore())
    private fun newMembers(
        index: MembersIndex? = null,
        resolved: RepsForLocation = RepsForLocation(emptyList(), emptyList()),
    ) = PickerStubMemberRepository(index, resolved)

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
    fun `save writes resolved bioguide ids`() = runTest {
        val repo = newRepo()
        val resolved = RepsForLocation(
            house = listOf(aMember("H1", "house")),
            senators = listOf(aMember("S1", "senate"), aMember("S2", "senate")),
        )
        val vm = LocationPickerViewModel(repo, StubZipLookup(), newMembers(resolved = resolved))
        vm.selectState("TX")
        vm.selectDistrict(21)
        vm.save()
        assertEquals(setOf("H1", "S1", "S2"), repo.savedIds.first())
    }

    @Test
    fun `save with empty resolution surfaces SaveFailed and writes nothing`() = runTest {
        val repo = newRepo()
        val vm = LocationPickerViewModel(repo, StubZipLookup(), newMembers(/* resolved=empty */))
        vm.selectState("TX")
        vm.selectDistrict(21)
        vm.save()
        assertTrue(repo.savedIds.first().isEmpty())
        assertEquals(DistrictHint.SaveFailed, vm.uiState.first().districtHint)
    }

    @Test
    fun `save emits Saved event after persisting`() = runTest {
        val repo = newRepo()
        val resolved = RepsForLocation(
            house = listOf(aMember("H1", "house")),
            senators = emptyList(),
        )
        val vm = LocationPickerViewModel(repo, StubZipLookup(), newMembers(resolved = resolved))
        vm.selectState("DC")  // delegate, atLarge → save without district
        vm.save()
        val event = vm.events.first()
        assertEquals(LocationPickerEvent.Saved, event)
    }

    @Test
    fun `zip lookup hidden when asset unavailable`() = runTest {
        val vm = LocationPickerViewModel(newRepo(), StubZipLookup(available = false), newMembers())
        // The flag defaults to true; wait until the init coroutine flips it.
        val s = vm.uiState.first { !it.isZipLookupAvailable }
        assertFalse(s.isZipLookupAvailable)
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
