package com.informedcitizen.pipeline.fetch

/**
 * Map free-form party strings from Congress.gov into the single-letter
 * codes the published bills manifest uses. Mirrors Python
 * `_common.normalize_party`:
 *  - empty / null → `""`
 *  - starts with `D` → `"D"`
 *  - starts with `R` → `"R"`
 *  - starts with `I` → `"I"`
 *  - otherwise → first uppercase char (best-effort)
 */
fun normalizePartyCode(value: String?): String {
    if (value.isNullOrBlank()) return ""
    val v = value.trim().uppercase()
    return when {
        v.startsWith("D") -> "D"
        v.startsWith("R") -> "R"
        v.startsWith("I") -> "I"
        else -> v.take(1)
    }
}
