package com.informedcitizen.di

import com.informedcitizen.ui.settings.SettingsSection
import com.informedcitizen.ui.settings.sections.AiTitlesSettingsSection
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class AiTitlesSettingsSectionModule {
    @Binds @IntoSet
    abstract fun bindAiTitlesSettingsSection(impl: AiTitlesSettingsSection): SettingsSection
}
