package com.informedcitizen.di

import android.content.Context
import com.informedcitizen.data.ai.AiCapability
import com.informedcitizen.data.ai.AiCapabilityImpl
import com.informedcitizen.data.ai.AiCoreBillSummarizer
import com.informedcitizen.data.ai.AiCoreEngineFactory
import com.informedcitizen.data.ai.BillSummarizer
import com.informedcitizen.data.ai.RealAiCoreEngine
import com.informedcitizen.data.repository.AiTitlesPreferenceRepository
import com.informedcitizen.data.repository.AiTitlesPreferenceRepositoryImpl
import com.informedcitizen.data.work.BillSummarizationController
import com.informedcitizen.data.work.BillSummarizationControllerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiTitlesModule {
    @Binds @Singleton
    abstract fun bindBillSummarizer(impl: AiCoreBillSummarizer): BillSummarizer

    @Binds @Singleton
    abstract fun bindAiCapability(impl: AiCapabilityImpl): AiCapability

    @Binds @Singleton
    abstract fun bindAiTitlesPreferenceRepository(
        impl: AiTitlesPreferenceRepositoryImpl,
    ): AiTitlesPreferenceRepository

    @Binds @Singleton
    abstract fun bindBillSummarizationController(
        impl: BillSummarizationControllerImpl,
    ): BillSummarizationController

    companion object {
        @Provides @Singleton
        fun provideClock(): Clock = Clock.systemDefaultZone()

        @Provides @Singleton
        fun provideAiCoreEngineFactory(
            @ApplicationContext context: Context,
        ): AiCoreEngineFactory = AiCoreEngineFactory { callback ->
            RealAiCoreEngine(context, callback)
        }
    }
}
