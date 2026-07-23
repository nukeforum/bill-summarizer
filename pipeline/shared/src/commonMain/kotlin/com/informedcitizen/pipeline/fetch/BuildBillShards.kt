package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.BillShard
import com.informedcitizen.pipeline.model.BillShardIndex
import com.informedcitizen.pipeline.model.BillsManifest
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * Sharded per-Congress bill manifest builder (issue #40, epic #38).
 * Parity shadow of Python `_common.build_bill_shards` /
 * `save_bill_shards`: it splits a Congress's bills into recency-first
 * pages so the published record can grow from ~256 bills to the full
 * ~10,000+ (once #39's pre-floor bills ship) without the app fetching one
 * tens-of-megabytes manifest.
 *
 * Dual-publish: the single `congress<N>_bills.json` keeps being written
 * ([FileBillsManifestStore]); the shard set
 * (`congress<N>_bills_index.json` + `congress<N>_bills_p<NNN>.json`) is
 * published alongside it. Wire shapes are [BillShardIndex] / [BillShard];
 * each shard file reuses the [BillsManifest] shape.
 */
const val SHARD_PAGE_SIZE: Int = 500

internal val SHARD_FILE_REGEX = Regex("""^congress(\d+)_bills_p(\d+)\.json$""")

/** Mirrors Python `shard_index_path_for`. */
fun shardIndexFileName(congress: Int): String = "congress${congress}_bills_index.json"

/**
 * Zero-padded, 1-based shard filename (`congress119_bills_p001.json`).
 * Mirrors Python `shard_path_for`'s `p{page:03d}`.
 */
fun shardFileName(congress: Int, page: Int): String =
    "congress${congress}_bills_p${page.toString().padStart(3, '0')}.json"

/**
 * Order bills newest-first by latest-action date, id as a stable
 * tiebreak. Mirrors Python `sort_bills_recency_first`, which sorts by id
 * asc (stable) then by date desc (stable); Kotlin's [sortedBy] /
 * [sortedByDescending] are likewise stable, so equal dates keep id-asc
 * order.
 */
fun sortBillsRecencyFirst(bills: List<Bill>): List<Bill> =
    bills.sortedBy { it.id }.sortedByDescending { it.latestAction.date }

/**
 * One shard file to publish: its filename and the [BillsManifest] page.
 */
data class BillShardFile(val name: String, val manifest: BillsManifest)

/** The full result of sharding: the index plus the ordered shard pages. */
data class BillShardSet(val index: BillShardIndex, val shardFiles: List<BillShardFile>)

/**
 * Split [bills] into recency-first pages of at most [pageSize]. Pure
 * (no disk writes). Page 1 = newest bills; only the last (oldest) page
 * may hold fewer than [pageSize]. An empty Congress yields zero shards.
 * Mirrors Python `build_bill_shards`.
 */
fun buildBillShards(
    congress: Int,
    bills: List<Bill>,
    generatedAt: String,
    votesCoverage: Boolean,
    pageSize: Int = SHARD_PAGE_SIZE,
): BillShardSet {
    require(pageSize >= 1) { "page_size must be >= 1" }
    val ordered = sortBillsRecencyFirst(bills)

    val shardEntries = mutableListOf<BillShard>()
    val shardFiles = mutableListOf<BillShardFile>()
    var start = 0
    var page = 1
    while (start < ordered.size) {
        val pageBills = ordered.subList(start, minOf(start + pageSize, ordered.size))
        val dates = pageBills.map { it.latestAction.date }.filter { it.isNotEmpty() }
        val name = shardFileName(congress, page)
        shardEntries.add(
            BillShard(
                page = page,
                path = name,
                count = pageBills.size,
                firstActionDate = dates.minOrNull(),
                lastActionDate = dates.maxOrNull(),
            ),
        )
        shardFiles.add(
            BillShardFile(
                name = name,
                manifest = BillsManifest(
                    generatedAt = generatedAt,
                    congress = congress,
                    votesCoverage = votesCoverage,
                    bills = pageBills.toList(),
                ),
            ),
        )
        start += pageSize
        page++
    }

    val index = BillShardIndex(
        generatedAt = generatedAt,
        congress = congress,
        pageSize = pageSize,
        totalBills = ordered.size,
        votesCoverage = votesCoverage,
        shards = shardEntries,
    )
    return BillShardSet(index, shardFiles)
}

/**
 * Writes the shard set for a Congress beside its single manifest.
 * Parallels [FileBillsManifestStore] but for the #40 shard files.
 * Direct port of Python `save_bill_shards`.
 */
class FileBillShardStore(
    private val fileSystem: FileSystem,
    private val outputDir: Path,
) {
    private val manifestStore = FileBillsManifestStore(fileSystem, outputDir)

    fun indexPathFor(congress: Int): Path = outputDir / shardIndexFileName(congress)

    fun shardPathFor(congress: Int, page: Int): Path = outputDir / shardFileName(congress, page)

    /**
     * Build and write the shard set for [congress]; returns the shard
     * index. Reads the on-disk single manifest when [bills] is null.
     * Writes each `congress<N>_bills_p<NNN>.json` shard plus the
     * `congress<N>_bills_index.json`, and prunes any stale shard file
     * from a previous run with more pages so a shrunk record never
     * leaves an orphaned shard the index no longer lists.
     * [votesCoverage] mirrors [FileBillsManifestStore]'s stamp.
     */
    fun save(
        congress: Int,
        bills: List<Bill>? = null,
        nowIso: String,
        pageSize: Int = SHARD_PAGE_SIZE,
    ): BillShardIndex {
        val source = bills ?: (manifestStore.load(congress)?.bills ?: emptyList())
        val votesCoverage = manifestStore.votesCoverage(congress)
        val set = buildBillShards(
            congress = congress,
            bills = source,
            generatedAt = nowIso,
            votesCoverage = votesCoverage,
            pageSize = pageSize,
        )

        fileSystem.createDirectories(outputDir)
        val currentNames = set.shardFiles.mapTo(mutableSetOf()) { it.name }
        for (path in fileSystem.list(outputDir)) {
            val match = SHARD_FILE_REGEX.matchEntire(path.name) ?: continue
            if (match.groupValues[1].toInt() == congress && path.name !in currentNames) {
                fileSystem.delete(path)
            }
        }

        for (shard in set.shardFiles) {
            val text = ManifestJson.encodeToString(BillsManifest.serializer(), shard.manifest) + "\n"
            fileSystem.sink(outputDir / shard.name).buffer().use { it.writeUtf8(text) }
        }
        val indexText = ManifestJson.encodeToString(BillShardIndex.serializer(), set.index) + "\n"
        fileSystem.sink(indexPathFor(congress)).buffer().use { it.writeUtf8(indexText) }
        return set.index
    }

    companion object {
        fun system(outputDir: Path): FileBillShardStore =
            FileBillShardStore(FileSystem.SYSTEM, outputDir)
    }
}
