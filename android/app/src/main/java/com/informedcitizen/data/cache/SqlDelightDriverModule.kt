package com.informedcitizen.data.cache

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.informedcitizen.cache.BillSummaryDatabase
import com.informedcitizen.data.ai.AiCoreBillSummarizer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SqlDelightDriverModule {

    @Provides @Singleton
    fun provideDriver(@ApplicationContext context: Context): AndroidSqliteDriver =
        AndroidSqliteDriver(BillSummaryDatabase.Schema, context, "bill_summary.db")

    @Provides @Singleton
    fun provideDatabase(driver: AndroidSqliteDriver): BillSummaryDatabase =
        BillSummaryDatabase(driver)

    @Provides @Singleton
    fun provideBillSummaryCache(db: BillSummaryDatabase): BillSummaryCache =
        SqlDelightBillSummaryCache(
            db = db,
            modelVersion = AiCoreBillSummarizer.MODEL_VERSION,
            promptVersion = AiCoreBillSummarizer.PROMPT_VERSION,
        )
}
