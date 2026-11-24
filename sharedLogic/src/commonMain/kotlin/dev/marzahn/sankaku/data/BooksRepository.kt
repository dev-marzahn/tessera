package dev.marzahn.sankaku.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.BooksApi
import dev.marzahn.sankaku.api.dto.BookDto
import dev.marzahn.sankaku.api.dto.BookListResponse
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.api.dto.SeriesDto
import dev.marzahn.sankaku.api.dto.SeriesSummaryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

sealed interface BookListItem {
  val key: String

  data class Book(val book: BookDto) : BookListItem {
    override val key: String get() = "book-${book.id}"
  }

  data class Series(val series: SeriesSummaryDto, val first: BookDto) : BookListItem {
    override val key: String get() = "series-${series.id}"
  }
}

enum class BookSort(val tag: String, val label: String) {
  Popular("order:popularity", "Popular"),
  Newest("order:date", "Newest"),
}

object BookSearch {
  const val ENGLISH = "english_language"

  private const val SAFE = "rating:s"

  fun sortOf(tags: List<String>): BookSort =
    BookSort.entries.firstOrNull { sort -> tags.any { it.equals(sort.tag, ignoreCase = true) } } ?: BookSort.Popular

  fun englishOnly(tags: List<String>): Boolean = tags.any { it.equals(ENGLISH, ignoreCase = true) }

  fun withSort(tags: List<String>, sort: BookSort): List<String> =
    SearchTags.filtersFirst(tags.filterNot(::isOrder) + listOfNotNull(sort.tag.takeIf { sort != BookSort.Popular }))

  fun withEnglishOnly(tags: List<String>, on: Boolean): List<String> =
    tags.filterNot { it.equals(ENGLISH, ignoreCase = true) } + listOfNotNull(ENGLISH.takeIf { on })

  fun submitted(next: List<String>, current: List<String>): List<String> {
    val tags = next.filterNot { isOrder(it) || isFeedOrThreshold(it) || it.equals(ENGLISH, ignoreCase = true) }
    val order = next.firstOrNull(::isOrder)
    val sort = when (order) {
      null -> BookSort.Popular
      else -> BookSort.entries.firstOrNull { it.tag.equals(order, ignoreCase = true) } ?: sortOf(current)
    }
    return withEnglishOnly(withSort(tags, sort), englishOnly(next))
  }

  fun searched(tags: List<String>, ratingLocked: Boolean): List<String> {
    val rest = tags.filterNot(::isOrder).let { if (ratingLocked) it.filterNot(::isRating) + SAFE else it }
    return SearchTags.filtersFirst(listOf(sortOf(tags).tag) + rest)
  }

  fun isPlain(tags: List<String>): Boolean = tags.all { isOrder(it) || it.equals(ENGLISH, ignoreCase = true) }

  fun reading(ratingLocked: Boolean): List<String> = listOf(READING) + listOfNotNull(SAFE.takeIf { ratingLocked })

  private const val READING = "order:reading"

  private fun isOrder(tag: String) = tag.removePrefix("-").startsWith("order:", ignoreCase = true)

  private fun isRating(tag: String) = tag.removePrefix("-").startsWith("rating:", ignoreCase = true)

  private fun isFeedOrThreshold(tag: String) =
    tag.removePrefix("-").let { it.startsWith("feed:", ignoreCase = true) || it.startsWith("threshold:", ignoreCase = true) }
}

class BooksRepository(
  private val api: BooksApi,
  private val scope: CoroutineScope,
  private val clock: Clock = Clock.System,
  private val pagesFor: Duration = FIRST_PAGES_TTL,
  signedIn: Flow<Boolean> = emptyFlow(),
  private val favourites: BookFavourites? = null,
) {
  fun list(searched: List<String>): Flow<PagingData<BookListItem>> = Pager(
    PagingConfig(pageSize = BooksApi.PAGE_SIZE, initialLoadSize = BooksApi.PAGE_SIZE, prefetchDistance = PREFETCH_DISTANCE, enablePlaceholders = false),
  ) { BookListPagingSource { page -> fresh({ api.list(searched, page) }) { it.pools.data + it.series.data.mapNotNull { s -> s.firstPool } } } }.flow

  suspend fun reading(ratingLocked: Boolean): List<BookDto> =
    fresh({ api.list(BookSearch.reading(ratingLocked), page = 1, limit = BooksApi.PAGE_SIZE) }) { it.pools.data }.pools.data

  private class CachedPages(val pages: List<PostDto>, val at: Instant)

  private val pages = MutableStateFlow<Map<Pair<String, Boolean>, CachedPages>>(emptyMap())

  suspend fun firstPages(bookId: String, signedIn: Boolean): List<PostDto> {
    cachedFirstPages(bookId, signedIn)?.let { return it }
    val key = bookId to signedIn
    val ask = askingLock.withLock {
      asking[key]?.takeUnless { it.isCompleted } ?: scope.async {
        api.firstPages(bookId, ROW_PAGES).also { loaded ->
          pages.update { cached ->
            val now = clock.now()
            cached.filterValues { now - it.at < pagesFor } + (key to CachedPages(loaded, now))
          }
        }
      }.also { asking[key] = it }
    }
    try {
      return ask.await()
    } finally {
      askingLock.withLock { if (asking[key] === ask && ask.isCompleted) asking.remove(key) }
    }
  }

  private val asking = mutableMapOf<Pair<String, Boolean>, Deferred<List<PostDto>>>()
  private val askingLock = Mutex()

  fun cachedFirstPages(bookId: String, signedIn: Boolean): List<PostDto>? =
    pages.value[bookId to signedIn]?.takeIf { clock.now() - it.at < pagesFor }?.pages

  private val booksOfPosts = MutableStateFlow<Map<Pair<String, Boolean>, List<BookDto>>>(emptyMap())

  private val signOuts = MutableStateFlow(0)

  private val askingBooks = mutableMapOf<Pair<String, Boolean>, Deferred<List<BookDto>>>()

  init {
    scope.launch {
      signedIn.filter { !it }.collect {
        askingLock.withLock {
          signOuts.update { it + 1 }
          booksOfPosts.value = emptyMap()
          askingBooks.clear()
        }
      }
    }
  }

  suspend fun bookOf(post: PostDto, signedIn: Boolean, ratingLocked: Boolean): BookDto? {
    if (!asksForBook(post)) return null
    val key = post.id to signedIn
    booksOfPosts.value[key]?.let { return pick(it, ratingLocked) }
    val ask = askingLock.withLock {
      askingBooks[key]?.takeUnless { it.isCompleted } ?: run {
        val since = signOuts.value
        scope.async {
          api.booksOf(post.id).also { loaded ->
            booksOfPosts.update { cached ->
              if (signOuts.value != since) cached
              else (cached - key + (key to loaded)).entries.toList().takeLast(BOOKS_OF_POSTS_MAX).associate { it.toPair() }
            }
          }
        }
      }.also { askingBooks[key] = it }
    }
    try {
      return pick(ask.await(), ratingLocked)
    } finally {
      askingLock.withLock { if (askingBooks[key] === ask && ask.isCompleted) askingBooks.remove(key) }
    }
  }

  fun cachedBookOf(post: PostDto, signedIn: Boolean, ratingLocked: Boolean): BookDto? =
    booksOfPosts.value[post.id to signedIn]?.let { pick(it, ratingLocked) }

  private fun asksForBook(post: PostDto) = post.inVisiblePool

  private fun pick(books: List<BookDto>, ratingLocked: Boolean): BookDto? =
    if (ratingLocked) books.firstOrNull { it.rating.isSafe() } else books.firstOrNull()

  suspend fun series(seriesId: String, ratingLocked: Boolean): SeriesDto {
    val series = fresh({ api.series(seriesId) }) { it.books }
    return if (ratingLocked) series.copy(books = series.books.filter { it.rating.isSafe() }) else series
  }

  private suspend fun <T> fresh(load: suspend () -> T, books: (T) -> List<BookDto>): T {
    val favourites = favourites ?: return load()
    return favourites.fresh(load, books)
  }

  companion object {
    val FIRST_PAGES_TTL: Duration = 10.minutes

    const val ROW_PAGES = BooksApi.FIRST_PAGES + 1

    const val BOOKS_OF_POSTS_MAX = 200

    const val PREFETCH_DISTANCE = 6
  }
}

fun String?.isSafe(): Boolean = this.equals("s", ignoreCase = true) || this.equals("safe", ignoreCase = true)

class BookListPagingSource(private val load: suspend (page: Int) -> BookListResponse) : PagingSource<Int, BookListItem>() {
  private val seen = mutableSetOf<String>()
  private val loading = Mutex()

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BookListItem> = try {
    loading.withLock {
      var page = params.key ?: 1
      var response = load(page)
      var fresh = rows(page, response).filter { seen.add(it.key) }
      var skipped = 0
      while (fresh.isEmpty() && response.pools.data.isNotEmpty() && skipped < MAX_REPEATED_PAGES) {
        page++
        response = load(page)
        fresh = rows(page, response).filter { seen.add(it.key) }
        skipped++
      }
      val ended = response.pools.data.isEmpty() || fresh.isEmpty()
      LoadResult.Page(fresh, prevKey = null, nextKey = if (ended) null else page + 1)
    }
  } catch (e: ApiError) {
    LoadResult.Error(e)
  }

  private fun rows(page: Int, response: BookListResponse): List<BookListItem> {
    val series = if (page == 1) {
      response.series.data.mapNotNull { series -> series.firstPool?.let { BookListItem.Series(series, it) } }
    } else {
      emptyList()
    }
    return series + response.pools.data.map(BookListItem::Book)
  }

  override fun getRefreshKey(state: PagingState<Int, BookListItem>): Int? = null

  companion object {
    const val MAX_REPEATED_PAGES = 5
  }
}
