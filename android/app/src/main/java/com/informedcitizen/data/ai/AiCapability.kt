package com.informedcitizen.data.ai

import kotlinx.coroutines.flow.Flow

interface AiCapability {
    val status: Flow<Status>

    enum class Status {
        Available,
        ModelDownloading,
        NotSupported,
    }
}
