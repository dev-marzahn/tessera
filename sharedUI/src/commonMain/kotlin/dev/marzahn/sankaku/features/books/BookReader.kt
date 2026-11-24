package dev.marzahn.sankaku.features.books

import dev.marzahn.sankaku.data.BookPlace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OpenBook(val id: String, val title: String? = null)

data class ReaderSession(
  val book: OpenBook,
  val results: BookResults? = null,
  val index: Int = 0,
  val opening: Int = 0,
)

class BookReader(private val saveProgress: (bookId: String, place: BookPlace) -> Unit) {
  private val _session = MutableStateFlow<ReaderSession?>(null)
  val session: StateFlow<ReaderSession?> = _session.asStateFlow()

  private val _shown = MutableStateFlow<OpenBook?>(null)

  val shown: StateFlow<OpenBook?> = _shown.asStateFlow()

  private var openings = 0

  private val places = mutableMapOf<String, BookPlace>()

  fun open(bookId: String, title: String? = null) {
    set(ReaderSession(OpenBook(bookId, title), opening = ++openings))
  }

  fun open(results: BookResults, index: Int) {
    val result = results.peek(index) ?: return
    set(ReaderSession(result.open, results, index, ++openings))
  }

  fun showing(index: Int, book: OpenBook) {
    val session = _session.value ?: return
    if (session.results == null || session.index == index && session.book == book) return
    set(session.copy(book = book, index = index))
  }

  fun close() {
    set(null)
  }

  fun report(bookId: String, place: BookPlace) {
    places[bookId] = place
  }

  fun sendProgress(bookId: String) {
    places.remove(bookId)?.let { saveProgress(bookId, it) }
  }

  fun sendInBackground() {
    _shown.value?.id?.let(::sendProgress)
  }

  private fun set(session: ReaderSession?) {
    val left = _shown.value?.id?.takeIf { it != session?.book?.id }
    _session.value = session
    _shown.value = session?.book
    left?.let(::sendProgress)
  }
}
