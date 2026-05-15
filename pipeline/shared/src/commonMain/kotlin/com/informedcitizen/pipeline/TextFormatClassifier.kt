package com.informedcitizen.pipeline

fun classifyTextFormatUrl(url: String): String? {
    val withoutQuery = url.lowercase().substringBefore('?')
    return when {
        withoutQuery.endsWith(".htm") || withoutQuery.endsWith(".html") -> "html"
        withoutQuery.endsWith(".xml") -> "xml"
        withoutQuery.endsWith(".pdf") -> "pdf"
        else -> null
    }
}
