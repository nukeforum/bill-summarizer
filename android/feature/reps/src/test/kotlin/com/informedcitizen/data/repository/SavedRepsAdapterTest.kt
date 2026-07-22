package com.informedcitizen.data.repository

import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.pipeline.model.MemberLegislation
import com.informedcitizen.pipeline.model.MembersIndex
import com.informedcitizen.testutil.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private fun member(bid: String, chamber: String) = Member(
    bioguideId = bid,
    name = "Name $bid",
    party = "R",
    state = "OH",
    district = if (chamber == "house") 10 else null,
    chamber = chamber,
)

private class FakeMemberRepository(
    private val reps: RepsForLocation = RepsForLocation(emptyList(), emptyList()),
    private val failure: Throwable? = null,
) : MemberRepository {
    var lookups = 0
    override suspend fun findRepsForLocation(congress: Int, stateCode: String, district: Int?) =
        error("not used by SavedRepsAdapter")
    override suspend fun findRepsByIds(congress: Int, bioguideIds: Set<String>): RepsForLocation {
        lookups++
        failure?.let { throw it }
        return reps
    }
    override suspend fun getMember(bioguideId: String, congress: Int): Member? = null
    override suspend fun getSponsored(bioguideId: String): MemberLegislation? = null
    override suspend fun getCosponsored(bioguideId: String): MemberLegislation? = null
    override suspend fun getIndex(congress: Int): MembersIndex? = null
}

class SavedRepsAdapterTest {

    private suspend fun savedRepsRepository(ids: Set<String>): SavedRepsRepository =
        SavedRepsRepository(InMemoryPreferencesDataStore()).also { it.set(ids) }

    @Test
    fun `resolves saved ids to display fields, house before senators`() = runTest {
        val members = FakeMemberRepository(
            reps = RepsForLocation(
                house = listOf(member("T000463", "house")),
                senators = listOf(member("M002222", "senate"), member("H002222", "senate")),
            ),
        )
        val adapter = SavedRepsAdapter(savedRepsRepository(setOf("T000463", "M002222", "H002222")), members)
        adapter.congressProvider = { 119 }

        val reps = adapter.savedReps.first()

        assertEquals(listOf("T000463", "M002222", "H002222"), reps.map { it.bioguideId })
        val turner = reps.first()
        assertEquals(SavedRep("T000463", "Name T000463", "R", "OH", "house"), turner)
    }

    @Test
    fun `no saved ids emits empty without a member lookup`() = runTest {
        val members = FakeMemberRepository()
        val adapter = SavedRepsAdapter(SavedRepsRepository(InMemoryPreferencesDataStore()), members)

        assertTrue(adapter.savedReps.first().isEmpty())
        assertEquals(0, members.lookups)
    }

    @Test
    fun `member lookup failure emits empty`() = runTest {
        val members = FakeMemberRepository(failure = IOException("offline"))
        val adapter = SavedRepsAdapter(savedRepsRepository(setOf("T000463")), members)
        adapter.congressProvider = { 119 }

        assertTrue(adapter.savedReps.first().isEmpty())
        assertEquals(1, members.lookups)
    }
}
