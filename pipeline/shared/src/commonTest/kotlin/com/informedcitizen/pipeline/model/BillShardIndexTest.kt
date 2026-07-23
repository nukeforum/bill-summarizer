package com.informedcitizen.pipeline.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Matches the app's lenient wire config (NetworkModule / HttpClientFactory). */
private val LenientJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Matches ManifestJson's published-output config. */
private val PublishJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
    explicitNulls = true
}

private fun indexFixture(): BillShardIndex = BillShardIndex(
    generatedAt = "2026-07-23T00:00:00Z",
    congress = 119,
    pageSize = 500,
    totalBills = 900,
    votesCoverage = true,
    shards = listOf(
        BillShard(
            page = 1,
            path = "congress119_bills_p001.json",
            count = 500,
            firstActionDate = "2026-04-01",
            lastActionDate = "2026-07-22",
        ),
        BillShard(
            page = 2,
            path = "congress119_bills_p002.json",
            count = 400,
            firstActionDate = "2025-01-03",
            lastActionDate = "2026-03-31",
        ),
    ),
)

class BillShardIndexTest {
    @Test fun decodes_published_wire_json() {
        val json = """
            {
              "generated_at": "2026-07-23T00:00:00Z",
              "congress": 119,
              "page_size": 500,
              "total_bills": 900,
              "votes_coverage": true,
              "shards": [
                {"page": 1, "path": "congress119_bills_p001.json", "count": 500,
                 "first_action_date": "2026-04-01", "last_action_date": "2026-07-22"},
                {"page": 2, "path": "congress119_bills_p002.json", "count": 400,
                 "first_action_date": "2025-01-03", "last_action_date": "2026-03-31"}
              ]
            }
        """.trimIndent()
        val index = LenientJson.decodeFromString(BillShardIndex.serializer(), json)
        assertEquals(119, index.congress)
        assertEquals(500, index.pageSize)
        assertEquals(900, index.totalBills)
        assertEquals(true, index.votesCoverage)
        assertEquals(2, index.shards.size)
        // Page 1 is the most-recent shard.
        assertEquals(1, index.shards[0].page)
        assertEquals("congress119_bills_p001.json", index.shards[0].path)
        assertEquals("2026-07-22", index.shards[0].lastActionDate)
    }

    @Test fun roundtrips_through_publish_config() {
        val index = indexFixture()
        val encoded = PublishJson.encodeToString(BillShardIndex.serializer(), index)
        assertEquals(index, PublishJson.decodeFromString(BillShardIndex.serializer(), encoded))
    }

    @Test fun encodes_snake_case_wire_names() {
        val encoded = PublishJson.encodeToString(BillShardIndex.serializer(), indexFixture())
        assertEquals(true, "\"generated_at\": \"2026-07-23T00:00:00Z\"" in encoded)
        assertEquals(true, "\"page_size\": 500" in encoded)
        assertEquals(true, "\"total_bills\": 900" in encoded)
        assertEquals(true, "\"votes_coverage\": true" in encoded)
        assertEquals(true, "\"first_action_date\": \"2026-04-01\"" in encoded)
        assertEquals(true, "\"last_action_date\": \"2026-07-22\"" in encoded)
    }

    @Test fun lenient_decode_tolerates_absent_optionals_and_future_fields() {
        // A minimal/forward-compatible index must not crash the app: absent
        // votes_coverage/total_bills default, and an unknown key is ignored.
        val json = """
            {
              "congress": 118,
              "page_size": 500,
              "compression": "gzip",
              "shards": [
                {"page": 1, "path": "congress118_bills_p001.json", "count": 12}
              ]
            }
        """.trimIndent()
        val index = LenientJson.decodeFromString(BillShardIndex.serializer(), json)
        assertNull(index.generatedAt)
        assertEquals(false, index.votesCoverage)
        assertEquals(0, index.totalBills)
        assertEquals(1, index.shards.size)
        assertNull(index.shards[0].firstActionDate)
        assertEquals(12, index.shards[0].count)
    }

    @Test fun congresses_index_carries_optional_shard_index_path() {
        // Dual-publish: the entry keeps manifest_path AND gains shard_index_path,
        // while an un-sharded Congress omits it (decodes to null).
        val json = """
            {
              "generated_at": "2026-07-23T00:00:00Z",
              "current_congress": 119,
              "congresses": [
                {"congress": 119, "bill_count": 900, "manifest_path": "congress119_bills.json",
                 "shard_index_path": "congress119_bills_index.json", "is_current": true},
                {"congress": 118, "bill_count": 42, "manifest_path": "congress118_bills.json"}
              ]
            }
        """.trimIndent()
        val idx = LenientJson.decodeFromString(CongressesIndex.serializer(), json)
        assertEquals("congress119_bills_index.json", idx.congresses[0].shardIndexPath)
        // Unchanged whole-manifest path stays present alongside the shard index.
        assertEquals("congress119_bills.json", idx.congresses[0].manifestPath)
        // Un-sharded Congress omits the field entirely.
        assertNull(idx.congresses[1].shardIndexPath)
    }
}
