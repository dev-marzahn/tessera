package dev.marzahn.sankaku.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.dto.Page
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PageCursor(val next: String? = null, val number: Int = 1) {
  internal fun after(page: Page<*>): PageCursor? {
    val next = page.meta.next?.takeIf { it != next } ?: return null
    return PageCursor(next, number + 1)
  }
}

class KeysetPagingSource<T : Any>(
  private val id: (T) -> Any,
  private val start: PageCursor = PageCursor(),
  private val onLoaded: (PageCursor, List<T>) -> Unit = { _, _ -> },
  private val hide: (T) -> Boolean = { false },
  private val onHidden: () -> Unit = {},
  private val load: suspend (PageCursor) -> Page<T>,
) : PagingSource<PageCursor, T>() {
  private val seen = mutableSetOf<Any>()
  private val loading = Mutex()

  override suspend fun load(params: LoadParams<PageCursor>): LoadResult<PageCursor, T> = try {
    loading.withLock {
      var cursor = params.key ?: start
      var page = fetch(cursor)
      var fresh = shown(page)
      var skipped = 0
      while (fresh.isEmpty() && skipped < MAX_EMPTY_PAGES) {
        cursor = cursor.after(page) ?: break
        page = fetch(cursor)
        fresh = shown(page)
        skipped++
      }
      onLoaded(cursor, fresh)
      LoadResult.Page(data = fresh, prevKey = null, nextKey = cursor.after(page))
    }
  } catch (e: ApiError) {
    LoadResult.Error(e)
  }

  private suspend fun fetch(cursor: PageCursor): Page<T> = try {
    load(cursor)
  } catch (_: ApiError.SearchTimeout) {
    load(cursor)
  }

  private fun shown(page: Page<T>): List<T> {
    val (hidden, shown) = page.data.filter { seen.add(id(it)) }.partition(hide)
    if (hidden.isNotEmpty()) onHidden()
    return shown
  }

  override fun getRefreshKey(state: PagingState<PageCursor, T>): PageCursor? = null

  companion object {
    const val MAX_EMPTY_PAGES = 5
  }
}
