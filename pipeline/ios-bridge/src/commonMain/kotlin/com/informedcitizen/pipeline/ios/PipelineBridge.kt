package com.informedcitizen.pipeline.ios

/**
 * Stable, Swift-friendly entry point into shared pipeline behavior.
 *
 * Keep this surface limited to primitives, strings, byte arrays, and dedicated
 * bridge result types. The native iOS adapter owns conversion into Swift domain
 * models so implementation-specific types never enter feature or view code.
 */
class PipelineBridge {
    fun congressForYear(year: Int): Int =
        com.informedcitizen.pipeline.congressForYear(year)

    fun classifyOutcome(actionText: String): String? =
        com.informedcitizen.pipeline.classifyOutcome(actionText)

    fun classifyLifecycleStatus(actionText: String): String? =
        com.informedcitizen.pipeline.classifyLifecycleStatus(actionText)

    fun normalizePartyCode(value: String?): String =
        com.informedcitizen.pipeline.fetch.normalizePartyCode(value)
}
