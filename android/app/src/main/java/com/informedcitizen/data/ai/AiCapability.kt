package com.informedcitizen.data.ai

import kotlinx.coroutines.flow.Flow

interface AiCapability {
    val status: Flow<Status>

    /**
     * Initiate (or retry) the on-device model download. No-op when the
     * current status is Available, NotSupported, or ModelDownloading.
     */
    fun requestDownload()

    sealed class Status {
        object Available : Status()
        object DownloadAvailable : Status()
        data class ModelDownloading(val progress: Float) : Status()
        data class DownloadFailed(val reason: String) : Status()
        object NotSupported : Status()
    }
}
