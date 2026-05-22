package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.pipeline.model.MemberLegislation
import com.informedcitizen.pipeline.model.MemberLegislationItem
import com.informedcitizen.pipeline.model.MembersIndex
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/** Mirrors Python `_common.members_index_path`. Three-digit zero-padded. */
fun membersIndexFileName(congress: Int): String =
    "members_${congress.toString().padStart(3, '0')}.json"

/** Mirrors Python `_common.member_legislation_path`. */
fun memberLegislationFileName(bioguideId: String, kind: String): String =
    "${bioguideId}_$kind.json"

/** Top-level subdirectory for per-member legislation files. Matches Python `MEMBERS_SUBDIR`. */
const val MEMBERS_SUBDIR: String = "members"

/**
 * Hosts the per-Congress members index at
 * `<outputDir>/members_NNN.json`. Parallels [FileBillsManifestStore].
 */
class FileMembersIndexStore(
    private val fileSystem: FileSystem,
    private val outputDir: Path,
) {
    fun pathFor(congress: Int): Path = outputDir / membersIndexFileName(congress)

    fun load(congress: Int): MembersIndex? {
        val path = pathFor(congress)
        if (!fileSystem.exists(path)) return null
        return try {
            val text = fileSystem.source(path).buffer().use { it.readUtf8() }
            ManifestJson.decodeFromString(MembersIndex.serializer(), text)
        } catch (_: Throwable) {
            null
        }
    }

    fun save(congress: Int, members: List<Member>, nowIso: String): MembersIndex {
        val index = MembersIndex(
            congress = congress,
            generatedAt = nowIso,
            members = members,
        )
        fileSystem.createDirectories(outputDir)
        val text = ManifestJson.encodeToString(MembersIndex.serializer(), index) + "\n"
        fileSystem.sink(pathFor(congress)).buffer().use { it.writeUtf8(text) }
        return index
    }

    companion object {
        fun system(outputDir: Path): FileMembersIndexStore =
            FileMembersIndexStore(FileSystem.SYSTEM, outputDir)
    }
}

/**
 * Hosts per-member sponsored/cosponsored legislation files at
 * `<outputDir>/members/{bioguideId}_{kind}.json`.
 *
 * The orchestrator uses [exists] to skip already-cached members so a
 * partial Phase 2 run is resumable across re-invocations.
 */
class FileMemberLegislationStore(
    private val fileSystem: FileSystem,
    private val outputDir: Path,
) {
    fun pathFor(bioguideId: String, kind: String): Path =
        outputDir / MEMBERS_SUBDIR / memberLegislationFileName(bioguideId, kind)

    fun exists(bioguideId: String, kind: String): Boolean =
        fileSystem.exists(pathFor(bioguideId, kind))

    fun save(
        bioguideId: String,
        kind: String,
        congress: Int,
        bills: List<MemberLegislationItem>,
        nowIso: String,
    ): MemberLegislation {
        require(kind == "sponsored" || kind == "cosponsored") {
            "unknown kind: '$kind' (expected 'sponsored' or 'cosponsored')"
        }
        val payload = MemberLegislation(
            bioguideId = bioguideId,
            congress = congress,
            kind = kind,
            generatedAt = nowIso,
            bills = bills,
        )
        val path = pathFor(bioguideId, kind)
        path.parent?.let { fileSystem.createDirectories(it) }
        val text = ManifestJson.encodeToString(MemberLegislation.serializer(), payload) + "\n"
        fileSystem.sink(path).buffer().use { it.writeUtf8(text) }
        return payload
    }

    companion object {
        fun system(outputDir: Path): FileMemberLegislationStore =
            FileMemberLegislationStore(FileSystem.SYSTEM, outputDir)
    }
}
