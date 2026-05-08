package com.informedcitizen.di

import com.informedcitizen.data.ai.AiCapability
import com.informedcitizen.data.ai.AiCapabilityImpl
import com.informedcitizen.data.ai.AiCoreBillSummarizer
import com.informedcitizen.data.ai.BillSummarizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiTitlesModule {
    @Binds @Singleton
    abstract fun bindBillSummarizer(impl: AiCoreBillSummarizer): BillSummarizer

    @Binds @Singleton
    abstract fun bindAiCapability(impl: AiCapabilityImpl): AiCapability
}
