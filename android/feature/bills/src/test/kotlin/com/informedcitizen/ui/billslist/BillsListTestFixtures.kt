package com.informedcitizen.ui.billslist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.informedcitizen.data.ai.AiCapability
import com.informedcitizen.data.ai.BillSummary
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.cache.BillSummaryCache
import com.informedcitizen.data.cache.BillSummaryEntry
import com.informedcitizen.data.repository.AiTitlesPreferenceRepository
import com.informedcitizen.data.work.BillSummarizationController
import com.informedcitizen.data.work.SummarizationScope
import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.CongressEntry
import com.informedcitizen.pipeline.model.CongressesIndex
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.SessionCalendar
import com.informedcitizen.pipeline.model.SessionCalendarSource
import com.informedcitizen.pipeline.model.Sponsor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal fun billFixture(
    id: String,
    title: String = "Title for $id",
    actionText: String = "Action",
    outcome: Outcome = Outcome.PASSED_HOUSE,
    policyArea: String? = null,
): Bill = Bill(
    id = id,
    congress = 119,
    type = "hr",
    number = "1",
    title = title,
    shortTitle = null,
    sponsor = Sponsor(name = "Sponsor", party = "D", state = "CA"),
    introducedDate = "2026-01-01",
    latestAction = Action(date = "2026-05-01", text = actionText),
    outcome = outcome,
    policyArea = policyArea,
    summaryCrs = null,
    textUrlHtml = null,
    textUrlXml = null,
    textUrlPdf = null,
    congressGovUrl = "https://congress.gov/$id",
)

internal class StubBillsApi(private val manifest: BillsManifest) : BillsApi {
    override suspend fun getCongressesIndex(): CongressesIndex = CongressesIndex(
        currentCongress = manifest.congress,
        congresses = listOf(CongressEntry(congress = manifest.congress, manifestPath = "congress${manifest.congress}_bills.json", isCurrent = true)),
    )
    override suspend fun getBillsManifest(url: String): BillsManifest = manifest
    override suspend fun getSessionCalendar(): SessionCalendar = SessionCalendar(
        generatedAt = "2026-01-01",
        source = SessionCalendarSource(house = "stub", senate = "stub"),
        chambers = emptyMap(),
    )
    override suspend fun getElectionCalendar(): ElectionCalendar = error("not used in this test")
}

internal class StubPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

internal class StubCache(initial: Map<String, BillSummaryEntry> = emptyMap()) : BillSummaryCache {
    private val flow = MutableStateFlow(initial)
    override fun observeAll() = flow
    override suspend fun get(billId: String) = flow.value[billId]
    override suspend fun putSuccess(billId: String, summary: BillSummary, generatedAtMillis: Long) {
        flow.value = flow.value + (billId to BillSummaryEntry(billId, summary, null, generatedAtMillis))
    }
    override suspend fun putError(billId: String, errorKind: String, generatedAtMillis: Long) {
        flow.value = flow.value + (billId to BillSummaryEntry(billId, null, errorKind, generatedAtMillis))
    }
    override suspend fun delete(billId: String) { flow.value = flow.value - billId }
    override suspend fun clearAll() { flow.value = emptyMap() }
    override suspend fun enqueue(billId: String, priority: Int, bypassCap: Boolean, enqueuedAtMillis: Long) {}
    override suspend fun nextPending() = null
    override suspend fun dequeue(billId: String) {}
    override suspend fun queueDepth() = 0L
    override suspend fun clearPending() {}
    override suspend fun incrementAttemptsToday(localDateIso: String) {}
    override suspend fun attemptsToday(localDateIso: String) = 0L
}

internal class FakeAiCapability(
    initial: AiCapability.Status = AiCapability.Status.Available,
) : AiCapability {
    private val state = MutableStateFlow(initial)
    override val status: Flow<AiCapability.Status> = state
    override fun requestDownload() = Unit
}

internal class FakeAiTitlesPrefs : AiTitlesPreferenceRepository {
    private val enabledState = MutableStateFlow(false)
    private val scopeState = MutableStateFlow<SummarizationScope>(SummarizationScope.DEFAULT)
    override val enabled: Flow<Boolean> = enabledState
    override val scope: Flow<SummarizationScope> = scopeState
    override suspend fun setEnabled(enabled: Boolean) { enabledState.value = enabled }
    override suspend fun setScope(scope: SummarizationScope) { scopeState.value = scope }
}

internal object NoOpController : BillSummarizationController {
    override fun start() = Unit
    override fun retry(billId: String) = Unit
    override fun stopNow() = Unit
    override fun clearCache() = Unit
}
