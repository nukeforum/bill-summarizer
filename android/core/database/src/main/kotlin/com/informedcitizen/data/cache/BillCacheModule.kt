package com.informedcitizen.data.cache

import com.informedcitizen.cache.BillSummaryDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the [BillCache] binding to the rest of the app. Co-located
 * with the SqlDelight driver module so [BillCache] consumers don't
 * need to import database internals; they `@Inject BillCache` and get
 * the SQL-backed impl.
 */
@Module
@InstallIn(SingletonComponent::class)
object BillCacheModule {
    @Provides
    @Singleton
    fun provideBillCache(db: BillSummaryDatabase): BillCache = SqlDelightBillCache(db)
}
