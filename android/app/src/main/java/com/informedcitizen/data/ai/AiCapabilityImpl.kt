package com.informedcitizen.data.ai

import android.content.Context
import android.os.Build
import com.google.ai.edge.aicore.DownloadCallback
import com.google.ai.edge.aicore.DownloadConfig
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiCapabilityImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AiCapability {

    override val status: Flow<AiCapability.Status> =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            flowOf(AiCapability.Status.NotSupported)
        } else {
            flow {
                coroutineScope {
                    val state = MutableStateFlow(AiCapability.Status.NotSupported)
                    launch { probe(state) }
                    state.collect { emit(it) }
                }
            }.flowOn(Dispatchers.IO)
        }

    private suspend fun probe(state: MutableStateFlow<AiCapability.Status>) {
        val callback = object : DownloadCallback {
            override fun onDownloadStarted(bytesToDownload: Long) {
                state.value = AiCapability.Status.ModelDownloading
            }
            override fun onDownloadPending() {
                state.value = AiCapability.Status.ModelDownloading
            }
            override fun onDownloadProgress(totalBytesDownloaded: Long) {
                state.value = AiCapability.Status.ModelDownloading
            }
            override fun onDownloadCompleted() {
                state.value = AiCapability.Status.Available
            }
            override fun onDownloadFailed(failureStatus: String, e: GenerativeAIException) {
                state.value = AiCapability.Status.NotSupported
            }
            override fun onDownloadDidNotStart(e: GenerativeAIException) {
                state.value = AiCapability.Status.NotSupported
            }
        }
        val model = GenerativeModel(
            generationConfig = generationConfig {
                this.context = this@AiCapabilityImpl.context
                temperature = 0.2f
                topK = 16
                maxOutputTokens = 8
            },
            downloadConfig = DownloadConfig(callback),
        )
        try {
            withContext(Dispatchers.IO) { model.prepareInferenceEngine() }
            state.value = AiCapability.Status.Available
        } catch (_: Throwable) {
            // Failed preparation: leave whatever the DownloadCallback put in
            // (e.g. NotSupported / ModelDownloading). Default is NotSupported.
            if (state.value == AiCapability.Status.Available) {
                state.value = AiCapability.Status.NotSupported
            }
        } finally {
            runCatching { model.close() }
        }
    }
}
