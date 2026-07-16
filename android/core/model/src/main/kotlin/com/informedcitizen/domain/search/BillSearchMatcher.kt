package com.informedcitizen.domain.search

import com.informedcitizen.pipeline.model.Bill
import com.informedcitizen.pipeline.model.MemberLegislationItem

/**
 * Case-insensitive keyword matching backing the bill search boxes. A blank
 * query matches everything; otherwise every whitespace-separated term must
 * appear in at least one searchable field, so "education committee" narrows
 * results rather than widening them. Dots are stripped from terms so a typed
 * "H.R. 1357" still matches the raw type/number pair.
 */
fun Bill.matchesSearchQuery(query: String): Boolean = allTermsMatchSomeField(
    query,
    listOf(
        title,
        shortTitle,
        summaryCrs,
        latestAction.text,
        sponsor.name,
        "$type$number",
        "$type $number",
    ),
)

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

private fun allTermsMatchSomeField(query: String, fields: List<String?>): Boolean {
    val terms = query.split(whitespace).mapNotNull { raw ->
        raw.replace(".", "").takeIf { it.isNotEmpty() }
    }
    return terms.all { term -> fields.any { it?.contains(term, ignoreCase = true) == true } }
}
