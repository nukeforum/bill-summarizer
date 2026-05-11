package com.informedcitizen.data.ai

import android.content.Context
import android.os.Build
import com.google.ai.edge.aicore.DownloadCallback
import com.google.ai.edge.aicore.GenerativeAIException
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
    private val engineFactory: AiCoreEngineFactory,
) : AiCapability {

    override val status: Flow<AiCapability.Status> =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            flowOf(AiCapability.Status.NotSupported)
        } else {
            flow {
                coroutineScope {
                    val state = MutableStateFlow<AiCapability.Status>(AiCapability.Status.NotSupported)
                    launch { probe(state) }
                    state.collect { emit(it) }
                }
            }.flowOn(Dispatchers.IO)
        }

    override fun requestDownload() {
        // A later task wires the real implementation. This stub keeps the type
        // system happy for the Task 3 commit.
    }

    private suspend fun probe(state: MutableStateFlow<AiCapability.Status>) {
        val callback = object : DownloadCallback {
            override fun onDownloadStarted(bytesToDownload: Long) {
                state.value = AiCapability.Status.ModelDownloading(-1f)
            }
            override fun onDownloadPending() {
                state.value = AiCapability.Status.ModelDownloading(-1f)
            }
            override fun onDownloadProgress(totalBytesDownloaded: Long) {
                state.value = AiCapability.Status.ModelDownloading(-1f)
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
        val engine = engineFactory.create(callback)
        try {
            withContext(Dispatchers.IO) { engine.prepareInferenceEngine() }
            state.value = AiCapability.Status.Available
        } catch (_: Throwable) {
            if (state.value === AiCapability.Status.Available) {
                state.value = AiCapability.Status.NotSupported
            }
        } finally {
            engine.close()
        }
    }
}
