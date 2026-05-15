package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.Action
import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.Outcome
import com.informedcitizen.pipeline.model.Sponsor
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test fun save_uses_two_space_indent() {
        val fs = FakeFileSystem()
        val store = FileBillsManifestStore(fs, "/out".toPath())
        store.save(119, listOf(fixture()), nowIso = "2026-05-15T00:00:00Z")
        val text = fs.source("/out/congress119_bills.json".toPath()).buffer().use { it.readUtf8() }
        // First indented line should be `  "generated_at"`.
        assertTrue("\n  \"generated_at\"" in text, "expected 2-space indent for top-level fields:\n$text")
    }
}
