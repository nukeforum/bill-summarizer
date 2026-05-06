package com.informedcitizen.ui.reps

import com.informedcitizen.data.repository.LocationPreferenceRepository
import com.informedcitizen.data.zipcrosswalk.ZipDistrictLookup
import com.informedcitizen.data.zipcrosswalk.ZipDistrictResult
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class StubZipLookup(
    var nextResult: ZipDistrictResult = ZipDistrictResult.Miss,
) : ZipDistrictLookup(loader = { "" }) {
    override suspend fun lookup(zip: String): ZipDistrictResult = nextResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class LocationPickerViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newRepo() = LocationPreferenceRepository(InMemoryPreferencesDataStore())

    @Test
    fun `selecting a state populates districts`() = runTest {
        val repo = newRepo()
        val vm = LocationPickerViewModel(repo, StubZipLookup())
        vm.selectState("TX")
        val s = vm.uiState.first()
        assertEquals("TX", s.selectedState)
        assertTrue(s.districtsForState.isNotEmpty())
        assertFalse(s.isAtLargeOrDelegate)
        assertFalse(s.canSave)  // need a district pick before save
    }

    @Test
    fun `selecting an at-large state marks atLarge and enables save`() = runTest {
        val vm = LocationPickerViewModel(newRepo(), StubZipLookup())
        vm.selectState("VT")
        val s = vm.uiState.first()
        assertTrue(s.isAtLargeOrDelegate)
        assertTrue(s.canSave)
    }

    @Test
    fun `selecting a delegate jurisdiction marks atLarge`() = runTest {
        val vm = LocationPickerViewModel(newRepo(), StubZipLookup())
        vm.selectState("DC")
        assertTrue(vm.uiState.first().isAtLargeOrDelegate)
    }

    @Test
    fun `selectDistrict enables save when state is set`() = runTest {
        val vm = LocationPickerViewModel(newRepo(), StubZipLookup())
        vm.selectState("TX")
        vm.selectDistrict(21)
        assertTrue(vm.uiState.first().canSave)
    }

    @Test
    fun `zip lookup single sets state and district hint`() = runTest {
        val zip = StubZipLookup(nextResult = ZipDistrictResult.Single("NY", 12))
        val vm = LocationPickerViewModel(newRepo(), zip)
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
        val vm = LocationPickerViewModel(newRepo(), zip)
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
        val vm = LocationPickerViewModel(newRepo(), zip)
        vm.onZipChanged("00000")
        vm.lookupZip()
        assertEquals(DistrictHint.Miss, vm.uiState.first().districtHint)
    }

    @Test
    fun `save writes to prefs`() = runTest {
        val repo = newRepo()
        val vm = LocationPickerViewModel(repo, StubZipLookup())
        vm.selectState("TX")
        vm.selectDistrict(21)
        vm.save()
        val saved = repo.location.first()
        assertEquals("TX", saved.stateCode)
        assertEquals(21, saved.district)
    }

    @Test
    fun `save at-large state writes district 0`() = runTest {
        val repo = newRepo()
        val vm = LocationPickerViewModel(repo, StubZipLookup())
        vm.selectState("DC")
        vm.save()
        val saved = repo.location.first()
        assertEquals("DC", saved.stateCode)
        assertEquals(0, saved.district)
    }
}
