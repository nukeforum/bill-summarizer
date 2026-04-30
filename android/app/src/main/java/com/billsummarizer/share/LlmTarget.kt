package com.billsummarizer.share

enum class LlmTarget(
    val displayName: String,
    val packageName: String,
    val webUrl: String,
) {
    CHATGPT("ChatGPT", "com.openai.chatgpt", "https://chat.openai.com/"),
    CLAUDE("Claude", "com.anthropic.claude", "https://claude.ai/new"),
    GEMINI("Gemini", "com.google.android.apps.bard", "https://gemini.google.com/app"),
}
