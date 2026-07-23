package com.informedcitizen.ui.calendar

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.repository.BallotRepsSource
import com.informedcitizen.data.repository.ElectionCalendarRepository
import com.informedcitizen.data.repository.SessionCalendarRepository
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.CongressesIndex
import com.informedcitizen.pipeline.model.ElectionCalendar
import com.informedcitizen.pipeline.model.ElectionEvent
import com.informedcitizen.pipeline.model.ElectionType
import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.pipeline.model.SessionCalendar
import com.informedcitizen.pipeline.model.SessionCalendarSource
import com.informedcitizen.testutil.FakeElectionCalendarCache
import com.informedcitizen.testutil.FakeSessionCalendarCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCalendarViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `election calendar surfaces alongside a successful session load`() = runTest {
        val api = StubApi(session = SESSION, election = ELECTION)
        val vm = SessionCalendarViewModel(sessionRepo(api), electionRepo(api), ballotReps())

        assertEquals(ELECTION, vm.electionCalendar.value)
        assertEquals(SessionCalendarUiState.Success(SESSION), vm.uiState.value)
    }

    @Test
    fun `an election fetch failure leaves the section hidden without failing the screen`() = runTest {
        val api = StubApi(session = SESSION, election = null)
        val vm = SessionCalendarViewModel(sessionRepo(api), electionRepo(api), ballotReps())

        assertNull(vm.electionCalendar.value)
        // The session calendar still loaded — its own screen is unaffected.
        assertEquals(SessionCalendarUiState.Success(SESSION), vm.uiState.value)
    }

    @Test
    fun `saved reps surface for the On your ballot section`() = runTest {
        val api = StubApi(session = SESSION, election = ELECTION)
        val vm = SessionCalendarViewModel(sessionRepo(api), electionRepo(api), ballotReps(listOf(REP)))

        assertEquals(listOf(REP), vm.savedReps.value)
    }

    private fun sessionRepo(api: BillsApi) =
        SessionCalendarRepository(api, FakeCrashReporter(), FakeSessionCalendarCache())

    private fun electionRepo(api: BillsApi) =
        ElectionCalendarRepository(api, FakeCrashReporter(), FakeElectionCalendarCache())

    private fun ballotReps(reps: List<Member> = emptyList()) = object : BallotRepsSource {
        override val savedReps: Flow<List<Member>> = flowOf(reps)
    }

    private companion object {
        val SESSION = SessionCalendar(
            generatedAt = "2026-05-05T12:00:00Z",
            source = SessionCalendarSource(house = "https://house.invalid", senate = "https://senate.invalid"),
            chambers = emptyMap(),
        )
        val REP = Member(
            bioguideId = "S000001",
            name = "Jane Doe",
            party = "D",
            state = "NY",
            chamber = "house",
            nextElectionYear = 2026,
        )
        val ELECTION = ElectionCalendar(
            generatedAt = "2026-05-05T12:00:00Z",
            source = "test",
            elections = listOf(
                ElectionEvent(
                    state = ElectionEvent.NATIONWIDE,
                    date = "2026-11-03",
                    type = ElectionType.GENERAL,
                    electionYear = 2026,
                ),
            ),
        )
    }

    private class StubApi(
        private val session: SessionCalendar,
        private val election: ElectionCalendar?,
    ) : BillsApi {
        override suspend fun getCongressesIndex(): CongressesIndex = error("not used in this test")
        override suspend fun getBillsManifest(url: String): BillsManifest = error("not used in this test")
        override suspend fun getSessionCalendar(): SessionCalendar = session
        override suspend fun getElectionCalendar(): ElectionCalendar =
            election ?: throw IOException("simulated election fetch failure")
    }
}
