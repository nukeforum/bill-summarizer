package com.informedcitizen.domain.search

import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.MemberLegislationItem

/**
 * Case-insensitive keyword matching backing the bill search boxes. A blank
 * query matches everything; otherwise every whitespace-separated term must
 * appear in at least one searchable field, so "education committee" narrows
 * results rather than widening them. Dots are stripped from terms so a typed
 * "H.R. 1357" still matches the raw type/number pair. The finer-grained
 * legislative [Bill.subjects] (issue #28) are searchable too, so a typed
 * subject keyword surfaces subject-tagged bills the same way the dedicated
 * subject filter chip does.
 */
fun Bill.matchesSearchQuery(query: String): Boolean = allTermsMatchSomeField(
    query,
    listOf(
        title,
        shortTitle,
        summaryCrs,
        policyArea,
        latestAction.text,
        sponsor.name,
        "$type$number",
        "$type $number",
        subjects.joinToString(" "),
    ),
)

/**
 * Relevance score for ranking the bills that already pass [matchesSearchQuery]
 * against [query]; higher is more relevant, a blank query scores 0 (no ranking).
 *
 * A term found in a high-signal field — the title, short title, policy area,
 * legislative [Bill.subjects], or the bill number — scores far higher than a
 * term that only appears buried in the long CRS summary or a procedural
 * latest-action line. So a bill genuinely *about* the query ranks above one that
 * merely mentions the words in passing (the "student loans" query that matched a
 * tangential appropriations bill naming the phrase once in its summary). A
 * contiguous whole-query phrase hit in the title/short title adds a further
 * bonus, so an exact-title match floats to the top.
 */
fun Bill.searchRelevance(query: String): Int {
    val terms = queryTerms(query)
    if (terms.isEmpty()) return 0
    val fields = listOf(
        WeightedField(TITLE_WEIGHT, title),
        WeightedField(TITLE_WEIGHT, shortTitle),
        WeightedField(TOPIC_WEIGHT, subjects.joinToString(" ")),
        WeightedField(TOPIC_WEIGHT, policyArea),
        WeightedField(NUMBER_WEIGHT, "$type$number"),
        WeightedField(NUMBER_WEIGHT, "$type $number"),
        WeightedField(SPONSOR_WEIGHT, sponsor.name),
        WeightedField(MENTION_WEIGHT, summaryCrs),
        WeightedField(MENTION_WEIGHT, latestAction.text),
    )
    // Each term contributes the weight of the strongest field it appears in, so
    // a term in the title outweighs the same term appearing only in the summary.
    var score = terms.sumOf { term ->
        fields.filter { it.text?.contains(term, ignoreCase = true) == true }
            .maxOfOrNull { it.weight } ?: 0
    }
    val phrase = terms.joinToString(" ")
    if (title.contains(phrase, ignoreCase = true) || shortTitle?.contains(phrase, ignoreCase = true) == true) {
        score += PHRASE_BONUS
    }
    return score
}

private class WeightedField(val weight: Int, val text: String?)

private const val TITLE_WEIGHT = 8
private const val TOPIC_WEIGHT = 6
private const val NUMBER_WEIGHT = 5
private const val SPONSOR_WEIGHT = 3
private const val MENTION_WEIGHT = 1
private const val PHRASE_BONUS = 12

fun MemberLegislationItem.matchesSearchQuery(query: String): Boolean = allTermsMatchSomeField(
    query,
    listOf(
        title,
        policyArea,
        latestAction.text,
        "$type$number",
        "$type $number",
    ),
)

private val whitespace = Regex("\\s+")

/** Whitespace-split, dot-stripped, non-empty query terms shared by the matcher and the ranker. */
private fun queryTerms(query: String): List<String> = query.split(whitespace).mapNotNull { raw ->
    raw.replace(".", "").takeIf { it.isNotEmpty() }
}

private fun allTermsMatchSomeField(query: String, fields: List<String?>): Boolean {
    val terms = queryTerms(query)
    return terms.all { term -> fields.any { it?.contains(term, ignoreCase = true) == true } }
}
