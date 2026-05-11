package com.informedcitizen.data.ai

import kotlinx.coroutines.flow.Flow

interface AiCapability {
    val status: Flow<Status>

    sealed class Status {
        object Available : Status()
        object DownloadAvailable : Status()
        data class ModelDownloading(val progress: Float) : Status()
        data class DownloadFailed(val reason: String) : Status()
        object NotSupported : Status()
    }
}
