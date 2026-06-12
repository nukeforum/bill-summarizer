package com.informedcitizen.data.repository

import com.informedcitizen.crash.FakeCrashReporter
import com.informedcitizen.data.api.MembersApi
import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.pipeline.model.MemberLegislation
import com.informedcitizen.pipeline.model.MemberLegislationItem
import com.informedcitizen.pipeline.model.MembersIndex
import com.informedcitizen.testutil.FakeMembersIndexCache
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun aMember(bid: String, state: String, district: Int? = null, chamber: String = "house") =
    Member(bid, "Name $bid", "D", state, district, chamber, null, null, 1, 1, null, null)

private val sampleIndex = MembersIndex(
    congress = 119,
    generatedAt = "2026-05-06T03:00:00Z",
    members = listOf(
        aMember("A000001", "TX", 21, "house"),
        aMember("B000002", "MI", null, "senate"),
        aMember("C000003", "MI", null, "senate"),
        aMember("D000004", "DC", 0, "house"),
    ),
)

private fun http404(): HttpException = HttpException(
    Response.error<Any>(404, "".toResponseBody("application/json".toMediaType())),
)

private class FakeMembersApi(
    private val index: MembersIndex,
    private val sponsored: Map<String, MemberLegislation> = emptyMap(),
    private val cosponsored: Map<String, MemberLegislation> = emptyMap(),
    private val sponsoredErrors: Set<String> = emptySet(),
) : MembersApi {
    var indexCalls = 0
    override suspend fun getMembersIndex(congress: String): MembersIndex {
        indexCalls++
        return index
    }
    override suspend fun getSponsored(bioguideId: String): MemberLegislation {
        if (bioguideId in sponsoredErrors) throw http404()
        return sponsored[bioguideId] ?: error("no sponsored fixture for $bioguideId")
    }
    override suspend fun getCosponsored(bioguideId: String): MemberLegislation =
        cosponsored[bioguideId] ?: error("no cosponsored fixture for $bioguideId")
}

class MemberRepositoryTest {

    @Test
    fun `findRepsForLocation with district returns rep plus senators`() = runTest {
        val repo = CachedMemberRepository(FakeMembersApi(sampleIndex), FakeCrashReporter(), FakeMembersIndexCache())
        val out = repo.findRepsForLocation(congress = 119, stateCode = "TX", district = 21)
        assertEquals(listOf("A000001"), out.house.map { it.bioguideId })
        assertTrue("no senators in TX in fixture", out.senators.isEmpty())
    }

    @Test
    fun `findRepsForLocation with null district returns senators only`() = runTest {
        val repo = CachedMemberRepository(FakeMembersApi(sampleIndex), FakeCrashReporter(), FakeMembersIndexCache())
        val out = repo.findRepsForLocation(congress = 119, stateCode = "MI", district = null)
        assertTrue(out.house.isEmpty())
        assertEquals(setOf("B000002", "C000003"), out.senators.map { it.bioguideId }.toSet())
    }

    @Test
    fun `findRepsForLocation returns rep and both senators when state has all three`() = runTest {
        val full = MembersIndex(
            congress = 119,
            generatedAt = "x",
            members = sampleIndex.members + aMember("E000005", "MI", 1, "house"),
        )
        val repo = CachedMemberRepository(FakeMembersApi(full), FakeCrashReporter(), FakeMembersIndexCache())
        val out = repo.findRepsForLocation(119, "MI", 1)
        assertEquals(listOf("E000005"), out.house.map { it.bioguideId })
        assertEquals(setOf("B000002", "C000003"), out.senators.map { it.bioguideId }.toSet())
    }

    @Test
    fun `getSponsored on 404 returns empty MemberLegislation`() = runTest {
        val repo = CachedMemberRepository(
            FakeMembersApi(sampleIndex, sponsoredErrors = setOf("A000001")),
            FakeCrashReporter(),
            FakeMembersIndexCache(),
        )
        val result = repo.getSponsored("A000001")
        assertNotNull(result)
        assertTrue(result!!.bills.isEmpty())
    }

    @Test
    fun `getSponsored drops items with blank type or number`() = runTest {
        val malformed = MemberLegislationItem(
            id = "-119",
            type = "",
            number = "",
            congress = 119,
            title = "",
            introducedDate = "",
            latestAction = Action(date = "", text = ""),
        )
        val good = MemberLegislationItem(
            id = "hr1-119",
            type = "hr",
            number = "1",
            congress = 119,
            title = "Real Bill",
            introducedDate = "2026-01-01",
            latestAction = Action(date = "2026-01-01", text = "Introduced"),
        )
        val fixture = MemberLegislation(
            bioguideId = "A000001",
            congress = 119,
            kind = "sponsored",
            generatedAt = "x",
            bills = listOf(malformed, good, malformed),
        )
        val repo = CachedMemberRepository(
            FakeMembersApi(sampleIndex, sponsored = mapOf("A000001" to fixture)),
            FakeCrashReporter(),
            FakeMembersIndexCache(),
        )
        val result = repo.getSponsored("A000001")
        assertEquals(listOf("hr1-119"), result!!.bills.map { it.id })
    }

    @Test
    fun `getCosponsored drops items with blank type or number`() = runTest {
        val malformed = MemberLegislationItem(
            id = "-119",
            type = "",
            number = "",
            congress = 119,
            title = "",
            introducedDate = "",
            latestAction = Action(date = "", text = ""),
        )
        val fixture = MemberLegislation(
            bioguideId = "A000001",
            congress = 119,
            kind = "cosponsored",
            generatedAt = "x",
            bills = listOf(malformed, malformed),
        )
        val repo = CachedMemberRepository(
            FakeMembersApi(sampleIndex, cosponsored = mapOf("A000001" to fixture)),
            FakeCrashReporter(),
            FakeMembersIndexCache(),
        )
        val result = repo.getCosponsored("A000001")
        assertTrue(result!!.bills.isEmpty())
    }

    @Test
    fun `index cached on second call`() = runTest {
        val api = FakeMembersApi(sampleIndex)
        val repo = CachedMemberRepository(api, FakeCrashReporter(), FakeMembersIndexCache())
        repo.findRepsForLocation(119, "TX", 21)
        repo.findRepsForLocation(119, "TX", 21)
        assertEquals(1, api.indexCalls)
    }

    @Test
    fun `getMember returns null on index load failure`() = runTest {
        val throwingApi = object : MembersApi {
            override suspend fun getMembersIndex(congress: String): MembersIndex =
                throw RuntimeException("boom")
            override suspend fun getSponsored(bioguideId: String): MemberLegislation = error("unused")
            override suspend fun getCosponsored(bioguideId: String): MemberLegislation = error("unused")
        }
        val repo = CachedMemberRepository(throwingApi, FakeCrashReporter(), FakeMembersIndexCache())
        assertNull(repo.getMember("X", 119))
    }
}
