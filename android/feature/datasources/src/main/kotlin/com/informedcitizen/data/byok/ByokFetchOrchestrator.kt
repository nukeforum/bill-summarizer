package com.informedcitizen.data.byok

import android.content.Context
import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.data.cache.BillSource
import com.informedcitizen.data.cache.MemberVotesCache
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.CachedMemberRepository
import com.informedcitizen.data.repository.SavedRepsRepository
import com.informedcitizen.data.repository.SessionCalendarRepository
import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.congressForYear
import com.informedcitizen.pipeline.fetch.FileBillsManifestStore
import com.informedcitizen.pipeline.fetch.FileMemberLegislationStore
import com.informedcitizen.pipeline.fetch.FileMembersIndexStore
import com.informedcitizen.pipeline.fetch.RECENT_DAYS
import com.informedcitizen.pipeline.fetch.FileVotesStore
import com.informedcitizen.pipeline.fetch.buildSessionCalendar
import com.informedcitizen.pipeline.fetch.fetchBills
import com.informedcitizen.pipeline.fetch.fetchMembers
import com.informedcitizen.pipeline.fetch.fetchVotes
import com.informedcitizen.pipeline.fetch.nowIso
import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.LegislatorsClient
import com.informedcitizen.pipeline.http.SenateVotesClient
import com.informedcitizen.pipeline.http.SessionCalendarClient
import com.informedcitizen.pipeline.http.createPipelineHttpClient
import com.informedcitizen.pipeline.model.MemberVotes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import okio.Path.Companion.toOkioPath
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-run cap on newly fetched roll calls for the BYOK votes step.
 * Lower than the CLI's [com.informedcitizen.pipeline.fetch.FETCH_VOTES_MAX_NEW_DEFAULT]
 * (1000) because a phone shouldn't pull an unbounded batch of vote XML
 * in one tick; fetching is incremental, so the record catches up across
 * daily runs.
 */
private const val BYOK_VOTES_MAX_NEW = 400

/**
 * Runs the in-app data pipeline with the user's own API key — the
 * same `pipeline:shared` orchestrators the CI workflows run, pointed
 * at a private working directory under filesDir. Each fetch merges
 * into the previous BYOK output on disk (same manifest-merge semantics
 * as CI), then pushes the result into the app repositories, which
 * update the UI's in-memory state and persist under the BYOK source.
 *
 * Deliberately NOT exposed: historical backfill. Phones are the wrong
 * host for a months-long crawl; the CLI keeps doing that in CI. BYOK
 * covers the current Congress only.
 */
@Singleton
class ByokFetchOrchestrator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyStore: ByokKeyStore,
    private val billRepository: BillRepository,
    private val memberRepository: CachedMemberRepository,
    private val calendarRepository: SessionCalendarRepository,
    private val savedRepsRepository: SavedRepsRepository,
    private val memberVotesCache: MemberVotesCache,
    private val crashReporter: CrashReporter,
) {
    private val workDir: File
        get() = File(context.filesDir, "byok-pipeline").apply { mkdirs() }

    /**
     * Daily refresh of the current-Congress bills manifest (backlog #43).
     *
     * The direct fetch is bounded to the [RECENT_DAYS]-day recency window
     * (the `cutoff` below) and capped per run at [byokMaxBillsPerRun] new
     * bills so a phone never spends the user's whole hourly Congress.gov
     * budget in one tick. Enrichment is incremental — a bill already in
     * the BYOK manifest is skipped for free, so the window fills in across
     * daily ticks. Breadth beyond the window comes from the app's
     * published shards over the keyless read path; the coverage bound is
     * stated to the user via [BYOK_BILLS_COVERAGE_STATEMENT].
     */
    suspend fun fetchBills(): Result<Int> = withKeyedClient { client, apiKey ->
        val now = Clock.System.now()
        val congress = congressForYear(now.toLocalDateTime(TimeZone.UTC).year)
        val result = fetchBills(
            client = CongressClient(client, apiKey),
            congress = congress,
            cutoff = now.minus(RECENT_DAYS, DateTimeUnit.DAY, TimeZone.UTC),
            nowIso = nowIso(now),
            manifestStore = FileBillsManifestStore.system(workDir.toOkioPath()),
            errors = ErrorCollector(),
            maxNew = byokMaxBillsPerRun(),
        )
        billRepository.publishByokBills(result.finalManifest)
        result.finalManifest.bills.size
    }

    /**
     * Daily refresh of roll-call votes (backlog #31). The vote feeds
     * (senate.gov LIS XML, clerk.house.gov EVS XML, congress-legislators
     * YAML) are all keyless — same public-feed pattern as [fetchCalendar]
     * — so votes never touch the user's Congress.gov key budget.
     *
     * Passing the BYOK bills [FileBillsManifestStore] lets the shared
     * driver attach vote refs to the recent-bills manifest and reconcile
     * misclassified outcomes against real passage roll calls (#30). When
     * that changes the manifest, we re-publish it so the enriched refs
     * reach the bill-detail roll-call surfaces (#20) on the BYOK path.
     *
     * The shared driver also rebuilds the per-member vote shards on disk;
     * we publish the saved reps' shards into [MemberVotesCache] under
     * [BillSource.BYOK] so the "your reps voted" (#21) and member-detail
     * recent-votes (#22) surfaces show BYOK votes too.
     *
     * [BYOK_VOTES_MAX_NEW] bounds per-run work on a phone; fetching is
     * incremental (a roll call already on disk is never refetched), so a
     * capped first run tops up on subsequent daily ticks.
     */
    suspend fun fetchVotes(): Result<Int> = runReported("byok votes fetch failed") { _ ->
        val client = createPipelineHttpClient()
        try {
            val now = Clock.System.now()
            val congress = congressForYear(now.toLocalDateTime(TimeZone.UTC).year)
            val manifestStore = FileBillsManifestStore.system(workDir.toOkioPath())
            val votesStore = FileVotesStore.system(workDir.toOkioPath())
            val result = fetchVotes(
                client = SenateVotesClient(client),
                store = votesStore,
                congress = congress,
                nowIso = nowIso(now),
                errors = ErrorCollector(),
                manifestStore = manifestStore,
                maxNew = BYOK_VOTES_MAX_NEW,
            )
            if (result.billManifestRefreshed) {
                manifestStore.load(congress)?.let { billRepository.publishByokBills(it) }
            }
            publishByokMemberVotes(
                savedIds = savedRepsRepository.savedIds.first(),
                cache = memberVotesCache,
                fetchedAtMillis = System.currentTimeMillis(),
                loadShard = { votesStore.loadMemberVotes(it) },
            )
            result.fetched
        } finally {
            client.close()
        }
    }

    /** Weekly refresh of the members index (Phase 1 only — no legislation crawl). */
    suspend fun fetchMembersIndex(): Result<Int> = withKeyedClient { client, apiKey ->
        val now = Clock.System.now()
        val congress = congressForYear(now.toLocalDateTime(TimeZone.UTC).year)
        val indexStore = FileMembersIndexStore.system(workDir.toOkioPath())
        fetchMembers(
            congressClient = CongressClient(client, apiKey),
            legislatorsClient = LegislatorsClient(client),
            congress = congress,
            nowIso = nowIso(now),
            indexStore = indexStore,
            legislationStore = FileMemberLegislationStore.system(workDir.toOkioPath()),
            errors = ErrorCollector(),
            runPhase1 = true,
            runPhase2 = false,
        )
        val index = indexStore.load(congress)
            ?: error("members index missing after Phase 1 run")
        memberRepository.publishByokIndex(index)
        index.members.size
    }

    /** Weekly refresh of the session calendar. Public feeds — no key needed. */
    suspend fun fetchCalendar(): Result<Int> = runReported("byok calendar fetch failed") { _ ->
        val client = createPipelineHttpClient()
        try {
            val now = Clock.System.now()
            val result = buildSessionCalendar(
                client = SessionCalendarClient(client),
                today = now.toLocalDateTime(TimeZone.UTC).date,
                nowIso = nowIso(now),
            )
            calendarRepository.publishByokCalendar(result.calendar)
            result.calendar.chambers.size
        } finally {
            client.close()
        }
    }

    private suspend fun <T> withKeyedClient(
        block: suspend (client: io.ktor.client.HttpClient, apiKey: String) -> T,
    ): Result<T> = runReported("byok fetch failed") { secret ->
        val apiKey = keyStore.currentCongressApiKey()
            ?: error("No Congress.gov API key configured")
        secret.value = apiKey
        val client = createPipelineHttpClient()
        try {
            block(client, apiKey)
        } finally {
            client.close()
        }
    }

    /**
     * Runs [block] and, on failure, reports the throwable with the key
     * scrubbed out.
     *
     * [block] receives a [SecretSlot] to publish the key it read, so the
     * scrub uses the exact value that was in flight rather than re-reading
     * the encrypted keystore on the failure path — a second read could
     * return a different value (the user cleared the key mid-fetch) or
     * throw. A keyless step ([fetchVotes], [fetchCalendar]) leaves the slot
     * empty and falls through to [redactSecret]'s query-parameter backstop,
     * which is all those steps could ever need.
     */
    private suspend fun <T> runReported(
        nonFatalMessage: String,
        block: suspend (secret: SecretSlot) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        val secret = SecretSlot()
        val result = runCatching { block(secret) }
        result.exceptionOrNull()?.let { throwable ->
            reportRedactedNonFatal(
                crashReporter = crashReporter,
                throwable = throwable,
                apiKey = secret.value,
                nonFatalMessage = nonFatalMessage,
            )
        }
        result
    }
}

/**
 * Carries the API key a fetch step actually used out to its failure
 * reporting, without a second keystore decrypt. Confined to one
 * [ByokFetchOrchestrator.runReported] call, so no cross-thread visibility
 * concern beyond the coroutine's own happens-before edges.
 */
private class SecretSlot {
    var value: String? = null
}

/**
 * Hand [throwable] to [crashReporter] with [apiKey] scrubbed out of its
 * message chain first.
 *
 * `recordNonFatal` ends at `FirebaseCrashlytics.recordException`, which
 * uploads the throwable's message — and the failures that reach here are
 * network failures, the class of exception most likely to quote the
 * request. The key is kept out of the URL upstream
 * ([com.informedcitizen.pipeline.http.CongressClient]) so there should be
 * nothing to scrub; this makes that a belt-and-braces guarantee rather
 * than an invariant one refactor could quietly break.
 *
 * Extracted as a top-level function — like [publishByokMemberVotes] — so
 * it is unit-testable without an Android [Context].
 */
internal fun reportRedactedNonFatal(
    crashReporter: CrashReporter,
    throwable: Throwable,
    apiKey: String?,
    nonFatalMessage: String,
) {
    crashReporter.recordNonFatal(redactThrowable(throwable, apiKey), nonFatalMessage)
}

/**
 * Publish [savedIds]'s member vote shards into [cache] under
 * [BillSource.BYOK]. A rep with no shard on disk — no recorded votes in
 * the BYOK recent-fetch window — is skipped, so the read surface falls
 * through to its network path for that rep rather than showing an empty
 * BYOK shard. Only saved reps are published: the read surfaces query
 * per saved rep, so publishing the full ~540-member set would be wasted
 * writes. Returns the number of shards published.
 *
 * Extracted from [ByokFetchOrchestrator.fetchVotes] as a pure function
 * (shard I/O behind [loadShard]) so it is unit-testable without an
 * Android [Context].
 */
internal suspend fun publishByokMemberVotes(
    savedIds: Set<String>,
    cache: MemberVotesCache,
    fetchedAtMillis: Long,
    loadShard: (String) -> MemberVotes?,
): Int {
    var published = 0
    for (bioguideId in savedIds) {
        val shard = loadShard(bioguideId) ?: continue
        cache.replaceForSource(
            source = BillSource.BYOK,
            votes = shard,
            fetchedAtMillis = fetchedAtMillis,
        )
        published++
    }
    return published
}
