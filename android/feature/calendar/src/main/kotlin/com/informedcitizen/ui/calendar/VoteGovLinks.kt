package com.informedcitizen.ui.calendar

import com.informedcitizen.pipeline.model.Member
import java.util.Locale

/**
 * Pure, offline mapping from a two-letter USPS state/territory code to the
 * canonical vote.gov per-state registration page for issue #25 ("Register &
 * vote" affordance on the #24 elections surface).
 *
 * vote.gov's per-state pages are full-name slugs — `https://vote.gov/register/ohio`,
 * `.../new-york`, `.../district-of-columbia`, `.../american-samoa` (all 200,
 * probed 2026-07-21). The single `/register/{slug}` page carries the state's
 * registration, check-your-registration, and (where official) polling-place
 * links, so **one URL per state covers the whole issue scope** and vote.gov
 * owns the "where official" judgment — the app never does.
 *
 * Kept as a 56-entry Kotlin constant rather than a bundled asset or a field on
 * the election-calendar JSON: state names don't change, the mapping is needed
 * offline before any calendar fetch, and riding the calendar would couple an
 * always-valid action to a fetchable (possibly absent) artifact. Pure function
 * → exhaustively unit-testable, no network.
 */
internal object VoteGovLinks {

    /** vote.gov root; the national fallback and the base every state page hangs off. */
    const val ROOT_URL: String = "https://vote.gov/"

    /** Visible handoff caption the issue demands — always shown beside the button. */
    const val HANDOFF_CAPTION: String = "Opens vote.gov in your browser"

    /**
     * Merged semantic label for the button + caption: the disclosure is
     * announced with the action, not discoverable-by-swipe-only.
     */
    const val ACTION_SEMANTICS: String = "Register and vote. $HANDOFF_CAPTION."

    private const val REGISTER_BASE: String = "https://vote.gov/register/"

    /**
     * USPS code → vote.gov slug for the 50 states, D.C., and the five
     * delegate jurisdictions (AS, GU, MP, PR, VI). Slugs are the lowercased,
     * hyphenated full jurisdiction name (multi-word: `new-york`,
     * `district-of-columbia`, `american-samoa`).
     */
    private val CODE_TO_SLUG: Map<String, String> = mapOf(
        "AL" to "alabama", "AK" to "alaska", "AZ" to "arizona", "AR" to "arkansas",
        "CA" to "california", "CO" to "colorado", "CT" to "connecticut", "DE" to "delaware",
        "FL" to "florida", "GA" to "georgia", "HI" to "hawaii", "ID" to "idaho",
        "IL" to "illinois", "IN" to "indiana", "IA" to "iowa", "KS" to "kansas",
        "KY" to "kentucky", "LA" to "louisiana", "ME" to "maine", "MD" to "maryland",
        "MA" to "massachusetts", "MI" to "michigan", "MN" to "minnesota", "MS" to "mississippi",
        "MO" to "missouri", "MT" to "montana", "NE" to "nebraska", "NV" to "nevada",
        "NH" to "new-hampshire", "NJ" to "new-jersey", "NM" to "new-mexico", "NY" to "new-york",
        "NC" to "north-carolina", "ND" to "north-dakota", "OH" to "ohio", "OK" to "oklahoma",
        "OR" to "oregon", "PA" to "pennsylvania", "RI" to "rhode-island", "SC" to "south-carolina",
        "SD" to "south-dakota", "TN" to "tennessee", "TX" to "texas", "UT" to "utah",
        "VT" to "vermont", "VA" to "virginia", "WA" to "washington", "WV" to "west-virginia",
        "WI" to "wisconsin", "WY" to "wyoming",
        "DC" to "district-of-columbia", "AS" to "american-samoa", "GU" to "guam",
        "MP" to "northern-mariana-islands", "PR" to "puerto-rico", "VI" to "virgin-islands",
    )

    /**
     * The official registration URL for [stateCode]. A known two-letter code
     * (case-insensitive) returns its per-state vote.gov page; a null, blank,
     * or unknown code returns [ROOT_URL] — the national fallback where the
     * user picks their state on vote.gov itself. Never throws.
     */
    fun registrationUrl(stateCode: String?): String {
        val slug = CODE_TO_SLUG[stateCode?.trim()?.uppercase(Locale.US)]
        return if (slug != null) REGISTER_BASE + slug else ROOT_URL
    }

    /**
     * The single state code the "Register & vote" button should target,
     * derived from [savedReps] exactly like #24's scope rule: a distinct
     * [Member.state] across the user's saved reps. Returns null (→ national
     * fallback [ROOT_URL]) when there are no saved reps or they span more
     * than one state, so a multi-state user is sent to the vote.gov front
     * door to pick their own — never guessed. Blank states are ignored.
     */
    fun registrationStateCode(savedReps: List<Member>): String? =
        savedReps.mapNotNull { it.state.trim().ifEmpty { null } }
            .distinct()
            .singleOrNull()
}
