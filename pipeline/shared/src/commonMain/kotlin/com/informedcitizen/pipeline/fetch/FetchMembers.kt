package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.LegislatorsClient
import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.pipeline.model.MemberLegislationItem
import com.informedcitizen.pipeline.model.SocialHandle
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Page size for `/member/congress/{c}` and per-member legislation list endpoints. */
const val MEMBER_LIST_PAGE_LIMIT: Int = 250

/** Mirrors Python `fetch_members.LIST_PAGES_MAX = 10` (10 × 250 = 2500 caps the roster walk). */
const val MEMBER_LIST_PAGES_MAX: Int = 10

/**
 * Mirrors Python `fetch_members.LEG_PAGES_MAX = 5` (5 × 250 = 1250 caps
 * each `(member, kind)` walk). Senior senators have thousands of
 * cosponsorships; the cap keeps runtime bounded — the app shows at most
 * a couple hundred at a time.
 */
const val MEMBER_LEG_PAGES_MAX: Int = 5

/** Per-phase tallies returned by [fetchMembers]. */
data class FetchMembersResult(
    val congress: Int,
    val ranPhase1: Boolean,
    val ranPhase2: Boolean,
    val phase1MemberCount: Int,
    val phase2Backfilled: Int,
    val phase2SkippedCached: Int,
    val phase2TimedOut: Boolean,
    val contactInfoLoaded: Boolean,
    val contactInfoSize: Int,
    val socialsLoaded: Boolean,
    val socialsSize: Int,
)

/**
 * Streaming progress hooks. Defaulted to no-ops so the orchestrator
 * stays testable without log noise; the CLI wraps each with a `println`.
 */
data class FetchMembersProgress(
    val onPhase1Start: (congress: Int) -> Unit = {},
    val onPhase1Member: (member: Member) -> Unit = {},
    val onPhase1Done: (count: Int) -> Unit = {},
    val onPhase2Start: () -> Unit = {},
    val onPhase2MemberDone: (bioguideId: String) -> Unit = {},
    val onPhase2TimeBudgetExceeded: (processed: Int, skipped: Int) -> Unit = { _, _ -> },
    val onPhase2Done: (backfilled: Int, skipped: Int, total: Int) -> Unit = { _, _, _ -> },
    val onContactInfoLoaded: (size: Int, withForm: Int, withSite: Int) -> Unit = { _, _, _ -> },
    val onContactInfoFailed: (message: String) -> Unit = {},
    val onSocialsLoaded: (size: Int, totalHandles: Int) -> Unit = { _, _ -> },
    val onSocialsFailed: (message: String) -> Unit = {},
    val onStateWarning: (message: String) -> Unit = {},
)

/**
 * Full fetch-members orchestrator. Mirrors Python
 * `fetch_members.main`:
 *
 *  - Phase 1 (unconditional unless `runPhase1=false`): walk
 *    `/member/congress/{c}` + per-member detail, merge with the
 *    contact-info side index, persist `members_NNN.json`.
 *  - Phase 2 (skipped if `runPhase2=false`): walk each member's
 *    sponsored / cosponsored legislation. Per-member skip when both
 *    `members/{bid}_{kind}.json` files already exist. Time-budget gate
 *    surfaces via `onPhase2TimeBudgetExceeded` and the function returns
 *    normally (mirrors Python's exit-0 behavior).
 *
 * Phase 1 always runs to completion when enabled — the Reps tab in the
 * Android app depends on the index landing, and it's fast (~7 min for
 * 535 members), so the time budget only gates Phase 2.
 *
 * The `clockOverride` parameter is the deadline anchor for the
 * time-budget check (defaults to wall clock); tests inject a fake
 * [Clock] to drive deterministic budget-exhaustion behavior.
 */
suspend fun fetchMembers(
    congressClient: CongressClient,
    legislatorsClient: LegislatorsClient,
    congress: Int,
    nowIso: String,
    indexStore: FileMembersIndexStore,
    legislationStore: FileMemberLegislationStore,
    errors: ErrorCollector,
    runPhase1: Boolean = true,
    runPhase2: Boolean = true,
    timeBudgetMillis: Long = 300L * 60L * 1000L,
    progress: FetchMembersProgress = FetchMembersProgress(),
    clockOverride: Clock = Clock.System,
    maxListPages: Int = MEMBER_LIST_PAGES_MAX,
    maxLegPages: Int = MEMBER_LEG_PAGES_MAX,
): FetchMembersResult {
    val startMillis = clockOverride.now().toEpochMilliseconds()
    val deadlineMillis = startMillis + timeBudgetMillis

    val existingIndex = indexStore.load(congress)
    val existingByBid: Map<String, Member> =
        existingIndex?.members.orEmpty().associateBy { it.bioguideId }
    var membersOut: List<Member> = existingIndex?.members.orEmpty()

    var contactInfo: Map<String, LegislatorContactInfo> = emptyMap()
    var contactInfoLoaded = false
    if (runPhase1) {
        try {
            val text = legislatorsClient.fetchCurrent()
            contactInfo = parseContactInfoJson(text)
            contactInfoLoaded = true
            val withForm = contactInfo.values.count { it.contactForm != null }
            val withSite = contactInfo.values.count { it.website != null }
            progress.onContactInfoLoaded(contactInfo.size, withForm, withSite)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Non-fatal: members still publish with both fields null.
            errors.record(
                kind = "contact_info_index",
                identifier = "unitedstates",
                errorClass = e::class.simpleName ?: "Throwable",
                message = e.message ?: e.toString(),
            )
            progress.onContactInfoFailed(e.message ?: e.toString())
        }
    }

    var socialsIndex: Map<String, List<SocialHandle>> = emptyMap()
    var socialsLoaded = false
    if (runPhase1) {
        try {
            val socialsText = legislatorsClient.fetchSocialMedia()
            socialsIndex = parseSocialsJson(socialsText)
            socialsLoaded = true
            val totalHandles = socialsIndex.values.sumOf { it.size }
            progress.onSocialsLoaded(socialsIndex.size, totalHandles)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Non-fatal: members still publish with socials=[].
            errors.record(
                kind = "socials_index",
                identifier = "unitedstates",
                errorClass = e::class.simpleName ?: "Throwable",
                message = e.message ?: e.toString(),
            )
            progress.onSocialsFailed(e.message ?: e.toString())
        }
    }

    if (runPhase1) {
        progress.onPhase1Start(congress)
        val collected = mutableListOf<Member>()
        for (summary in listMembers(congressClient, congress, maxListPages)) {
            val bioguideId = summary.stringField("bioguideId").orEmpty()
            if (bioguideId.isEmpty()) continue
            val parsed: Member? = try {
                val detailBody = congressClient.get("/member/$bioguideId")
                val detail = detailBody.jsonObjectField("member") ?: JsonObject(emptyMap())
                // Detail wins on every overlapping key; list summary supplies
                // fields the detail endpoint sometimes omits (e.g. depiction,
                // terms). Order matters — detail responses are richer for most
                // fields but can intermittently drop these specific ones.
                val merged = JsonObject(summary.toMutableMap().apply { putAll(detail) })
                parseMemberSummary(merged, progress.onStateWarning)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val cached = existingByBid[bioguideId]
                if (cached != null) {
                    errors.record(
                        kind = "member_detail_reused_cache",
                        identifier = bioguideId,
                        errorClass = e::class.simpleName ?: "Throwable",
                        message = e.message ?: e.toString(),
                    )
                    cached
                } else {
                    errors.record(
                        kind = "member_detail_dropped",
                        identifier = bioguideId,
                        errorClass = e::class.simpleName ?: "Throwable",
                        message = e.message ?: e.toString(),
                    )
                    null
                }
            }
            if (parsed == null) continue
            val info = contactInfo[bioguideId]
            val withContact = parsed.copy(
                contactForm = info?.contactForm,
                website = info?.website,
                socials = socialsIndex[bioguideId].orEmpty(),
            )
            collected += withContact
            progress.onPhase1Member(withContact)
        }
        indexStore.save(congress, collected, nowIso)
        membersOut = collected
        progress.onPhase1Done(collected.size)
    } else {
        // --phase2-only: must have an existing index to walk.
        if (membersOut.isEmpty()) {
            errors.record(
                kind = "phase2_without_index",
                identifier = "members_$congress",
                errorClass = "IllegalState",
                message = "Phase 2 requested but no existing members index was found.",
            )
            return FetchMembersResult(
                congress = congress,
                ranPhase1 = false,
                ranPhase2 = false,
                phase1MemberCount = 0,
                phase2Backfilled = 0,
                phase2SkippedCached = 0,
                phase2TimedOut = false,
                contactInfoLoaded = false,
                contactInfoSize = 0,
                socialsLoaded = false,
                socialsSize = 0,
            )
        }
    }

    if (!runPhase2) {
        return FetchMembersResult(
            congress = congress,
            ranPhase1 = runPhase1,
            ranPhase2 = false,
            phase1MemberCount = membersOut.size,
            phase2Backfilled = 0,
            phase2SkippedCached = 0,
            phase2TimedOut = false,
            contactInfoLoaded = contactInfoLoaded,
            contactInfoSize = contactInfo.size,
            socialsLoaded = socialsLoaded,
            socialsSize = socialsIndex.size,
        )
    }

    progress.onPhase2Start()
    var backfilled = 0
    var skippedCached = 0
    var timedOut = false
    for (parsed in membersOut) {
        if (clockOverride.now().toEpochMilliseconds() > deadlineMillis) {
            timedOut = true
            progress.onPhase2TimeBudgetExceeded(backfilled, skippedCached)
            break
        }
        val bid = parsed.bioguideId
        if (legislationStore.exists(bid, "sponsored") &&
            legislationStore.exists(bid, "cosponsored")
        ) {
            skippedCached++
            continue
        }
        for (kind in listOf("sponsored", "cosponsored")) {
            val rawItems: List<JsonObject> = try {
                fetchLegislation(congressClient, bid, kind, maxLegPages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                errors.record(
                    kind = "${kind}_legislation",
                    identifier = bid,
                    errorClass = e::class.simpleName ?: "Throwable",
                    message = e.message ?: e.toString(),
                )
                emptyList()
            }
            val bills: List<MemberLegislationItem> = rawItems.map(::parseMemberLegislationItem)
            legislationStore.save(bid, kind, congress, bills, nowIso)
        }
        backfilled++
        progress.onPhase2MemberDone(bid)
    }
    progress.onPhase2Done(backfilled, skippedCached, membersOut.size)

    return FetchMembersResult(
        congress = congress,
        ranPhase1 = runPhase1,
        ranPhase2 = true,
        phase1MemberCount = membersOut.size,
        phase2Backfilled = backfilled,
        phase2SkippedCached = skippedCached,
        phase2TimedOut = timedOut,
        contactInfoLoaded = contactInfoLoaded,
        contactInfoSize = contactInfo.size,
        socialsLoaded = socialsLoaded,
        socialsSize = socialsIndex.size,
    )
}

/**
 * Paginated walk of `/member/congress/{congress}` with `currentMember=true`.
 * Mirrors Python `fetch_members.list_members`. Terminates when a page
 * comes back shorter than the limit (Congress.gov returns a short
 * final page when the list is exhausted).
 */
internal suspend fun listMembers(
    client: CongressClient,
    congress: Int,
    maxPages: Int,
): List<JsonObject> {
    val collected = mutableListOf<JsonObject>()
    for (page in 0 until maxPages) {
        val offset = page * MEMBER_LIST_PAGE_LIMIT
        val body = client.get(
            path = "/member/congress/$congress",
            params = mapOf(
                "limit" to MEMBER_LIST_PAGE_LIMIT.toString(),
                "offset" to offset.toString(),
                "currentMember" to "true",
            ),
        )
        val members = (body["members"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        if (members.isEmpty()) return collected
        collected += members
        if (members.size < MEMBER_LIST_PAGE_LIMIT) return collected
    }
    return collected
}

/**
 * Fetch all pages of sponsored / cosponsored legislation for [bioguideId].
 * Mirrors Python `fetch_members.fetch_legislation`.
 */
internal suspend fun fetchLegislation(
    client: CongressClient,
    bioguideId: String,
    kind: String,
    maxPages: Int,
): List<JsonObject> {
    val (endpoint, bodyKey) = when (kind) {
        "sponsored" -> "/member/$bioguideId/sponsored-legislation" to "sponsoredLegislation"
        "cosponsored" -> "/member/$bioguideId/cosponsored-legislation" to "cosponsoredLegislation"
        else -> error("unknown kind: '$kind'")
    }
    val items = mutableListOf<JsonObject>()
    for (page in 0 until maxPages) {
        val offset = page * MEMBER_LIST_PAGE_LIMIT
        val body = client.get(
            path = endpoint,
            params = mapOf(
                "limit" to MEMBER_LIST_PAGE_LIMIT.toString(),
                "offset" to offset.toString(),
            ),
        )
        val pageItems = (body[bodyKey] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        if (pageItems.isEmpty()) break
        items += pageItems
        if (pageItems.size < MEMBER_LIST_PAGE_LIMIT) break
    }
    return items
}
