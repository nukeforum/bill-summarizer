package com.informedcitizen.di

import com.informedcitizen.cache.BillSummaryDatabase
import com.informedcitizen.data.ai.AiCoreBillSummarizer
import com.informedcitizen.data.cache.BillSummaryCache
import com.informedcitizen.data.cache.SqlDelightBillSummaryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds [BillSummaryCache] from the SQLDelight database, stamping the
 * cache with the AICore engine's identity. Lives in :app because it's
 * the only place that owns both the database (:core:database) and the
 * AICore version constants (still in :app's data/ai/ for now); it will
 * move to :feature:ai-titles when that extraction happens.
 */
@Module
@InstallIn(SingletonComponent::class)
object BillSummaryCacheModule {

    @Provides @Singleton
    fun provideBillSummaryCache(db: BillSummaryDatabase): BillSummaryCache =
        SqlDelightBillSummaryCache(
            db = db,
            modelVersion = AiCoreBillSummarizer.MODEL_VERSION,
            promptVersion = AiCoreBillSummarizer.PROMPT_VERSION,
        )
}
