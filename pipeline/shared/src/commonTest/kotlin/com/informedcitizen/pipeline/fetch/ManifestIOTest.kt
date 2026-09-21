package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.LifecycleStatus
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.Sponsor
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun fixture(id: String = "hr1234-119"): Bill = Bill(
    id = id,
    congress = 119,
    type = "hr",
    number = "1234",
    title = "Some Title",
    shortTitle = null,
    sponsor = Sponsor("Rep. Smith, X", "R", "NE"),
    introducedDate = "2026-01-01",
    latestAction = Action("2026-04-01", "Became Public Law No: 119-1."),
    outcome = Outcome.ENACTED,
    summaryCrs = null,
    textUrlHtml = null,
    textUrlXml = null,
    textUrlPdf = null,
    congressGovUrl = "https://www.congress.gov/bill/119th-congress/house-bill/1234",
)

class ManifestIOTest {
    @Test fun manifest_file_name_pattern_matches_python() {
        assertEquals("congress119_bills.json", manifestFileName(119))
        assertEquals("congress93_bills.json", manifestFileName(93))
    }

    @Test fun load_returns_null_when_file_missing() {
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        assertNull(store.load(119))
    }

    @Test fun save_then_load_roundtrip() {
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        val manifest = store.save(119, listOf(fixture()), nowIso = "2026-05-15T00:00:00Z")
        assertEquals(1, manifest.bills.size)
        val loaded = store.load(119)
        assertNotNull(loaded)
        assertEquals(manifest, loaded)
    }

    @Test fun save_creates_output_dir_if_missing() {
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/data/out".toPath())
        store.save(119, listOf(fixture()), nowIso = "2026-05-15T00:00:00Z")
        assertTrue(fs.exists("/data/out".toPath()))
        assertTrue(fs.exists("/data/out/congress119_bills.json".toPath()))
    }

    @Test fun save_writes_trailing_newline() {
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        store.save(119, listOf(fixture()), nowIso = "2026-05-15T00:00:00Z")
        val text = fs.source("/out/congress119_bills.json".toPath()).buffer().use { it.readUtf8() }
        assertTrue(text.endsWith("\n"), "expected trailing newline, got: ${text.takeLast(20)}")
    }

    @Test fun save_writes_short_title_null_explicitly() {
        // Byte-parity: Python writes `"short_title": null`; Kotlin must too,
        // otherwise the JSON shape diverges during parallel-run period.
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        store.save(119, listOf(fixture()), nowIso = "2026-05-15T00:00:00Z")
        val text = fs.source("/out/congress119_bills.json".toPath()).buffer().use { it.readUtf8() }
        assertTrue("\"short_title\": null" in text, "missing explicit null short_title:\n$text")
        assertTrue("\"text_url_html\": null" in text, "missing explicit null text_url_html:\n$text")
    }

    @Test fun save_omits_policy_area_when_null() {
        // Byte-parity: Python omits the `policy_area` key entirely when the
        // value is absent (issue #74), while KEEPING every other null field
        // explicit. Kotlin must match, otherwise a carried-forward bill that
        // decodes to a null policyArea re-serializes as `"policy_area": null`
        // and diverges from the Python canonical output.
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        store.save(119, listOf(fixture()), nowIso = "2026-05-15T00:00:00Z")
        val text = fs.source("/out/congress119_bills.json".toPath()).buffer().use { it.readUtf8() }
        assertTrue("policy_area" !in text, "null policy_area must be omitted, not written:\n$text")
        // The other null-valued fields must still be written explicitly.
        assertTrue("\"short_title\": null" in text, "short_title null must stay explicit:\n$text")
        assertTrue("\"summary_crs\": null" in text, "summary_crs null must stay explicit:\n$text")
    }

    @Test fun save_writes_policy_area_when_present() {
        // The omit rule is null-only: a real policy_area value is written.
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        store.save(
            119,
            listOf(fixture().copy(policyArea = "Taxation")),
            nowIso = "2026-05-15T00:00:00Z",
        )
        val text = fs.source("/out/congress119_bills.json".toPath()).buffer().use { it.readUtf8() }
        assertTrue("\"policy_area\": \"Taxation\"" in text, "present policy_area must be written:\n$text")
    }

    @Test fun save_then_load_roundtrip_with_omitted_policy_area() {
        // Omitting the key on write must not break the read round-trip: a
        // manifest without policy_area decodes back to a null policyArea.
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        val manifest = store.save(119, listOf(fixture()), nowIso = "2026-05-15T00:00:00Z")
        val loaded = store.load(119)
        assertNotNull(loaded)
        assertEquals(manifest, loaded)
        assertNull(loaded.bills.single().policyArea)
    }

    @Test fun save_omits_status_when_null() {
        // Byte-parity (issue #116): Python's build_bill_record deletes a null
        // `status` exactly as it deletes a null `policy_area`, so Kotlin must
        // omit the key too. Without this, a carried-forward bill decoded into a
        // null lifecycleStatus re-serializes as `"status": null` and diverges
        // from every manifest Python ever wrote.
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        store.save(119, listOf(fixture()), nowIso = "2026-05-15T00:00:00Z")
        val text = fs.source("/out/congress119_bills.json".toPath()).buffer().use { it.readUtf8() }
        assertTrue("\"status\"" !in text, "null status must be omitted, not written:\n$text")
        // The other null-valued fields must still be written explicitly.
        assertTrue("\"short_title\": null" in text, "short_title null must stay explicit:\n$text")
        assertTrue("\"summary_crs\": null" in text, "summary_crs null must stay explicit:\n$text")
    }

    @Test fun save_writes_status_when_present() {
        // The omit rule is null-only: a real lifecycle status is written, in
        // the declared position between `outcome` and `policy_area` — the key
        // order Python's record dict produces.
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        store.save(
            119,
            listOf(fixture().copy(lifecycleStatus = LifecycleStatus.IN_COMMITTEE, policyArea = "Taxation")),
            nowIso = "2026-05-15T00:00:00Z",
        )
        val text = fs.source("/out/congress119_bills.json".toPath()).buffer().use { it.readUtf8() }
        assertTrue("\"status\": \"in_committee\"" in text, "present status must be written:\n$text")
        assertTrue(
            text.indexOf("\"outcome\"") < text.indexOf("\"status\"") &&
                text.indexOf("\"status\"") < text.indexOf("\"policy_area\""),
            "status must sit between outcome and policy_area:\n$text",
        )
    }

    @Test fun save_omits_both_status_and_policy_area_when_both_null() {
        // Dropping one key must not resurrect the other: the transform removes
        // every null key in one pass and leaves the surviving order intact.
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        store.save(119, listOf(fixture()), nowIso = "2026-05-15T00:00:00Z")
        val text = fs.source("/out/congress119_bills.json".toPath()).buffer().use { it.readUtf8() }
        assertTrue("\"status\"" !in text, "null status must be omitted:\n$text")
        assertTrue("policy_area" !in text, "null policy_area must be omitted:\n$text")
        assertTrue(
            text.indexOf("\"outcome\"") < text.indexOf("\"subjects\""),
            "surviving key order must be unchanged:\n$text",
        )
    }

    @Test fun save_then_load_roundtrip_with_omitted_status() {
        // Omitting the key on write must not break the read round-trip: a
        // manifest without `status` decodes back to a null lifecycleStatus.
        // This is the additive decoding contract — manifests published before
        // the field existed must keep decoding unchanged.
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        val manifest = store.save(119, listOf(fixture()), nowIso = "2026-05-15T00:00:00Z")
        val loaded = store.load(119)
        assertNotNull(loaded)
        assertEquals(manifest, loaded)
        assertNull(loaded.bills.single().lifecycleStatus)
    }

    @Test fun load_then_save_normalizes_an_explicit_null_status() {
        // The #116 regression, reproduced end to end: a manifest carrying the
        // `"status": null` the Kotlin votes writer used to stamp on must be
        // rewritten WITHOUT the key, so a single pipeline run converges on the
        // Python-canonical shape instead of perpetuating the divergence.
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        store.save(119, listOf(fixture()), nowIso = "2026-05-15T00:00:00Z")
        val path = "/out/congress119_bills.json".toPath()
        val stamped = fs.source(path).buffer().use { it.readUtf8() }
            .replace("\"outcome\": \"enacted\",", "\"outcome\": \"enacted\",\n      \"status\": null,")
        fs.write(path) { writeUtf8(stamped) }
        assertTrue("\"status\": null" in stamped, "precondition: fixture must carry the stray null")

        val reloaded = store.load(119)
        assertNotNull(reloaded)
        store.save(119, reloaded.bills, nowIso = "2026-05-15T00:00:00Z")
        val rewritten = fs.source(path).buffer().use { it.readUtf8() }
        assertTrue("\"status\"" !in rewritten, "rewrite must drop the stray null status:\n$rewritten")
    }

    @Test fun save_stamps_votes_coverage_from_index_presence() {
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())

        store.save(119, listOf(fixture()), nowIso = "2026-05-15T00:00:00Z")
        var text = fs.source("/out/congress119_bills.json".toPath()).buffer().use { it.readUtf8() }
        assertTrue("\"votes_coverage\": false" in text, "expected coverage false without index:\n$text")

        fs.write("/out/congress119_votes.json".toPath()) { writeUtf8("{}") }
        store.save(119, listOf(fixture()), nowIso = "2026-05-15T00:00:00Z")
        text = fs.source("/out/congress119_bills.json".toPath()).buffer().use { it.readUtf8() }
        assertTrue("\"votes_coverage\": true" in text, "expected coverage true with index:\n$text")
        // Key order matches Python save_manifest for byte parity.
        assertTrue(
            text.indexOf("\"congress\"") < text.indexOf("\"votes_coverage\"") &&
                text.indexOf("\"votes_coverage\"") < text.indexOf("\"bills\""),
            "votes_coverage must sit between congress and bills:\n$text",
        )
    }

    @Test fun load_tolerates_unknown_manifest_field() {
        // A manifest carrying a field the Kotlin models don't know yet
        // (forward-compatible pipeline addition) must decode as the bills
        // it holds, mirroring the app's lenient wire parsing — not read as
        // an absent file and trigger a rebuild-from-empty parity diff.
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        store.save(119, listOf(fixture()), nowIso = "2026-05-15T00:00:00Z")
        val original = fs.source("/out/congress119_bills.json".toPath()).buffer().use { it.readUtf8() }
        val withExtra = original.replaceFirst(
            "{\n  \"generated_at\"",
            "{\n  \"future_field\": {\"nested\": [1, 2, 3]},\n  \"generated_at\"",
        )
        fs.write("/out/congress119_bills.json".toPath()) { writeUtf8(withExtra) }

        val loaded = store.load(119)
        assertNotNull(loaded, "unknown field must not read as an absent manifest")
        assertEquals(1, loaded.bills.size)
        assertEquals("hr1234-119", loaded.bills.single().id)
    }

    @Test fun load_throws_on_corrupt_manifest_rather_than_reporting_absent() {
        // A present-but-unparseable manifest must surface loudly, not
        // masquerade as an absent file (which would silently rebuild from
        // empty). Distinguishes corruption from absence.
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        fs.createDirectories("/out".toPath())
        fs.write("/out/congress119_bills.json".toPath()) { writeUtf8("{ not valid json") }
        assertFailsWith<IllegalStateException> { store.load(119) }
    }

    @Test fun save_uses_two_space_indent() {
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        store.save(119, listOf(fixture()), nowIso = "2026-05-15T00:00:00Z")
        val text = fs.source("/out/congress119_bills.json".toPath()).buffer().use { it.readUtf8() }
        // First indented line should be `  "generated_at"`.
        assertTrue("\n  \"generated_at\"" in text, "expected 2-space indent for top-level fields:\n$text")
    }
}
