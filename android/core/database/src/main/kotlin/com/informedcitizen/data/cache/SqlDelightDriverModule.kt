package com.informedcitizen.data.cache

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.informedcitizen.cache.BillSummaryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the SQLDelight driver and database. The concrete
 * [BillSummaryCache] binding lives wherever the AI engine's version
 * stamps are owned (today: :app; eventually: :feature:ai-titles), so this
 * module stays free of any AI dependency.
 */
@Module
@InstallIn(SingletonComponent::class)
object SqlDelightDriverModule {

    @Provides @Singleton
    fun provideDriver(@ApplicationContext context: Context): AndroidSqliteDriver =
        AndroidSqliteDriver(BillSummaryDatabase.Schema, context, "bill_summary.db")

    @Provides @Singleton
    fun provideDatabase(driver: AndroidSqliteDriver): BillSummaryDatabase =
        BillSummaryDatabase(driver)
}
