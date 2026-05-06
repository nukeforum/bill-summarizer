package com.informedcitizen.data.zipcrosswalk

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ZipEntry(val state: String, val districts: List<Int>)

@Singleton
class AssetZipDistrictLookup(
    private val loader: suspend () -> String,
) : ZipDistrictLookup {
    private val mutex = Mutex()
    private var cache: Map<String, ZipEntry>? = null

    @Inject
    constructor(@ApplicationContext context: Context) : this(loader = {
        withContext(Dispatchers.IO) {
            context.assets.open("zip_to_cd.json").bufferedReader().use { it.readText() }
        }
    })

    override suspend fun lookup(zip: String): ZipDistrictResult {
        val normalized = zip.padStart(5, '0').take(5)
        val data = ensureLoaded() ?: return ZipDistrictResult.Miss
        val entry = data[normalized] ?: return ZipDistrictResult.Miss
        return when {
            entry.districts.size <= 1 ->
                ZipDistrictResult.Single(entry.state, entry.districts.firstOrNull() ?: 0)
            else -> ZipDistrictResult.Multiple(entry.state, entry.districts)
        }
    }

    override suspend fun isAvailable(): Boolean = ensureLoaded() != null

    private suspend fun ensureLoaded(): Map<String, ZipEntry>? = mutex.withLock {
        cache ?: runCatching {
            val text = loader()
            JSON.decodeFromString<Map<String, ZipEntry>>(text)
        }.getOrNull()?.also { cache = it }
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }
    }
}
