package com.informedcitizen.di

import com.informedcitizen.ui.settings.SettingsSection
import com.informedcitizen.ui.settings.sections.CongressCalendarSettingsSection
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarSettingsSectionModule {
    @Binds @IntoSet
    abstract fun bindCongressCalendarSection(impl: CongressCalendarSettingsSection): SettingsSection
}
