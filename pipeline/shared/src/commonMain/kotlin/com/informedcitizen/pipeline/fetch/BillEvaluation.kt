package com.informedcitizen.pipeline.fetch

import com.informedcitizen.pipeline.classifyOutcome
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Outcome of [evaluateBill]. Mirrors Python `evaluate_bill`'s `(outcome, reason)` tuple. */
sealed class BillEvaluationResult {
    data class Kept(val outcome: String) : BillEvaluationResult()
    data class Rejected(val reason: String) : BillEvaluationResult()
}

object RejectionReasons {
    const val MISSING_TYPE_OR_NUMBER = "missing_type_or_number"
    const val NO_OUTCOME_MATCH = "no_outcome_match"
    const val UNPARSEABLE_ACTION_DATE = "unparseable_action_date"
    const val ACTION_TOO_OLD = "action_too_old"
    const val DUPLICATE = "duplicate"
    const val BUILD_ERROR = "build_error"
    const val KEPT = "kept"
}

/**
 * Filter a list-endpoint bill summary against the outcome rules and
 * the freshness [cutoff]. Pure: takes the raw `JsonObject` shape
 * Congress.gov returns. Returns either [BillEvaluationResult.Kept]
 * with the matched outcome string, or [BillEvaluationResult.Rejected]
 * tagged with one of [RejectionReasons]. Mirrors Python
 * `_common.evaluate_bill`.
 */
fun evaluateBill(summary: JsonObject, cutoff: Instant): BillEvaluationResult {
    val billType = summary.stringField("type")?.lowercase().orEmpty()
    val billNumber = summary.stringField("number").orEmpty()
    if (billType.isEmpty() || billNumber.isEmpty()) {
        return BillEvaluationResult.Rejected(RejectionReasons.MISSING_TYPE_OR_NUMBER)
    }
    val latestAction = summary.jsonObjectField("latestAction") ?: JsonObject(emptyMap())
    val actionText = latestAction.stringField("text").orEmpty()
    val outcome = classifyOutcome(actionText)
        ?: return BillEvaluationResult.Rejected(RejectionReasons.NO_OUTCOME_MATCH)
    val actionDateRaw = latestAction.stringField("actionDate") ?: latestAction.stringField("date")
    val actionDate = parseIsoInstant(actionDateRaw)
        ?: return BillEvaluationResult.Rejected(RejectionReasons.UNPARSEABLE_ACTION_DATE)
    if (actionDate < cutoff) {
        return BillEvaluationResult.Rejected(RejectionReasons.ACTION_TOO_OLD)
    }
    return BillEvaluationResult.Kept(outcome)
}

/**
 * Permissive string field extractor. Accepts JSON strings and JSON
 * numbers (Congress.gov occasionally returns bill numbers as numeric
 * primitives). Returns null for missing keys, JSON null, and
 * structural values (object/array).
 */
internal fun JsonObject.stringField(key: String): String? = this[key]?.asStringOrNull()

internal fun JsonObject.jsonObjectField(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonElement.asStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.contentOrNull else primitive.contentOrNull
}
