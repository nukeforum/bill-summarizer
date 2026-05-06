package com.informedcitizen.ui.settings

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.repository.CrashReportingPreferenceRepository
import com.informedcitizen.data.repository.LocationPreferenceRepository
import com.informedcitizen.data.repository.ThemePreferenceRepository
import com.informedcitizen.testutil.InMemoryPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `forgetLocation calls repository forget`() = runTest {
        val locationPrefs = LocationPreferenceRepository(InMemoryPreferencesDataStore())
        locationPrefs.set(stateCode = "TX", district = 21)

        val themePrefs = ThemePreferenceRepository(InMemoryPreferencesDataStore())
        val crashPrefs = CrashReportingPreferenceRepository(InMemoryPreferencesDataStore())
        val crashReporter = FakeCrashReporter()

        val vm = SettingsViewModel(themePrefs, crashPrefs, crashReporter, locationPrefs)
        vm.forgetLocation()

        val saved = locationPrefs.location.first()
        assertNull(saved.stateCode)
        assertNull(saved.district)
    }
}
