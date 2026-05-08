package com.informedcitizen.data.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object BillSummaryParser {

    sealed interface Result {
        data class Success(val summary: BillSummary) : Result
        data object ParseFailed : Result
    }

    private const val MAX_TITLE_CHARS = 80

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(raw: String): Result {
        val parsed = runCatching { json.decodeFromString<RawSummary>(raw) }.getOrNull()
            ?: return Result.ParseFailed
        val title = parsed.conciseTitle?.trim().orEmpty()
        if (title.isEmpty() || title.length > MAX_TITLE_CHARS) return Result.ParseFailed
        val topicName = parsed.topic?.trim().orEmpty()
        if (topicName.isEmpty()) return Result.ParseFailed
        val topic = BillTopic.fromName(topicName) ?: BillTopic.Other
        return Result.Success(BillSummary(generatedTitle = title, topic = topic))
    }

    @Serializable
    private data class RawSummary(
        @SerialName("concise_title") val conciseTitle: String? = null,
        @SerialName("topic") val topic: String? = null,
    )
}
