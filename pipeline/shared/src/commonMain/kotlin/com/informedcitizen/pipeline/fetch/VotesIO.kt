package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Chamber
import com.informedcitizen.pipeline.model.RollCallVote
import com.informedcitizen.pipeline.model.VoteRef
import com.informedcitizen.pipeline.model.VotesIndex
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/** Mirrors Python `fetch_votes.votes_index_path`. */
fun votesIndexFileName(congress: Int): String = "congress${congress}_votes.json"

/**
 * Hosts the per-vote JSON files at
 * `<outputDir>/votes/congress<N>/<chamber>-<session>-<roll>.json` and
 * the per-Congress index at `<outputDir>/congress<N>_votes.json`.
 * Parallels [FileMemberLegislationStore] (per-record subdirectory) +
 * [FileBillsManifestStore] (top-level index).
 *
 * [voteExists] is the incremental-fetch resume cursor: published roll
 * calls are immutable, so a vote file already on disk is never
 * refetched, and [loadVotes] lets the driver rebuild the index from
 * disk at the end of every run (a crashed run heals on the next one).
 */
class FileVotesStore(
    private val fileSystem: FileSystem,
    private val outputDir: Path,
) {
    fun votesDir(congress: Int): Path = outputDir / "votes" / "congress$congress"

    fun votePathFor(congress: Int, chamber: Chamber, session: Int, rollNumber: Int): Path =
        outputDir / voteFileRelPath(congress, chamber, session, rollNumber)

    fun voteExists(congress: Int, chamber: Chamber, session: Int, rollNumber: Int): Boolean =
        fileSystem.exists(votePathFor(congress, chamber, session, rollNumber))

    fun saveVote(vote: RollCallVote): Path {
        val path = votePathFor(vote.congress, vote.chamber, vote.session, vote.rollNumber)
        path.parent?.let { fileSystem.createDirectories(it) }
        val text = ManifestJson.encodeToString(RollCallVote.serializer(), vote) + "\n"
        fileSystem.sink(path).buffer().use { it.writeUtf8(text) }
        return path
    }

    /**
     * Every vote file on disk for [congress], filename-sorted (mirrors
     * Python `rebuild_votes_index`'s sorted glob). Missing directory
     * means no votes yet — an empty list, not an error.
     */
    fun loadVotes(congress: Int): List<RollCallVote> {
        val dir = votesDir(congress)
        if (!fileSystem.exists(dir)) return emptyList()
        return fileSystem.list(dir)
            .filter { it.name.endsWith(".json") }
            .sortedBy { it.name }
            .map { path ->
                val text = fileSystem.source(path).buffer().use { it.readUtf8() }
                ManifestJson.decodeFromString(RollCallVote.serializer(), text)
            }
    }

    fun indexPathFor(congress: Int): Path = outputDir / votesIndexFileName(congress)

    /**
     * Vote refs from the on-disk index; empty when it doesn't exist.
     * Missing is normal (older Congresses, or a checkout from before
     * the votes pipeline ran) — bill records then get empty `votes`
     * lists until the index appears. Mirrors Python
     * `_common.load_vote_refs`.
     */
    fun loadVoteRefs(congress: Int): List<VoteRef> {
        val path = indexPathFor(congress)
        if (!fileSystem.exists(path)) return emptyList()
        val text = fileSystem.source(path).buffer().use { it.readUtf8() }
        return ManifestJson.decodeFromString(VotesIndex.serializer(), text).votes
    }

    fun saveIndex(index: VotesIndex): Path {
        fileSystem.createDirectories(outputDir)
        val path = indexPathFor(index.congress)
        val text = ManifestJson.encodeToString(VotesIndex.serializer(), index) + "\n"
        fileSystem.sink(path).buffer().use { it.writeUtf8(text) }
        return path
    }

    companion object {
        fun system(outputDir: Path): FileVotesStore =
            FileVotesStore(FileSystem.SYSTEM, outputDir)
    }
}
