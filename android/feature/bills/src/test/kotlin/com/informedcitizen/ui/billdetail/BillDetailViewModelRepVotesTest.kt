package com.informedcitizen.ui.billdetail

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.api.BillTextFetcher
import com.informedcitizen.data.api.MembersApi
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.MemberVotesRepository
import com.informedcitizen.data.repository.SavedRep
import com.informedcitizen.data.repository.SavedRepsSource
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.MemberLegislation
import com.informedcitizen.pipeline.model.MemberVoteRow
import com.informedcitizen.pipeline.model.MemberVotes
import com.informedcitizen.pipeline.model.MembersIndex
import com.informedcitizen.pipeline.model.VotePosition
import com.informedcitizen.testutil.FakeBillCache
import com.informedcitizen.testutil.FakeMemberVotesCache
import com.informedcitizen.testutil.InMemoryPreferencesDataStore
import com.informedcitizen.ui.billslist.StubBillsApi
import com.informedcitizen.ui.billslist.billFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private val moreno = SavedRep("M002222", "Bernie Moreno", "R", "OH", "senate")
private val turner = SavedRep("T000463", "Michael Turner", "R", "OH", "house")

private fun row(voteId: String, billId: String?, position: VotePosition = VotePosition.YEA) =
    MemberVoteRow(
        voteId = voteId,
        congress = 119,
        date = "2026-07-10",
        question = "On Passage",
        result = "Passed",
        position = position,
        billId = billId,
        type = billId?.let { "hr" },
        number = billId?.let { "30" },
        shortTitle = null,
    )

private fun shard(rep: SavedRep, vararg rows: MemberVoteRow) = MemberVotes(
    generatedAt = "2026-07-21T06:30:00Z",
    bioguideId = rep.bioguideId,
    voteCount = rows.size,
    votes = rows.toList(),
)

private class FakeVotesApi(
    private val shards: Map<String, MemberVotes> = emptyMap(),
    private val failures: Map<String, Throwable> = emptyMap(),
) : MembersApi {
    var voteFetches = 0
    override suspend fun getMembersIndex(congress: String): MembersIndex =
        error("not used by this test")
    override suspend fun getSponsored(bioguideId: String): MemberLegislation =
        error("not used by this test")
    override suspend fun getCosponsored(bioguideId: String): MemberLegislation =
        error("not used by this test")
    override suspend fun getMemberVotes(bioguideId: String): MemberVotes {
        voteFetches++
        failures[bioguideId]?.let { throw it }
        return shards[bioguideId] ?: error("no shard stubbed for $bioguideId")
    }
}

/**
 * Covers the join and gating in [BillDetailViewModel.repVotes]: shard
 * rows filtered to the bill on screen and keyed by vote id, the
 * coverage gate, the no-saved-reps fast path, and failure surfacing.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BillDetailViewModelRepVotesTest {

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(
        votesApi: FakeVotesApi,
        reps: List<SavedRep>,
        votesCoverage: Boolean = true,
    ): BillDetailViewModel {
        val manifest = BillsManifest(
            generatedAt = "2026-07-21",
            congress = 119,
            bills = listOf(billFixture(id = "hr30-119")),
            votesCoverage = votesCoverage,
        )
        return BillDetailViewModel(
            billRepository = BillRepository(
                api = StubBillsApi(manifest),
                dataStore = InMemoryPreferencesDataStore(),
                crashReporter = FakeCrashReporter(),
                billCache = FakeBillCache(),
            ),
            billTextFetcher = BillTextFetcher(OkHttpClient(), FakeCrashReporter()),
            savedRepsSource = object : SavedRepsSource {
                override val savedReps = MutableStateFlow(reps)
            },
            memberVotesRepository = MemberVotesRepository(
                api = votesApi,
                cache = FakeMemberVotesCache(),
                crashReporter = FakeCrashReporter(),
            ),
        )
    }

    @Test
    fun `positions for the bill on screen are keyed by vote id`() = runTest {
        val api = FakeVotesApi(
            shards = mapOf(
                moreno.bioguideId to shard(
                    moreno,
                    row("senate-119-1-618", billId = "hr30-119"),
                    row("senate-119-1-620", billId = "s99-119"),
                ),
                turner.bioguideId to shard(
                    turner,
                    row("house-119-1-17", billId = "hr30-119", position = VotePosition.NAY),
                ),
            ),
        )
        val vm = makeVm(api, reps = listOf(moreno, turner))

        vm.load("hr30-119")

        val state = vm.repVotes.value
        assertFalse(state.fetchFailed)
        // Only this bill's roll calls appear; the s99-119 row is filtered out.
        assertEquals(setOf("senate-119-1-618", "house-119-1-17"), state.positionsByVoteId.keys)
        assertEquals(moreno, state.positionsByVoteId.getValue("senate-119-1-618").single().rep)
        assertEquals(
            VotePosition.NAY,
            state.positionsByVoteId.getValue("house-119-1-17").single().position,
        )
        assertEquals(2, api.voteFetches)
    }

    @Test
    fun `coverage off yields empty state and zero shard fetches`() = runTest {
        val api = FakeVotesApi(
            shards = mapOf(moreno.bioguideId to shard(moreno, row("senate-119-1-618", "hr30-119"))),
        )
        val vm = makeVm(api, reps = listOf(moreno), votesCoverage = false)

        vm.load("hr30-119")

        assertEquals(RepVotesUiState.Empty, vm.repVotes.value)
        assertEquals(0, api.voteFetches)
    }

    @Test
    fun `no saved reps yields empty state and zero shard fetches`() = runTest {
        val api = FakeVotesApi()
        val vm = makeVm(api, reps = emptyList())

        vm.load("hr30-119")

        assertEquals(RepVotesUiState.Empty, vm.repVotes.value)
        assertEquals(0, api.voteFetches)
    }

    @Test
    fun `shard fetch failure surfaces fetchFailed with the other rep's rows intact`() = runTest {
        val api = FakeVotesApi(
            shards = mapOf(turner.bioguideId to shard(turner, row("house-119-1-17", "hr30-119"))),
            failures = mapOf(moreno.bioguideId to IOException("simulated offline")),
        )
        val vm = makeVm(api, reps = listOf(moreno, turner))

        vm.load("hr30-119")

        val state = vm.repVotes.value
        assertTrue(state.fetchFailed)
        assertEquals(setOf("house-119-1-17"), state.positionsByVoteId.keys)
    }
}
