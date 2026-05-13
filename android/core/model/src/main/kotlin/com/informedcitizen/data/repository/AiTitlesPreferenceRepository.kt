package com.informedcitizen.data.repository

import com.informedcitizen.data.work.SummarizationScope
import kotlinx.coroutines.flow.Flow

interface AiTitlesPreferenceRepository {
    val enabled: Flow<Boolean>
    val scope: Flow<SummarizationScope>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setScope(scope: SummarizationScope)
}
