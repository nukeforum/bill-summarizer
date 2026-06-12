package com.informedcitizen.data.byok

import android.content.Context
import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.data.repository.BillRepository
import com.informedcitizen.data.repository.CachedMemberRepository
import com.informedcitizen.data.repository.SessionCalendarRepository
import com.informedcitizen.pipeline.ErrorCollector
import com.informedcitizen.pipeline.congressForYear
import com.informedcitizen.pipeline.fetch.FileBillsManifestStore
import com.informedcitizen.pipeline.fetch.FileMemberLegislationStore
import com.informedcitizen.pipeline.fetch.FileMembersIndexStore
import com.informedcitizen.pipeline.fetch.RECENT_DAYS
import com.informedcitizen.pipeline.fetch.buildSessionCalendar
import com.informedcitizen.pipeline.fetch.fetchBills
import com.informedcitizen.pipeline.fetch.fetchMembers
import com.informedcitizen.pipeline.fetch.nowIso
import com.informedcitizen.pipeline.http.CongressClient
import com.informedcitizen.pipeline.http.LegislatorsClient
import com.informedcitizen.pipeline.http.SessionCalendarClient
import com.informedcitizen.pipeline.http.createPipelineHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
    private val crashReporter: CrashReporter,
) {
    private val workDir: File
        get() = File(context.filesDir, "byok-pipeline").apply { mkdirs() }

    /** Daily refresh of the current-Congress bills manifest. */
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
        )
        billRepository.publishByokBills(result.finalManifest)
        result.finalManifest.bills.size
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
    suspend fun fetchCalendar(): Result<Int> = runReported("byok calendar fetch failed") {
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
    ): Result<T> = runReported("byok fetch failed") {
        val apiKey = keyStore.currentCongressApiKey()
            ?: error("No Congress.gov API key configured")
        val client = createPipelineHttpClient()
        try {
            block(client, apiKey)
        } finally {
            client.close()
        }
    }

    private suspend fun <T> runReported(
        nonFatalMessage: String,
        block: suspend () -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching { block() }
            .onFailure { crashReporter.recordNonFatal(it, nonFatalMessage) }
    }
}
