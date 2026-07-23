package com.informedcitizen.data.repository

import com.informedcitizen.pipeline.model.Member
import kotlinx.coroutines.flow.Flow

/**
 * Port through which the elections surface (issue #33's "On your ballot"
 * section) observes the user's saved representatives as full wire
 * [Member]s — it needs each member's `next_election_year` (#32) to match
 * them against the cached election calendar, so the trimmed
 * `SavedRep`/`SavedRepsSource` used by the bills surfaces isn't enough.
 *
 * `feature:reps` already depends on `feature:calendar` (the "up for
 * election" badge, iter 20), so this module can't inject the reps
 * repositories directly without a dependency cycle — instead `feature:reps`
 * implements this interface over that existing edge and binds it in its
 * Hilt module (the same inversion `SavedRepsSource` uses for `feature:bills`).
 * Emits an empty list when no reps are saved or the member lookup fails, so
 * the section simply renders nothing rather than erroring the screen.
 */
interface BallotRepsSource {
    val savedReps: Flow<List<Member>>
}
