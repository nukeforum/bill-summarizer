package com.informedcitizen.di

import com.informedcitizen.ui.settings.SettingsSection
import com.informedcitizen.ui.settings.sections.DataSourcesSettingsSection
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourcesSettingsSectionModule {
    @Binds @IntoSet
    abstract fun bindDataSourcesSection(impl: DataSourcesSettingsSection): SettingsSection
}
