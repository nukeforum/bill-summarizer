package com.informedcitizen.di

import com.informedcitizen.data.repository.CachedMemberRepository
import com.informedcitizen.data.repository.MemberRepository
import com.informedcitizen.data.repository.SavedRepsAdapter
import com.informedcitizen.data.repository.SavedRepsSource
import com.informedcitizen.data.zipcrosswalk.AssetZipDistrictLookup
import com.informedcitizen.data.zipcrosswalk.ZipDistrictLookup
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {
    @Binds @Singleton
    abstract fun bindMemberRepository(impl: CachedMemberRepository): MemberRepository

    @Binds @Singleton
    abstract fun bindZipDistrictLookup(impl: AssetZipDistrictLookup): ZipDistrictLookup

    @Binds @Singleton
    abstract fun bindSavedRepsSource(impl: SavedRepsAdapter): SavedRepsSource
}
