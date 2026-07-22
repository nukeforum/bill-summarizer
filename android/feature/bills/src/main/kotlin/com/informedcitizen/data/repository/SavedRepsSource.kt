package com.informedcitizen.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * A saved representative resolved to the display fields the vote
 * surfaces render (issue #21). This is deliberately not the wire
 * [com.informedcitizen.pipeline.model.Member]: the bills surfaces need
 * exactly these fields and nothing more.
 */
data class SavedRep(
    val bioguideId: String,
    val name: String,
    val party: String,
    val state: String,
    val chamber: String,
)

/**
 * Port through which the bills surfaces observe the user's saved
 * representatives. `feature:reps` already depends on `feature:bills`
 * (member detail injects [BillRepository]), so this module cannot
 * inject the reps repositories directly — instead `feature:reps`
 * implements this interface over that existing edge and binds it in
 * its Hilt module. Emits an empty list when no reps are saved or the
 * member lookup fails.
 */
interface SavedRepsSource {
    val savedReps: Flow<List<SavedRep>>
}
