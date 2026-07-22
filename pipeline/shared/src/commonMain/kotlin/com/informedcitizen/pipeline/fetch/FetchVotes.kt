package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.http.SenateVotesApiException
import com.informedcitizen.pipeline.http.SenateVotesClient
import com.informedcitizen.pipeline.model.Chamber
import com.informedcitizen.pipeline.model.RollCallVote
import com.informedcitizen.pipeline.model.VotesIndex
import kotlinx.coroutines.CancellationException

/**
 * Fetch Senate roll-call votes and publish per-vote JSON plus a
 * per-Congress index. Direct port of Python `fetch_votes.py`.
 *
 * Senate only for now: senate.gov LIS XML needs no API key. House
 * votes come from the Congress.gov v3 house-vote endpoints
 * (CONGRESS_API_KEY) and land in a follow-up — the output layout
 * already accommodates both chambers.
 *
 * Runs are incremental and self-resuming: a roll call already on disk
 * is never refetched (published roll calls are immutable), so each run
 * costs the vote menu(s), the two legislators YAMLs, and one detail
 * XML per *new* vote. The index is rebuilt from the files on disk at
 * the end of every run, so a crashed run heals on the next one.
 */

const val LEGISLATORS_CURRENT_YAML_URL: String =
    "https://raw.githubusercontent.com/" +
        "unitedstates/congress-legislators/main/legislators-current.yaml"

const val LEGISLATORS_HISTORICAL_YAML_URL: String =
    "https://raw.githubusercontent.com/" +
        "unitedstates/congress-legislators/main/legislators-historical.yaml"

// Both sessions are always attempted; the menu for a session that
// hasn't started yet 404s and is skipped (same pattern as the Senate
// schedule walk in buildSessionCalendar).
private val SENATE_SESSIONS = listOf(1, 2)

/** Mirrors Python `fetch_votes.py`'s `--max-new` default. */
const val FETCH_VOTES_MAX_NEW_DEFAULT: Int = 1000

/** Unrecoverable fetch failure — the CLI reports it and exits 1. */
class FetchVotesException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

data class FetchVotesResult(
    val congress: Int,
    /** Sessions whose vote menu was published and parsed. */
    val sessions: List<Int>,
    /** Roll calls listed across all fetched menus. */
    val totalListed: Int,
    val fetched: Int,
    val skipped: Int,
    val index: VotesIndex,
)

/**
 * Streaming progress hooks. Defaulted to no-ops so the orchestrator
 * stays testable without log noise; the CLI wraps each with a `println`.
 */
data class FetchVotesProgress(
    val onMenus: (totalListed: Int, sessions: List<Int>) -> Unit = { _, _ -> },
    val onVoteSaved: (vote: RollCallVote) -> Unit = {},
    val onMaxNewReached: (maxNew: Int) -> Unit = {},
)

/**
 * Union of the current and historical legislators YAMLs. Mirrors
 * Python `fetch_votes.build_lis_to_bioguide`.
 *
 * Historical is required, not a nicety: a senator who leaves
 * mid-Congress moves to legislators-historical.yaml while their roll
 * calls stay published. Current entries win on the (unobserved) chance
 * of a conflict.
 */
suspend fun buildLisToBioguide(
    client: SenateVotesClient,
    currentYamlUrl: String = LEGISLATORS_CURRENT_YAML_URL,
    historicalYamlUrl: String = LEGISLATORS_HISTORICAL_YAML_URL,
): Map<String, String> {
    val historical = parseLisToBioguideYaml(client.fetch(historicalYamlUrl))
    val current = parseLisToBioguideYaml(client.fetch(currentYamlUrl))
    return historical + current
}

/**
 * Fetch and parse the vote menu for each published session. Mirrors
 * Python `fetch_votes.fetch_senate_menus`.
 *
 * A 404 means the session hasn't started (or its menu isn't up yet)
 * and is skipped; any other HTTP failure propagates. Throws
 * [FetchVotesException] if no session menu is available at all — that
 * means the congress number is wrong or senate.gov changed its layout,
 * either way nothing useful can be published.
 */
suspend fun fetchSenateMenus(client: SenateVotesClient, congress: Int): List<SenateVoteMenu> {
    val menus = mutableListOf<SenateVoteMenu>()
    for (session in SENATE_SESSIONS) {
        val url = senateVoteMenuUrl(congress, session)
        val text = try {
            client.fetch(url)
        } catch (e: SenateVotesApiException) {
            if (e.status == 404) continue
            throw e
        }
        val menu = parseSenateVoteMenu(text)
        if (menu.congress != congress || menu.session != session) {
            throw FetchVotesException(
                "vote menu at $url identifies itself as congress " +
                    "${menu.congress} session ${menu.session}",
            )
        }
        menus += menu
    }
    if (menus.isEmpty()) {
        throw FetchVotesException("no Senate vote menu available for Congress $congress")
    }
    return menus
}

/**
 * Full fetch-votes orchestrator. Mirrors Python `fetch_votes.main`:
 * fetch the session menus and the lis→bioguide map (both fatal on
 * failure), fetch/parse/save every menu vote not already on disk (a
 * vote that fails is recorded in [errors] and left off disk, so the
 * next run retries it; detail XML whose self-identified session/roll
 * disagrees with the menu is rejected rather than published under the
 * wrong id), then rebuild the index from every vote file on disk.
 *
 * [maxNew] caps new fetches per run; a capped run is harmless — the
 * next run picks up where it left off.
 */
suspend fun fetchVotes(
    client: SenateVotesClient,
    store: FileVotesStore,
    congress: Int,
    nowIso: String,
    errors: ErrorCollector,
    maxNew: Int = FETCH_VOTES_MAX_NEW_DEFAULT,
    progress: FetchVotesProgress = FetchVotesProgress(),
    currentYamlUrl: String = LEGISLATORS_CURRENT_YAML_URL,
    historicalYamlUrl: String = LEGISLATORS_HISTORICAL_YAML_URL,
): FetchVotesResult {
    val menus = fetchSenateMenus(client, congress)
    val lisToBioguide = buildLisToBioguide(client, currentYamlUrl, historicalYamlUrl)
    val totalListed = menus.sumOf { it.voteNumbers.size }
    progress.onMenus(totalListed, menus.map { it.session })

    var fetched = 0
    var skipped = 0
    outer@ for (menu in menus) {
        for (rollNumber in menu.voteNumbers) {
            if (store.voteExists(congress, Chamber.SENATE, menu.session, rollNumber)) {
                skipped++
                continue
            }
            if (fetched >= maxNew) {
                progress.onMaxNewReached(maxNew)
                break@outer
            }
            val url = senateVoteSourceUrl(congress, menu.session, rollNumber)
            val vote = try {
                val parsed = parseSenateVote(client.fetch(url), lisToBioguide)
                check(parsed.session == menu.session && parsed.rollNumber == rollNumber) {
                    "detail XML identifies itself as session ${parsed.session} " +
                        "roll ${parsed.rollNumber}"
                }
                parsed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Per-vote isolation: record and retry next run.
                errors.record(
                    kind = "senate_vote",
                    identifier = "$congress-${menu.session}-$rollNumber",
                    errorClass = e::class.simpleName ?: "Throwable",
                    message = e.message ?: e.toString(),
                    url = url,
                )
                continue
            }
            store.saveVote(vote)
            fetched++
            progress.onVoteSaved(vote)
        }
    }

    val refs = store.loadVotes(congress).map(::buildVoteRef)
    val index = buildVotesIndex(congress, refs, nowIso)
    store.saveIndex(index)
    return FetchVotesResult(
        congress = congress,
        sessions = menus.map { it.session },
        totalListed = totalListed,
        fetched = fetched,
        skipped = skipped,
        index = index,
    )
}
