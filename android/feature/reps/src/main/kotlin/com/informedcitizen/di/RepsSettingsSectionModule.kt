package com.informedcitizen.di

import com.informedcitizen.ui.settings.SettingsSection
import com.informedcitizen.ui.settings.sections.SavedRepsSettingsSection
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class RepsSettingsSectionModule {
    @Binds @IntoSet
    abstract fun bindSavedRepsSection(impl: SavedRepsSettingsSection): SettingsSection
}
