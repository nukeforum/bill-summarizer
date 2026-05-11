package com.informedcitizen.ui.settings

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.ai.FakeAiCapability
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.cache.BillSummaryCache
import com.informedcitizen.data.cache.BillSummaryEntry
import com.informedcitizen.data.ai.BillSummary
import com.informedcitizen.data.model.BillsManifest
import com.informedcitizen.data.model.CongressEntry
import com.informedcitizen.data.model.CongressesIndex
import com.informedcitizen.data.model.SessionCalendar
import com.informedcitizen.data.model.SessionCalendarSource
import com.informedcitizen.data.repository.AiTitlesPreferenceRepository
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.CrashReportingPreferenceRepository
import com.informedcitizen.data.repository.SavedRepsRepository
import com.informedcitizen.data.repository.ThemePreferenceRepository
import com.informedcitizen.data.work.BillSummarizationController
import com.informedcitizen.testutil.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
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
        val aiPrefs = AiTitlesPreferenceRepository(InMemoryPreferencesDataStore())
        val cache = StubBillSummaryCache()
        val billRepo = BillRepository(
            api = StubBillsApi(),
            dataStore = InMemoryPreferencesDataStore(),
            crashReporter = FakeCrashReporter(),
        )
        val controller = BillSummarizationController(
            context = android.content.ContextWrapper(null),
            prefs = aiPrefs,
            cache = cache,
            billRepository = billRepo,
        )
        return SettingsViewModel(
            themePrefs = themePrefs,
            crashPrefs = crashPrefs,
            crashReporter = crashReporter,
            savedReps = savedReps,
            aiPrefs = aiPrefs,
            aiCapability = FakeAiCapability(),
            controller = controller,
        )
    }

    private class StubBillsApi : BillsApi {
        override suspend fun getCongressesIndex() = CongressesIndex(
            currentCongress = 119,
            congresses = listOf(CongressEntry(119, "congress119_bills.json", isCurrent = true)),
        )
        override suspend fun getBillsManifest(url: String) =
            BillsManifest(generatedAt = "x", congress = 119, bills = emptyList())
        override suspend fun getSessionCalendar() = SessionCalendar(
            generatedAt = "x",
            source = SessionCalendarSource(house = "", senate = ""),
            chambers = emptyMap(),
        )
    }

    private class StubBillSummaryCache : BillSummaryCache {
        private val rows = MutableStateFlow<Map<String, BillSummaryEntry>>(emptyMap())
        override fun observeAll() = rows
        override suspend fun get(billId: String) = null
        override suspend fun putSuccess(billId: String, summary: BillSummary, generatedAtMillis: Long) {}
        override suspend fun putError(billId: String, errorKind: String, generatedAtMillis: Long) {}
        override suspend fun delete(billId: String) {}
        override suspend fun clearAll() {}
        override suspend fun enqueue(billId: String, priority: Int, bypassCap: Boolean, enqueuedAtMillis: Long) {}
        override suspend fun nextPending() = null
        override suspend fun dequeue(billId: String) {}
        override suspend fun queueDepth() = 0L
        override suspend fun clearPending() {}
        override suspend fun incrementAttemptsToday(localDateIso: String) {}
        override suspend fun attemptsToday(localDateIso: String) = 0L
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
