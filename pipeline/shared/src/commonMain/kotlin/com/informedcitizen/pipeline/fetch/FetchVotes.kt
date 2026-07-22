package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.http.SenateVotesApiException
import com.informedcitizen.pipeline.http.SenateVotesClient
import com.informedcitizen.pipeline.model.Chamber
import com.informedcitizen.pipeline.model.RollCallVote
import com.informedcitizen.pipeline.model.VoteRef
import com.informedcitizen.pipeline.model.VotesIndex
import kotlinx.coroutines.CancellationException

/**
 * Fetch roll-call votes (both chambers) and publish per-vote JSON plus
 * a per-Congress index. Direct port of Python `fetch_votes.py`.
 *
 * Both sources are keyless: Senate votes come from senate.gov LIS XML
 * (menu + detail documents), House votes from clerk.house.gov EVS XML.
 * The House publishes no machine-readable vote menu, so new House
 * rolls are discovered by probing sequentially past the rolls already
 * on disk until the first 404 (roll numbers are dense per
 * calendar-year session).
 *
 * Runs are incremental and self-resuming: a roll call already on disk
 * is never refetched (published roll calls are immutable), so each run
 * costs the Senate vote menu(s), the two legislators YAMLs, one detail
 * XML per *new* vote, and a handful of House probe fetches. The index
 * is rebuilt from the files on disk at the end of every run, so a
 * crashed run heals on the next one.
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

// House sessions are probed the same way: a session that hasn't
// started 404s on its first roll and costs a single fetch.
private val HOUSE_SESSIONS = listOf(1, 2)

// A clerk.house.gov outage or format drift looks like a run of
// consecutive parse failures; a single bad roll has parseable
// successors that reset the counter. The cap keeps a
// 200-with-error-page response from probing forever.
internal const val MAX_CONSECUTIVE_HOUSE_ERRORS: Int = 3

/** Mirrors Python `fetch_votes.py`'s `--max-new` default. */
const val FETCH_VOTES_MAX_NEW_DEFAULT: Int = 1000

/** Unrecoverable fetch failure — the CLI reports it and exits 1. */
class FetchVotesException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

data class FetchVotesResult(
    val congress: Int,
    /** Sessions whose Senate vote menu was published and parsed. */
    val sessions: List<Int>,
    /** Roll calls listed across all fetched Senate menus. */
    val totalListed: Int,
    /** Total new votes saved this run (both chambers). */
    val fetched: Int,
    val senateFetched: Int,
    val houseFetched: Int,
    /** Total votes already on disk (both chambers). */
    val skipped: Int,
    /** House candidate-ballot votes (Speaker elections) skipped. */
    val unsupported: Int,
    val index: VotesIndex,
    /** True iff the bills manifest's vote refs changed and it was rewritten. */
    val billManifestRefreshed: Boolean,
)

/**
 * Streaming progress hooks. Defaulted to no-ops so the orchestrator
 * stays testable without log noise; the CLI wraps each with a `println`.
 */
data class FetchVotesProgress(
    val onMenus: (totalListed: Int, sessions: List<Int>) -> Unit = { _, _ -> },
    val onVoteSaved: (vote: RollCallVote) -> Unit = {},
    val onMaxNewReached: (maxNew: Int) -> Unit = {},
    /** The House probe is starting (mirrors Python's banner line). */
    val onHouseProbeStart: () -> Unit = {},
    /** The Senate fetch exhausted the `--max-new` budget; House deferred. */
    val onHouseDeferred: () -> Unit = {},
    /** A candidate-ballot vote was skipped, e.g. `voteKey = "119-1-2"`. */
    val onUnsupportedVote: (voteKey: String, message: String) -> Unit = { _, _ -> },
    /** [MAX_CONSECUTIVE_HOUSE_ERRORS] hit; the session probe stopped. */
    val onConsecutiveFailures: (count: Int, session: Int) -> Unit = { _, _ -> },
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

private data class HouseFetchResult(
    val fetched: Int,
    val skipped: Int,
    val unsupported: Int,
)

/**
 * Probe clerk.house.gov for new House roll calls and save them.
 * Mirrors Python `fetch_votes.fetch_new_house_votes`.
 *
 * The House publishes no vote menu, but roll numbers are dense and
 * sequential per session, so each session is walked from roll 1: rolls
 * already on disk are skipped for free, missing ones are fetched, and
 * the first 404 is the end of the published roll calls. Non-404 fetch
 * failures are recorded and stop the session probe (an outage is
 * indistinguishable from the end of the rolls; the next run re-walks
 * from roll 1 with free existence checks). Candidate-ballot votes
 * (Speaker elections) cannot be expressed as yea/nay positions; they
 * are skipped — and re-probed every run, a couple of cheap fetches per
 * Congress — rather than recorded, keeping file existence as the only
 * resume cursor.
 */
private suspend fun fetchNewHouseVotes(
    client: SenateVotesClient,
    store: FileVotesStore,
    congress: Int,
    errors: ErrorCollector,
    maxNew: Int,
    progress: FetchVotesProgress,
): HouseFetchResult {
    var fetched = 0
    var skipped = 0
    var unsupported = 0
    for (session in HOUSE_SESSIONS) {
        var consecutiveErrors = 0
        var rollNumber = 0
        session@ while (true) {
            rollNumber += 1
            if (store.voteExists(congress, Chamber.HOUSE, session, rollNumber)) {
                skipped++
                continue
            }
            if (fetched >= maxNew) {
                progress.onMaxNewReached(maxNew)
                return HouseFetchResult(fetched, skipped, unsupported)
            }
            val url = houseVoteSourceUrl(congress, session, rollNumber)
            val voteKey = "$congress-$session-$rollNumber"
            val text = try {
                client.fetch(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e is SenateVotesApiException && e.status == 404) {
                    break@session // end of the published roll calls for this session
                }
                // Outage vs. end is indistinguishable; retry next run.
                errors.record(
                    kind = "house_vote",
                    identifier = voteKey,
                    errorClass = e::class.simpleName ?: "Throwable",
                    message = e.message ?: e.toString(),
                    url = url,
                )
                break@session
            }
            val vote = try {
                parseHouseVote(text)
            } catch (e: UnsupportedVoteException) {
                unsupported++
                consecutiveErrors = 0
                progress.onUnsupportedVote(voteKey, e.message.orEmpty())
                continue
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Per-vote isolation: record and retry next run.
                errors.record(
                    kind = "house_vote",
                    identifier = voteKey,
                    errorClass = e::class.simpleName ?: "Throwable",
                    message = e.message ?: e.toString(),
                    url = url,
                )
                consecutiveErrors++
                if (consecutiveErrors >= MAX_CONSECUTIVE_HOUSE_ERRORS) {
                    progress.onConsecutiveFailures(consecutiveErrors, session)
                    break@session
                }
                continue
            }
            if (vote.congress != congress || vote.session != session || vote.rollNumber != rollNumber) {
                errors.record(
                    kind = "house_vote",
                    identifier = voteKey,
                    errorClass = "IllegalArgumentException",
                    message = "detail XML identifies itself as ${vote.id}",
                    url = url,
                )
                break@session // the year->session mapping is off; nothing later can be right
            }
            consecutiveErrors = 0
            store.saveVote(vote)
            fetched++
            progress.onVoteSaved(vote)
        }
    }
    return HouseFetchResult(fetched, skipped, unsupported)
}

/**
 * Re-attach vote refs to the bills manifest after new votes land.
 * Mirrors Python `fetch_votes.refresh_bill_vote_refs`.
 *
 * Bills leave fetch-bills' refresh window ~60 days after their last
 * action, so this pass — not the bills workflow — is what links votes
 * that arrive later (or between bill runs) to their bills. Returns true
 * when the manifest changed and was rewritten; no-ops when there is no
 * manifest to enrich (votes can backfill before bills on a fresh tree).
 */
fun refreshBillVoteRefs(
    manifestStore: FileBillsManifestStore,
    congress: Int,
    refs: List<VoteRef>,
    nowIso: String,
): Boolean {
    val bills = manifestStore.load(congress)?.bills ?: return false
    val enriched = attachVoteRefs(bills, refs)
    if (enriched == bills) return false
    manifestStore.save(congress, enriched, nowIso)
    return true
}

/**
 * Full fetch-votes orchestrator. Mirrors Python `fetch_votes.main`:
 * fetch the Senate session menus and the lis→bioguide map (both fatal
 * on failure), fetch/parse/save every menu vote not already on disk (a
 * vote that fails is recorded in [errors] and left off disk, so the
 * next run retries it; detail XML whose self-identified session/roll
 * disagrees with the menu is rejected rather than published under the
 * wrong id), probe clerk.house.gov for new House rolls with the
 * remaining [maxNew] budget, then rebuild the index from every vote
 * file on disk and — when a [manifestStore] is supplied — refresh the
 * bills manifest's derived vote refs ([refreshBillVoteRefs]).
 *
 * [maxNew] caps new fetches per run, shared across both chambers
 * (Senate first, remainder to the House); a capped run is harmless —
 * the next run picks up where it left off.
 */
suspend fun fetchVotes(
    client: SenateVotesClient,
    store: FileVotesStore,
    congress: Int,
    nowIso: String,
    errors: ErrorCollector,
    manifestStore: FileBillsManifestStore? = null,
    maxNew: Int = FETCH_VOTES_MAX_NEW_DEFAULT,
    progress: FetchVotesProgress = FetchVotesProgress(),
    currentYamlUrl: String = LEGISLATORS_CURRENT_YAML_URL,
    historicalYamlUrl: String = LEGISLATORS_HISTORICAL_YAML_URL,
): FetchVotesResult {
    val menus = fetchSenateMenus(client, congress)
    val lisToBioguide = buildLisToBioguide(client, currentYamlUrl, historicalYamlUrl)
    val totalListed = menus.sumOf { it.voteNumbers.size }
    progress.onMenus(totalListed, menus.map { it.session })

    var senateFetched = 0
    var senateSkipped = 0
    outer@ for (menu in menus) {
        for (rollNumber in menu.voteNumbers) {
            if (store.voteExists(congress, Chamber.SENATE, menu.session, rollNumber)) {
                senateSkipped++
                continue
            }
            if (senateFetched >= maxNew) {
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
            senateFetched++
            progress.onVoteSaved(vote)
        }
    }

    val houseBudget = maxNew - senateFetched
    val house = if (houseBudget > 0) {
        progress.onHouseProbeStart()
        fetchNewHouseVotes(client, store, congress, errors, houseBudget, progress)
    } else {
        progress.onHouseDeferred()
        HouseFetchResult(fetched = 0, skipped = 0, unsupported = 0)
    }

    val refs = store.loadVotes(congress).map(::buildVoteRef)
    val index = buildVotesIndex(congress, refs, nowIso)
    store.saveIndex(index)
    val billManifestRefreshed = manifestStore != null &&
        refreshBillVoteRefs(manifestStore, congress, index.votes, nowIso)
    return FetchVotesResult(
        congress = congress,
        sessions = menus.map { it.session },
        totalListed = totalListed,
        fetched = senateFetched + house.fetched,
        senateFetched = senateFetched,
        houseFetched = house.fetched,
        skipped = senateSkipped + house.skipped,
        unsupported = house.unsupported,
        index = index,
        billManifestRefreshed = billManifestRefreshed,
    )
}
