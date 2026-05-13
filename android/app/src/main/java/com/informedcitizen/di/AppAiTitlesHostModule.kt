package com.informedcitizen.di

import android.content.Context
import android.content.Intent
import com.informedcitizen.MainActivity
import com.informedcitizen.R
import com.informedcitizen.feature.aititles.AiTitlesHost
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppAiTitlesHostModule {

    @Provides @Singleton
    fun provideAiTitlesHost(): AiTitlesHost = object : AiTitlesHost {
        override fun openAiSettingsIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_AI_SETTINGS
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        override val notificationSmallIconResId: Int = R.drawable.ic_launcher_foreground
    }
}
