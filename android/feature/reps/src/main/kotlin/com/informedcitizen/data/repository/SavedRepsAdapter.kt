package com.informedcitizen.data.repository

import com.informedcitizen.ui.reps.computeCurrentCongress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SavedRepsSource] adapter for the bills surfaces: resolves the saved
 * bioguide IDs against the current Congress's members index. Lives here
 * (not in `feature:bills`) because this module already depends on
 * `feature:bills` — the port is defined there and implemented over the
 * existing edge, avoiding a dependency cycle. Emits an empty list when
 * no reps are saved or the index lookup fails; the vote surfaces treat
 * both the same as "no saved reps".
 */
@Singleton
class SavedRepsAdapter @Inject constructor(
    savedRepsRepository: SavedRepsRepository,
    private val members: MemberRepository,
) : SavedRepsSource {

    internal var congressProvider: () -> Int = ::computeCurrentCongress

    override val savedReps: Flow<List<SavedRep>> =
        savedRepsRepository.savedIds.map { ids -> resolve(ids) }

    private suspend fun resolve(ids: Set<String>): List<SavedRep> {
        if (ids.isEmpty()) return emptyList()
        return runCatching {
            val reps = members.findRepsByIds(congress = congressProvider(), bioguideIds = ids)
            (reps.house + reps.senators).map { member ->
                SavedRep(
                    bioguideId = member.bioguideId,
                    name = member.name,
                    party = member.party,
                    state = member.state,
                    chamber = member.chamber,
                )
            }
        }.getOrDefault(emptyList())
    }
}
