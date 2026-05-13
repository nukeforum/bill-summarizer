package com.informedcitizen.featureflags

/**
 * Compile-time feature flags. Flip and recompile.
 */
object FeatureFlags {
    /**
     * AI title summarization (Settings section, topic chips, summarization
     * worker, long-press Re-summarize).
     *
     * OFF: AICore SDK `com.google.ai.edge.aicore:0.0.1-exp02` requires
     * Google Early Access allowlisting for the app package. Without it,
     * every `prepareInferenceEngine()` call returns `NOT_AVAILABLE` on
     * supported Pixel hardware with AICore installed. The full feature
     * (state machine, UI, tests) is implemented but unreachable until
     * `com.informedcitizen` is on the allowlist.
     *
     * Flip to `true` once allowlisting is in place and re-smoke on a
     * real device before shipping.
     */
    const val AI_TITLES = false
}
