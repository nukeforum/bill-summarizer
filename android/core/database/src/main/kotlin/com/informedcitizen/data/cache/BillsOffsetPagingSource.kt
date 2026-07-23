package com.informedcitizen.data.cache

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import com.informedcitizen.pipeline.model.Bill
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * An offset-keyed Paging 3 [PagingSource] over the DB-filtered
 * `selectBillsPaged` / `countBillsPaged` queries — the read side of the
 * sharded bills list (backlog #41). The Paging key is the absolute row
 * offset.
 *
 * This mirrors what SQLDelight's own `OffsetQueryPagingSource` does, but is
 * rolled by hand: sqldelight's `QueryPagingSource(...)` factory function is
 * shadowed by a public abstract class of the same name (Kotlin 2.3 binds the
 * call to the class constructor), so it isn't callable from here. The offset
 * arithmetic and the count-query [Query.Listener] that invalidates the source
 * on a cache write are reproduced directly.
 *
 * @param transacter the database, so count + page reads share one transaction.
 * @param context the dispatcher the blocking query runs on.
 * @param countQuery total matching rows (drives `itemsAfter` and the end-of-list check).
 * @param pageQuery builds the `Bill` page query for a `(limit, offset)` window.
 */
internal class BillsOffsetPagingSource(
    private val transacter: Transacter,
    private val context: CoroutineContext,
    private val countQuery: Query<Long>,
    private val pageQuery: (limit: Long, offset: Long) -> Query<Bill>,
) : PagingSource<Int, Bill>() {

    private val listener = Query.Listener { invalidate() }

    init {
        countQuery.addListener(listener)
        registerInvalidatedCallback { countQuery.removeListener(listener) }
    }

    override val jumpingSupported: Boolean get() = true

    override fun getRefreshKey(state: PagingState<Int, Bill>): Int? =
        state.anchorPosition?.let { anchor ->
            (anchor - state.config.initialLoadSize / 2).coerceAtLeast(0)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Bill> =
        withContext(context) {
            try {
                transacter.transactionWithResult {
                    val count = countQuery.executeAsOne().toInt()
                    val key = params.key ?: 0
                    val limit = when (params) {
                        is LoadParams.Prepend -> minOf(key, params.loadSize)
                        else -> params.loadSize
                    }
                    val offset = when (params) {
                        is LoadParams.Prepend -> maxOf(0, key - params.loadSize)
                        is LoadParams.Append -> key
                        is LoadParams.Refresh ->
                            if (key >= count) maxOf(0, count - params.loadSize) else key
                    }
                    val data = pageQuery(limit.toLong(), offset.toLong()).executeAsList()
                    val nextPosToLoad = offset + data.size
                    LoadResult.Page(
                        data = data,
                        prevKey = offset.takeIf { it > 0 && data.isNotEmpty() },
                        nextKey = nextPosToLoad.takeIf {
                            data.isNotEmpty() && data.size >= limit && it < count
                        },
                        itemsBefore = offset,
                        itemsAfter = (count - nextPosToLoad).coerceAtLeast(0),
                    )
                }
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }
}
