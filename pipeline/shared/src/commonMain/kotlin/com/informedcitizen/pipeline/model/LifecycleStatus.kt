package com.informedcitizen.pipeline.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Pre-floor lifecycle status for a bill that has not (yet) reached a terminal
 * floor [Outcome] — the ~97% of bills issue #39 stops silently dropping.
 * Derived from Congress.gov action data by `classifyLifecycleStatus`.
 *
 * Carried on [Bill.lifecycleStatus] as a **separate, additive field** rather
 * than by widening [Outcome] in place. That separation is a deliberate
 * old-install compatibility decision (issue #39's mandated check): a bill that
 * carries this new `"status"` key is decoded by every already-installed app
 * version — old builds simply drop the unknown key under `ignoreUnknownKeys`
 * and keep working. Widening [Outcome] in place would instead require every
 * old install to have shipped the [Outcome.UNKNOWN] fallback *and* the
 * `Bill.outcome` default (see `OutcomeFallbackTest`); a build released before
 * that fix landed has no default for `coerceInputValues` to target and would
 * crash on an unrecognised outcome string. A separate field sidesteps that
 * entirely. See `LifecycleStatusFallbackTest`.
 */
@Serializable
enum class LifecycleStatus {
    @SerialName("introduced") INTRODUCED,
    @SerialName("in_committee") IN_COMMITTEE,
    @SerialName("reported") REPORTED,

    /**
     * Forward-compat fallback for any lifecycle-status string a future pipeline
     * publishes that this app generation doesn't recognise. Because
     * [Bill.lifecycleStatus] defaults to null, the lenient wire config's
     * `coerceInputValues` maps an unrecognised value to null (the "no lifecycle
     * status published" state) rather than crashing; this member exists so app
     * code can pattern-match the taxonomy exhaustively and stays forward-safe
     * if the field default is ever changed to a non-null sentinel.
     */
    @SerialName("unknown") UNKNOWN,
}
