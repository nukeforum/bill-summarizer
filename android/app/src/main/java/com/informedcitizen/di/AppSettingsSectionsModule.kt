package com.informedcitizen.di

import com.informedcitizen.ui.settings.SettingsSection
import com.informedcitizen.ui.settings.sections.AboutSettingsSection
import com.informedcitizen.ui.settings.sections.CrashReportingSettingsSection
import com.informedcitizen.ui.settings.sections.ThemeSettingsSection
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class AppSettingsSectionsModule {
    @Binds @IntoSet
    abstract fun bindThemeSection(impl: ThemeSettingsSection): SettingsSection

    @Binds @IntoSet
    abstract fun bindCrashReportingSection(impl: CrashReportingSettingsSection): SettingsSection

    @Binds @IntoSet
    abstract fun bindAboutSection(impl: AboutSettingsSection): SettingsSection
}
