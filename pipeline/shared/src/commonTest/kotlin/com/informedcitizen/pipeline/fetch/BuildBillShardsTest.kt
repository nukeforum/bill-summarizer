package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillsManifest
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.Sponsor
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Parity shadow of `data-pipeline/tests/test_bill_shards.py`. Mirrors the
 * Python-canonical #40 shard builder case-for-case so the two pipelines
 * emit byte-identical shard sets during the parallel-run period.
 */
class BuildBillShardsTest {

    private fun bill(id: String, actionDate: String): Bill = Bill(
        id = id,
        congress = 119,
        type = "hr",
        number = "1",
        title = "Title",
        shortTitle = null,
        sponsor = Sponsor("Rep. Doe, J", "D", "CA"),
        introducedDate = "2025-01-01",
        latestAction = Action(actionDate, "Some action."),
        outcome = Outcome.ENACTED,
        summaryCrs = null,
        textUrlHtml = null,
        textUrlXml = null,
        textUrlPdf = null,
        congressGovUrl = "https://www.congress.gov/bill/$id",
    )

    @Test fun sort_bills_recency_first_newest_first_id_tiebreak() {
        val bills = listOf(
            bill("hr1-119", "2025-03-01"),
            bill("s2-119", "2026-04-30"),
            bill("hr9-119", "2026-04-30"),
            bill("hr3-119", "2024-01-01"),
        )
        val ordered = sortBillsRecencyFirst(bills)
        // Same-date pair (s2, hr9) breaks by id asc -> hr9 before s2.
        assertEquals(listOf("hr9-119", "s2-119", "hr1-119", "hr3-119"), ordered.map { it.id })
    }

    @Test fun build_bill_shards_pages_and_windows() {
        val bills = (1..5).map { bill("hr$it-119", "2026-" + ((it % 12) + 1).toString().padStart(2, '0') + "-01") }
        val set = buildBillShards(
            congress = 119,
            bills = bills,
            generatedAt = "2026-07-23T00:00:00Z",
            votesCoverage = true,
            pageSize = 2,
        )
        val index = set.index
        assertEquals(119, index.congress)
        assertEquals(2, index.pageSize)
        assertEquals(5, index.totalBills)
        assertTrue(index.votesCoverage)
        // 5 bills / page 2 -> 3 shards, only the last is short.
        assertEquals(listOf(2, 2, 1), index.shards.map { it.count })
        assertEquals(listOf(1, 2, 3), index.shards.map { it.page })
        assertEquals(
            listOf(
                "congress119_bills_p001.json",
                "congress119_bills_p002.json",
                "congress119_bills_p003.json",
            ),
            index.shards.map { it.path },
        )
        // Each shard's window bounds its own dates; page 1 holds the newest.
        for ((entry, file) in index.shards.zip(set.shardFiles)) {
            val dates = file.manifest.bills.map { it.latestAction.date }
            assertEquals(dates.min(), entry.firstActionDate)
            assertEquals(dates.max(), entry.lastActionDate)
        }
        assertTrue(index.shards[0].lastActionDate!! >= index.shards[1].lastActionDate!!)
    }

    @Test fun build_bill_shards_shard_files_reuse_manifest_shape() {
        val bills = listOf(bill("hr1-119", "2026-05-01"))
        val set = buildBillShards(
            congress = 119,
            bills = bills,
            generatedAt = "2026-07-23T00:00:00Z",
            votesCoverage = false,
            pageSize = 500,
        )
        val file = set.shardFiles.single()
        assertEquals("congress119_bills_p001.json", file.name)
        // Shard file is exactly a BillsManifest (same wire shape).
        assertEquals(
            BillsManifest(
                generatedAt = "2026-07-23T00:00:00Z",
                congress = 119,
                votesCoverage = false,
                bills = bills,
            ),
            file.manifest,
        )
    }

    @Test fun build_bill_shards_empty_congress_no_shards() {
        val set = buildBillShards(
            congress = 119,
            bills = emptyList(),
            generatedAt = "2026-07-23T00:00:00Z",
            votesCoverage = false,
        )
        assertEquals(0, set.index.totalBills)
        assertTrue(set.index.shards.isEmpty())
        assertTrue(set.shardFiles.isEmpty())
    }

    @Test fun build_bill_shards_rejects_bad_page_size() {
        assertFailsWith<IllegalArgumentException> {
            buildBillShards(
                congress = 119,
                bills = emptyList(),
                generatedAt = "2026-07-23T00:00:00Z",
                votesCoverage = false,
                pageSize = 0,
            )
        }
    }

    @Test fun save_writes_index_and_shards() {
        val fs = FakeFileSystem()
        val store = FileBillShardStore(fs, "/out".toPath())
        val bills = (1..3).map { bill("hr$it-119", "2026-0$it-01") }
        val index = store.save(119, bills, nowIso = "2026-07-23T00:00:00Z", pageSize = 2)

        val idxPath = "/out/congress119_bills_index.json".toPath()
        assertTrue(fs.exists(idxPath))
        val written = ManifestReadJson.decodeFromString(
            com.informedcitizen.pipeline.model.BillShardIndex.serializer(),
            fs.source(idxPath).buffer().use { it.readUtf8() },
        )
        assertEquals(index, written)
        assertTrue(fs.exists("/out/congress119_bills_p001.json".toPath()))
        assertTrue(fs.exists("/out/congress119_bills_p002.json".toPath()))
        val shard1 = ManifestReadJson.decodeFromString(
            BillsManifest.serializer(),
            fs.source("/out/congress119_bills_p001.json".toPath()).buffer().use { it.readUtf8() },
        )
        assertEquals(2, shard1.bills.size)
    }

    @Test fun save_prunes_stale_shards() {
        val fs = FakeFileSystem()
        val store = FileBillShardStore(fs, "/out".toPath())
        // First run: 3 bills, page 1 -> 3 shards.
        store.save(119, (1..3).map { bill("hr$it-119", "2026-05-01") }, nowIso = "2026-07-23T00:00:00Z", pageSize = 1)
        assertTrue(fs.exists("/out/congress119_bills_p003.json".toPath()))
        // Second run: fewer bills -> the orphaned third shard is pruned.
        store.save(119, listOf(bill("hr1-119", "2026-05-01")), nowIso = "2026-07-23T00:00:00Z", pageSize = 1)
        assertTrue(fs.exists("/out/congress119_bills_p001.json".toPath()))
        assertFalse(fs.exists("/out/congress119_bills_p002.json".toPath()))
        assertFalse(fs.exists("/out/congress119_bills_p003.json".toPath()))
    }

    @Test fun save_reads_on_disk_manifest_when_bills_null() {
        val fs = FakeFileSystem()
        FileBillsManifestStore(fs, "/out".toPath())
            .save(119, listOf(bill("hr1-119", "2026-05-01")), nowIso = "x")
        val index = FileBillShardStore(fs, "/out".toPath())
            .save(119, nowIso = "2026-07-23T00:00:00Z")
        assertEquals(1, index.totalBills)
        assertEquals(1, index.shards.size)
    }

    @Test fun save_stamps_votes_coverage_from_disk() {
        // votes_coverage mirrors FileBillsManifestStore: true only when a
        // congress<N>_votes.json index sits beside the manifest.
        val fs = FakeFileSystem()
        fs.createDirectories("/out".toPath())
        fs.sink("/out/congress119_votes.json".toPath()).buffer().use { it.writeUtf8("{}") }
        val index = FileBillShardStore(fs, "/out".toPath())
            .save(119, listOf(bill("hr1-119", "2026-05-01")), nowIso = "2026-07-23T00:00:00Z")
        assertTrue(index.votesCoverage)
    }

    @Test fun index_byte_parity_field_order() {
        // BillShardIndex + BillShard field order mirrors the Python dict
        // literals so the shadow byte-diff stays clean.
        val fs = FakeFileSystem()
        FileBillShardStore(fs, "/out".toPath())
            .save(119, listOf(bill("hr1-119", "2026-05-01")), nowIso = "2026-07-23T00:00:00Z")
        val text = fs.source("/out/congress119_bills_index.json".toPath()).buffer().use { it.readUtf8() }
        assertTrue(text.endsWith("\n"), "expected trailing newline")
        val order = listOf(
            "\"generated_at\"",
            "\"congress\"",
            "\"page_size\"",
            "\"total_bills\"",
            "\"votes_coverage\"",
            "\"shards\"",
            "\"page\"",
            "\"path\"",
            "\"count\"",
            "\"first_action_date\"",
            "\"last_action_date\"",
        )
        var lastIdx = -1
        for (key in order) {
            val idx = text.indexOf(key, startIndex = lastIdx + 1)
            assertTrue(idx > lastIdx, "expected $key after position $lastIdx; full text:\n$text")
            lastIdx = idx
        }
    }
}
