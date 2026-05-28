package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.SocialHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SocialsParserTest {

    @Test
    fun `extracts big four in known order`() {
        val json = """
            [
              {
                "id": {"bioguide": "F000483"},
                "social": {
                  "instagram": "replaurafriedman",
                  "facebook":  "RepLauraFriedman",
                  "twitter":   "RepLauraFriedman",
                  "youtube":   "@laurafriedman"
                }
              }
            ]
        """.trimIndent()
        val out = parseSocialsJson(json)
        assertEquals(
            mapOf(
                "F000483" to listOf(
                    SocialHandle(platform = "twitter",   handle = "RepLauraFriedman"),
                    SocialHandle(platform = "facebook",  handle = "RepLauraFriedman"),
                    SocialHandle(platform = "youtube",   handle = "@laurafriedman"),
                    SocialHandle(platform = "instagram", handle = "replaurafriedman"),
                ),
            ),
            out,
        )
    }

    @Test
    fun `includes only populated platforms`() {
        val json = """
            [{"id":{"bioguide":"A000001"},"social":{"twitter":"SenAlpha","youtube":"@senalpha"}}]
        """.trimIndent()
        val out = parseSocialsJson(json)
        assertEquals(
            mapOf(
                "A000001" to listOf(
                    SocialHandle("twitter", "SenAlpha"),
                    SocialHandle("youtube", "@senalpha"),
                ),
            ),
            out,
        )
    }

    @Test
    fun `includes forward-compat threads and bluesky`() {
        val json = """
            [{"id":{"bioguide":"B000002"},"social":{"twitter":"SenBeta","threads":"senbeta","bluesky":"senbeta.bsky.social"}}]
        """.trimIndent()
        val out = parseSocialsJson(json)
        assertEquals(
            mapOf(
                "B000002" to listOf(
                    SocialHandle("twitter", "SenBeta"),
                    SocialHandle("threads", "senbeta"),
                    SocialHandle("bluesky", "senbeta.bsky.social"),
                ),
            ),
            out,
        )
    }

    @Test
    fun `drops unknown platforms and id variants`() {
        val json = """
            [{"id":{"bioguide":"C000003"},"social":{"twitter":"SenGamma","twitter_id":12345,"facebook_id":67890,"tiktok":"sengamma"}}]
        """.trimIndent()
        val out = parseSocialsJson(json)
        assertEquals(
            mapOf("C000003" to listOf(SocialHandle("twitter", "SenGamma"))),
            out,
        )
    }

    @Test
    fun `skips entries without bioguide id`() {
        val json = """
            [
              {"id":{"govtrack":99999},"social":{"twitter":"nobody"}},
              {"id":{"bioguide":"D000004"},"social":{"facebook":"SenDelta"}}
            ]
        """.trimIndent()
        val out = parseSocialsJson(json)
        assertEquals(
            mapOf("D000004" to listOf(SocialHandle("facebook", "SenDelta"))),
            out,
        )
    }

    @Test
    fun `skips entries with no social block`() {
        val json = """
            [{"id":{"bioguide":"E000005"}}]
        """.trimIndent()
        val out = parseSocialsJson(json)
        assertTrue(out.isEmpty(), "expected empty map, got $out")
    }

    @Test
    fun `handles blank or non-array input`() {
        assertTrue(parseSocialsJson("").isEmpty())
        assertTrue(parseSocialsJson("[]").isEmpty())
        assertTrue(parseSocialsJson("{}").isEmpty())
    }
}
