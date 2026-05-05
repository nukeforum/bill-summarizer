package com.informedcitizen.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.informedcitizen.crash.BuildEnvironment
import com.informedcitizen.crash.CrashReporter
import com.informedcitizen.crash.FirebaseCrashReporter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CrashBindingsModule {
    @Binds
    @Singleton
    abstract fun bindCrashReporter(impl: FirebaseCrashReporter): CrashReporter
}

@Module
@InstallIn(SingletonComponent::class)
object CrashProvidersModule {

    @Provides
    @Singleton
    fun provideBuildEnvironment(@ApplicationContext context: Context): BuildEnvironment {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return BuildEnvironment(isDebuggable = debuggable)
    }
}
