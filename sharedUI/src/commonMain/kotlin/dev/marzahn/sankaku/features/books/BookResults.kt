package dev.marzahn.sankaku.features.books

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.paging.compose.LazyPagingItems
import dev.marzahn.sankaku.api.dto.BookDto
import dev.marzahn.sankaku.data.BookListItem

data class ResultBook(val book: BookDto, val seriesId: String? = null) {
  val open: OpenBook get() = OpenBook(book.id, book.title)
}

@Stable
abstract class BookResults {
  abstract val count: Int

  abstract fun bookAt(index: Int): ResultBook?

  abstract fun peek(index: Int): ResultBook?

  abstract val locked: Boolean

  abstract suspend fun reveal(index: Int)

  open val seriesId: String? get() = null

  private val covers = HashMap<String, Rect>()

  internal fun coverPlaced(bookId: String, bounds: Rect) {
    covers[bookId] = bounds
  }

  internal fun coverGone(bookId: String) {
    covers.remove(bookId)
  }

  internal fun coverOf(bookId: String): Rect? = covers[bookId]

  var hiddenCover: String? by mutableStateOf(null)
    internal set
}

internal class PagedBookResults(
  private val items: LazyPagingItems<BookListItem>,
  private val listState: LazyGridState,
  private val isLocked: () -> Boolean,
) : BookResults() {
  override val count: Int get() = items.itemCount
  override val locked: Boolean get() = isLocked()

  override fun bookAt(index: Int): ResultBook? = items.takeIf { index in 0 until it.itemCount }?.get(index)?.asResult()

  override fun peek(index: Int): ResultBook? = items.takeIf { index in 0 until it.itemCount }?.peek(index)?.asResult()

  override suspend fun reveal(index: Int) = listState.reveal(index)
}

internal class SeriesBookResults(
  private val books: List<BookDto>,
  override val seriesId: String,
  private val listState: LazyGridState,
  private val isLocked: () -> Boolean,
) : BookResults() {
  override val count: Int get() = books.size
  override val locked: Boolean get() = isLocked()

  override fun bookAt(index: Int): ResultBook? = peek(index)

  override fun peek(index: Int): ResultBook? = books.getOrNull(index)?.let { ResultBook(it, seriesId) }

  override suspend fun reveal(index: Int) = listState.reveal(index)
}

@Composable
internal fun BookResults.coverTracker(bookId: String): Modifier {
  DisposableEffect(this, bookId) { onDispose { coverGone(bookId) } }
  return Modifier.onGloballyPositioned { coverPlaced(bookId, it.boundsInWindow()) }
}

internal fun BookListItem.asResult(): ResultBook = when (this) {
  is BookListItem.Book -> ResultBook(book)
  is BookListItem.Series -> ResultBook(first, series.id)
}

private suspend fun LazyGridState.reveal(index: Int) {
  val info = layoutInfo
  val item = info.visibleItemsInfo.firstOrNull { it.index == index }
  if (item == null) {
    scrollToItem(index)
    return
  }
  val top = item.offset.y
  val bottom = top + item.size.height
  when {
    top < info.viewportStartOffset -> scrollToItem(index)
    bottom > info.viewportEndOffset - info.afterContentPadding -> scrollBy((bottom - info.viewportEndOffset + info.afterContentPadding).toFloat())
  }
}

internal fun nextInSeries(books: List<BookDto>, currentId: String): BookDto? {
  val at = books.indexOfFirst { it.id == currentId }
  return if (at < 0) null else books.getOrNull(at + 1)
}
