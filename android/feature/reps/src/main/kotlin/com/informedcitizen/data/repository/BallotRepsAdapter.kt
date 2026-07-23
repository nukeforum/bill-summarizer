package com.informedcitizen.data.repository

import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.ui.reps.computeCurrentCongress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BallotRepsSource] adapter for the elections surface (issue #33): resolves
 * the saved bioguide IDs against the current Congress's members index and
 * hands back the full wire [Member]s so the "On your ballot" section can read
 * each member's `next_election_year`. Lives here (not in `feature:calendar`)
 * because this module already depends on `feature:calendar` — the port is
 * defined there and implemented over the existing edge, avoiding a cycle.
 * Emits an empty list when no reps are saved or the index lookup fails; the
 * section treats both the same as "no saved reps on the ballot".
 */
@Singleton
class BallotRepsAdapter @Inject constructor(
    savedRepsRepository: SavedRepsRepository,
    private val members: MemberRepository,
) : BallotRepsSource {

    internal var congressProvider: () -> Int = ::computeCurrentCongress

    override val savedReps: Flow<List<Member>> =
        savedRepsRepository.savedIds.map { ids -> resolve(ids) }

    private suspend fun resolve(ids: Set<String>): List<Member> {
        if (ids.isEmpty()) return emptyList()
        return runCatching {
            val reps = members.findRepsByIds(congress = congressProvider(), bioguideIds = ids)
            reps.house + reps.senators
        }.getOrDefault(emptyList())
    }
}
