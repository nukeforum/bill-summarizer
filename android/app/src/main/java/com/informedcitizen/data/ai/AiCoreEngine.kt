package com.informedcitizen.data.ai

import android.content.Context
import com.google.ai.edge.aicore.DownloadCallback
import com.google.ai.edge.aicore.DownloadConfig
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig

/**
 * Thin wrapper around AICore's GenerativeModel so AiCapabilityImpl can be
 * unit-tested without an emulator. Callers create one engine per probe or
 * download attempt; close() releases it.
 */
interface AiCoreEngine {
    suspend fun prepareInferenceEngine()
    fun close()
}

class RealAiCoreEngine(
    context: Context,
    callback: DownloadCallback,
) : AiCoreEngine {
    private val model = GenerativeModel(
        generationConfig = generationConfig {
            this.context = context
            temperature = 0.2f
            topK = 16
            maxOutputTokens = 8
        },
        downloadConfig = DownloadConfig(callback),
    )

    override suspend fun prepareInferenceEngine() {
        model.prepareInferenceEngine()
    }
    override fun close() { runCatching { model.close() } }
}

/**
 * Factory that builds an engine bound to a fresh DownloadCallback. AICore
 * binds the callback at construction time, so each download attempt needs a
 * new engine instance.
 */
fun interface AiCoreEngineFactory {
    fun create(callback: DownloadCallback): AiCoreEngine
}
