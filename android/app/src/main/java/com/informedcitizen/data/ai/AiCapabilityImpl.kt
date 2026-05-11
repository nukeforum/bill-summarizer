package com.informedcitizen.data.ai

import android.content.Context
import android.os.Build
import com.google.ai.edge.aicore.DownloadCallback
import com.google.ai.edge.aicore.GenerativeAIException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.jvm.Volatile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiCapabilityImpl(
    @param:ApplicationContext private val context: Context,
    private val engineFactory: AiCoreEngineFactory,
    private val scope: CoroutineScope,
) : AiCapability {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        engineFactory: AiCoreEngineFactory,
    ) : this(
        context = context,
        engineFactory = engineFactory,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val state = MutableStateFlow<AiCapability.Status>(initialStatus())
    override val status: Flow<AiCapability.Status> = state.asStateFlow()

    private var downloadJob: Job? = null
    @Volatile private var bytesToDownload: Long = 0L

    private fun initialStatus(): AiCapability.Status =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            AiCapability.Status.NotSupported
        } else {
            AiCapability.Status.DownloadAvailable
        }

    override fun requestDownload() {
        when (state.value) {
            AiCapability.Status.Available,
            AiCapability.Status.NotSupported,
            is AiCapability.Status.ModelDownloading -> return
            AiCapability.Status.DownloadAvailable,
            is AiCapability.Status.DownloadFailed -> Unit
        }
        // Guards against a retry firing while the prior runDownload coroutine is
        // still draining prepareInferenceEngine after a terminal callback set state
        // to DownloadFailed. Without this, two runDownload coroutines could race.
        if (downloadJob?.isActive == true) return
        bytesToDownload = 0L
        downloadJob = scope.launch { runDownload() }
    }

    private suspend fun runDownload() {
        val callback = object : DownloadCallback {
            override fun onDownloadStarted(bytesToDownload: Long) {
                this@AiCapabilityImpl.bytesToDownload = bytesToDownload
                state.value = AiCapability.Status.ModelDownloading(progressFraction(0L))
            }
            override fun onDownloadPending() {
                state.value = AiCapability.Status.ModelDownloading(progressFraction(0L))
            }
            override fun onDownloadProgress(totalBytesDownloaded: Long) {
                state.value = AiCapability.Status.ModelDownloading(progressFraction(totalBytesDownloaded))
            }
            override fun onDownloadCompleted() {
                state.value = AiCapability.Status.Available
            }
            override fun onDownloadFailed(failureStatus: String, e: GenerativeAIException) {
                state.value = AiCapability.Status.DownloadFailed(
                    reason = failureStatus.ifBlank { "Check your connection and try again." },
                )
            }
            override fun onDownloadDidNotStart(e: GenerativeAIException) {
                state.value = AiCapability.Status.NotSupported
            }
        }
        val engine = engineFactory.create(callback)
        try {
            engine.prepareInferenceEngine()
            if (state.value !is AiCapability.Status.Available) {
                state.value = AiCapability.Status.Available
            }
        } catch (t: Throwable) {
            val s = state.value
            if (s !is AiCapability.Status.DownloadFailed &&
                s !is AiCapability.Status.NotSupported &&
                s !is AiCapability.Status.Available
            ) {
                state.value = AiCapability.Status.DownloadFailed(
                    reason = "Check your connection and try again.",
                )
            }
        } finally {
            engine.close()
        }
    }

    private fun progressFraction(downloaded: Long): Float {
        val total = bytesToDownload
        return if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else -1f
    }
}
