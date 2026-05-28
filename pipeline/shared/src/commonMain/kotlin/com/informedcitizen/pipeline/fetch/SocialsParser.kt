package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.model.SocialHandle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Allow-list of platform keys we surface from legislators-social-media.json.
 * Order here is the canonical output order — both Python and Kotlin parsers
 * preserve it per entry. Numeric `_id` variants (twitter_id, facebook_id,
 * youtube_id) and unknown keys (e.g. tiktok) are silently dropped.
 *
 * Mirrors Python `_common.KNOWN_SOCIAL_PLATFORMS`.
 */
val KNOWN_SOCIAL_PLATFORMS: List<String> = listOf(
    "twitter", "facebook", "youtube", "instagram", "threads", "bluesky",
)

/**
 * Extract `{bioguide → [SocialHandle, ...]}` from the body of
 * `legislators-social-media.json` (gh-pages branch of
 * unitedstates/congress-legislators). The JSON shape mirrors the YAML
 * exactly: a top-level array of `{id: {bioguide}, social: {...}}`.
 *
 * Only platforms in [KNOWN_SOCIAL_PLATFORMS] are surfaced, in that
 * constant's order. Entries with no `social` block, no bioguide, or no
 * populated known-platform handles are omitted from the output entirely.
 */
fun parseSocialsJson(text: String): Map<String, List<SocialHandle>> {
    val root = try {
        SocialsJson.parseToJsonElement(text)
    } catch (_: Throwable) {
        return emptyMap()
    }
    val array = root as? JsonArray ?: return emptyMap()
    val out = mutableMapOf<String, List<SocialHandle>>()
    for (entry in array) {
        val obj = entry as? JsonObject ?: continue
        val ids = obj["id"] as? JsonObject ?: continue
        val bioguide = (ids["bioguide"] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() } ?: continue
        val social = obj["social"] as? JsonObject ?: continue
        val handles = buildList {
            for (platform in KNOWN_SOCIAL_PLATFORMS) {
                val raw = (social[platform] as? JsonPrimitive)?.contentOrNullForString() ?: continue
                val handle = raw.trim()
                if (handle.isNotEmpty()) {
                    add(SocialHandle(platform = platform, handle = handle))
                }
            }
        }
        if (handles.isNotEmpty()) {
            out[bioguide] = handles
        }
    }
    return out
}

private fun JsonPrimitive.contentOrNullForString(): String? =
    if (isString) content else null

private val SocialsJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
