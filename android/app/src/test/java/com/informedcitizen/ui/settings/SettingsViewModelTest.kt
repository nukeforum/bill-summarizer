package com.informedcitizen.ui.settings

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.repository.CrashReportingPreferenceRepository
import com.informedcitizen.data.repository.SavedRepsRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newVm(savedReps: SavedRepsRepository): SettingsViewModel {
        val themePrefs = ThemePreferenceRepository(InMemoryPreferencesDataStore())
        val crashPrefs = CrashReportingPreferenceRepository(InMemoryPreferencesDataStore())
        val crashReporter = FakeCrashReporter()
        return SettingsViewModel(themePrefs, crashPrefs, crashReporter, savedReps)
    }

    @Test
    fun `forgetSavedReps clears saved bioguide ids`() = runTest {
        val savedReps = SavedRepsRepository(InMemoryPreferencesDataStore())
        savedReps.set(setOf("G000595", "T000476", "C001056"))

        val vm = newVm(savedReps)
        vm.forgetSavedReps()

        assertTrue(savedReps.savedIds.first().isEmpty())
    }

    @Test
    fun `hasSavedReps reflects repository contents`() = runTest {
        val savedReps = SavedRepsRepository(InMemoryPreferencesDataStore())
        val vm = newVm(savedReps)

        assertFalse(vm.hasSavedReps.first())

        savedReps.set(setOf("G000595"))
        assertTrue(vm.hasSavedReps.first { it })

        savedReps.forget()
        assertFalse(vm.hasSavedReps.first { !it })
    }
}
