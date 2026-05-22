package com.informedcitizen.pipeline.fetch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Per-legislator contact info pulled from legislators-current.json. */
data class LegislatorContactInfo(
    val contactForm: String?,
    val website: String?,
)

/**
 * Extract `{bioguide → {contactForm, website}}` from the body of
 * `legislators-current.json` (gh-pages branch of
 * unitedstates/congress-legislators).
 *
 * `contact_form` is read from the **current (last) term only**.
 * `url` (homepage) walks terms in reverse and picks the most recent
 * non-empty value — stale homepages remain useful, stale contact forms
 * usually 404. See Python `_common.parse_contact_info_yaml` for the
 * 2026-05-13 investigation that drove the asymmetry.
 *
 * The JSON shape (gh-pages) mirrors the YAML shape exactly: a top-level
 * array of `{id: {bioguide}, terms: [...]}`. Entries missing a bioguide
 * are skipped silently.
 */
fun parseContactInfoJson(text: String): Map<String, LegislatorContactInfo> {
    val root = ContactInfoJson.parseToJsonElement(text) as? JsonArray
        ?: return emptyMap()
    val out = mutableMapOf<String, LegislatorContactInfo>()
    for (entry in root) {
        val obj = entry as? JsonObject ?: continue
        val ids = obj.jsonObjectField("id") ?: continue
        val bioguide = ids.stringField("bioguide")?.takeIf { it.isNotEmpty() } ?: continue
        val terms = obj["terms"] as? JsonArray ?: continue
        val currentTerm = terms.lastOrNull() as? JsonObject ?: JsonObject(emptyMap())
        val contactForm = currentTerm.stringField("contact_form")?.takeIf { it.isNotEmpty() }
        var website: String? = null
        for (i in terms.indices.reversed()) {
            val term = terms[i] as? JsonObject ?: continue
            val w = term.stringField("url")?.takeIf { it.isNotEmpty() } ?: continue
            website = w
            break
        }
        out[bioguide] = LegislatorContactInfo(contactForm = contactForm, website = website)
    }
    return out
}

/**
 * Lenient JSON parser shared across `legislators-current.json` reads.
 * The upstream file occasionally adds new term-level fields; never let
 * an unknown key be the reason contact-info import fails.
 */
private val ContactInfoJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
