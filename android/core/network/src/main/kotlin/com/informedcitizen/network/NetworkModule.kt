package com.informedcitizen.network

import com.informedcitizen.data.api.BillsApi
import com.informedcitizen.data.api.MembersApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

// GitHub Pages site published from /docs by .github/workflows/update-bills.yml.
// BillsApi resolves data/congresses.json first, then fetches the per-Congress
// manifest path (e.g. data/congress119_bills.json) reported by that index.
private const val BILLS_BASE_URL = "https://nukeforum.github.io/bill-summarizer/"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BILLS_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideBillsApi(retrofit: Retrofit): BillsApi = retrofit.create(BillsApi::class.java)

    @Provides
    @Singleton
    fun provideMembersApi(retrofit: Retrofit): MembersApi = retrofit.create(MembersApi::class.java)
}
