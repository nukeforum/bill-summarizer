package com.informedcitizen.data.byok

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ByokModule {
    @Provides
    @Singleton
    fun provideByokCipher(): ByokCipher = KeystoreByokCipher()

    @Provides
    fun provideKeyValidator(): ByokKeyValidator = ByokKeyValidator()
}
