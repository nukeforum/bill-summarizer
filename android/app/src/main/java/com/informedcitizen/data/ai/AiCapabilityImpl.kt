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
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiCapabilityImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val engineFactory: AiCoreEngineFactory,
) : AiCapability {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow<AiCapability.Status>(initialStatus())
    override val status: Flow<AiCapability.Status> = state.asStateFlow()

    private var downloadJob: Job? = null
    private var bytesToDownload: Long = 0L

    private fun initialStatus(): AiCapability.Status =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            AiCapability.Status.NotSupported
        } else {
            AiCapability.Status.DownloadAvailable
        }

    override fun requestDownload() {
        // Task 5 fills this in.
    }
}
