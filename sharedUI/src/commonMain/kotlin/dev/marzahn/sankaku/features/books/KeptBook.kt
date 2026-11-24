package dev.marzahn.sankaku.features.books

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.ui.components.rememberAlreadyInTab
import dev.marzahn.sankaku.api.dto.BookDto
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.tabs.ClosedTabUndo
import dev.marzahn.sankaku.tabs.LocalTabEntry
import dev.marzahn.sankaku.tabs.TabsController
import dev.marzahn.sankaku.tabs.UpNextState
import dev.marzahn.sankaku.tabs.bookSeenId
import org.koin.compose.koinInject

internal fun BookDto.keptRoute(): Route.Book = Route.Book(id, title.ifBlank { null }, coverPost?.md5, rating)

@Composable
fun KeptBookScreen(
  route: Route.Book,
  tabs: TabsController = koinInject(),
  undo: ClosedTabUndo = koinInject(),
  upNext: UpNextState = koinInject(),
  reader: BookReader = koinInject(),
) {
  val tab = LocalTabEntry.current?.tab
  DisposableEffect(route.id) { onDispose { reader.sendProgress(route.id) } }
  LifecycleEventEffect(Lifecycle.Event.ON_STOP) { reader.sendProgress(route.id) }
  BookReaderScreen(
    OpenBook(route.id, route.title),
    onClose = {
      if (tab != null) {
        if (platformIsTouch) undo.dragClose(tab.id, upNext) else tabs.close(tab.id)
      }
    },
    onPlace = { reader.report(route.id, it) },
    tab = tab?.id,
  )
}

@Stable
internal class BookKeeping(
  private val keepIn: (BookDto) -> Unit,
  private val marksOf: (BookDto) -> BookMarks,
) {
  fun keep(book: BookDto) = keepIn(book)

  fun marks(book: BookDto): BookMarks = marksOf(book)
}

@Composable
internal fun rememberBookKeeping(
  navigator: Navigator = koinInject(),
  tabs: TabsController = koinInject(),
  seen: SeenStore = koinInject(),
): BookKeeping {
  val source = LocalTabEntry.current?.tab
  val all by tabs.tabs.collectAsStateWithLifecycle()
  val revision by seen.revision.collectAsStateWithLifecycle()
  val kept by remember(all) { derivedStateOf { tabs.keptBooks(all) } }
  val already = rememberAlreadyInTab()
  return BookKeeping(
    keepIn = { book ->
      val again = tabs.keptBookSeen(book.id, seen)
      navigator.openInNewTab(book.keptRoute())?.let { position -> already.show(book.id, position, again) }
    },
    marksOf = { book ->
      revision
      BookMarks(
        kept = book.id in kept,
        already = already.of(book.id),
        seen = source != null && tabs.seenFromBooks(source, book.id, seen),
      )
    },
  )
}

@Composable
internal fun RecordBookSeen(bookId: String, shown: Boolean, seen: SeenStore = koinInject()) {
  val wasShown = remember(bookId) { booleanArrayOf(false) }
  if (shown) wasShown[0] = true
  DisposableEffect(bookId) {
    onDispose { if (wasShown[0]) seen.record(bookSeenId(bookId), null) }
  }
}
