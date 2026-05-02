package com.informedcitizen.data.api

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillTextFetcher @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun fetchPlainText(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} fetching $url")
                }
                val html = response.body.string()
                stripHtml(html)
            }
        }
    }

    private fun stripHtml(html: String): String {
        val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        return spanned.toString().replace(Regex("\\n{3,}"), "\n\n").trim()
    }
}
