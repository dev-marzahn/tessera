package dev.marzahn.sankaku.features.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.google.fonts.icons.check
import com.google.fonts.icons.menu_book
import dev.marzahn.sankaku.Navigator
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.dto.BookDto
import dev.marzahn.sankaku.data.SavedReading
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.data.BookListItem
import dev.marzahn.sankaku.data.BookSearch
import dev.marzahn.sankaku.data.BookSort
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.data.TagsRepository
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.features.grid.CURSOR_REACH
import dev.marzahn.sankaku.features.grid.EmptySearchContent
import dev.marzahn.sankaku.features.grid.EmptySearchTokens
import dev.marzahn.sankaku.features.grid.Rating
import dev.marzahn.sankaku.features.grid.failedUnreachable
import dev.marzahn.sankaku.features.grid.ratingLocked
import dev.marzahn.sankaku.features.grid.withoutRating
import dev.marzahn.sankaku.platformCellGap
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.searchTags
import dev.marzahn.sankaku.shell.GridRefresh
import dev.marzahn.sankaku.shell.LocalChromePadding
import dev.marzahn.sankaku.shell.LocalOpenSearch
import dev.marzahn.sankaku.shell.RetryWhenBack
import dev.marzahn.sankaku.shell.signedInName
import dev.marzahn.sankaku.tabs.LocalTabEntry
import dev.marzahn.sankaku.tabs.queueSwipe
import dev.marzahn.sankaku.ui.components.AppLoadingIndicator
import dev.marzahn.sankaku.ui.components.BottomRowLayout
import dev.marzahn.sankaku.ui.components.ApiErrorState
import dev.marzahn.sankaku.ui.components.GridOfflineState
import dev.marzahn.sankaku.ui.components.KeyFocusHolder
import dev.marzahn.sankaku.ui.components.LargeIndicatorSize
import dev.marzahn.sankaku.ui.components.PullToRefresh
import dev.marzahn.sankaku.ui.components.Shortcuts
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun BookListScreen(
  route: Route.Books,
  navigator: Navigator = koinInject(),
  reader: BookReader = koinInject(),
  refresh: GridRefresh = koinInject(),
  session: AuthSession = koinInject(),
  actions: BookActions = koinInject(),
) {
  val viewModel = koinViewModel<BookListViewModel>(key = "books-${route.tags}") { parametersOf(route.tags) }
  val items = viewModel.books.collectAsLazyPagingItems()
  val locked by viewModel.ratingLocked.collectAsStateWithLifecycle()
  val signedIn by session.isSignedIn.collectAsStateWithLifecycle()
  val readingOf = rememberReadings(signedIn)
  val listState = rememberLazyGridState()
  val lockedNow by rememberUpdatedState(locked)
  val results = remember(items, listState) { PagedBookResults(items, listState) { lockedNow } }
  val plain = BookSearch.isPlain(route.tags)
  val reading = koinViewModel<ContinueReadingViewModel>(key = "continue-reading")
  val readingBooks by reading.books.collectAsStateWithLifecycle()
  val readerOpen = reader.session.collectAsStateWithLifecycle().value != null
  LifecycleResumeEffect(reading, plain, readerOpen) {
    reading.shown(plain && !readerOpen)
    onPauseOrDispose { reading.shown(false) }
  }
  val shelf = rememberShelfResults { lockedNow }
  val shelfScroll = remember { ScrollAway() }
  val shelfOpen = reader.session.collectAsStateWithLifecycle().value?.results === shelf
  val onShelf = if (plain) shelfBooks(readingBooks, readingOf) else emptyList()
  SideEffect { if (!shelfOpen) shelf.books = onShelf }
  RetryWhenBack {
    if (items.loadState.failedUnreachable()) items.retry()
    reading.retryIfUnreachable()
  }
  val cursor = rememberBookCursor(listState, results, reader)
  LaunchedEffect(viewModel) {
    refresh.requests.collect {
      refresh.setRunning(true)
      try {
        reading.refresh()
        items.refresh()
        withTimeoutOrNull(REFRESH_START_TIMEOUT_MS) { snapshotFlow { items.loadState.refresh }.first { it is LoadState.Loading } }
        snapshotFlow { items.loadState.refresh }.first { it !is LoadState.Loading }
        listState.scrollToItem(0)
        shelfScroll.show()
        cursor.hide()
      } finally {
        refresh.setRunning(false)
      }
    }
  }
  val keeping = rememberBookKeeping()
  fun open(item: BookListItem, index: Int, inNewTab: Boolean) {
    when (item) {
      is BookListItem.Book -> if (inNewTab) keeping.keep(item.book) else reader.open(results, index)
      is BookListItem.Series -> {
        val series = Route.BookSeries(item.series.id, item.series.title, route.tags)
        if (inNewTab) navigator.openInNewTab(series) else navigator.goTo(series)
      }
    }
  }
  val listKey = { event: KeyEvent ->
    cursor.onKey(event, items.itemCount) { key, index ->
      val item = items.peek(index) ?: return@onKey false
      when (key) {
        BookKey.Open -> true.also { open(item, index, inNewTab = false) }
        BookKey.Keep -> true.also { open(item, index, inNewTab = true) }
        BookKey.Favourite -> (item as? BookListItem.Book)?.let { actions.toggleFavourite(it.book) } != null
      }
    }
  }
  BooksFrame(
    refreshing = refresh.running.collectAsStateWithLifecycle().value,
    onRefresh = refresh::request,
    onKey = listKey,
    onFocusLost = cursor::letGo,
    reader = reader,
  ) {
    BookFilters(route.tags, onChange = { navigator.replaceTop(Route.Books(it)) })
    val load = items.loadState.refresh
    ScrollAwayTop(
      bodyAtTop = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 },
      top = {
        if (shelf.books.isNotEmpty()) {
          ContinueReadingShelf(
            results = shelf,
            locked = locked,
            readingOf = readingOf,
            onOpen = { index -> reader.open(shelf, index) },
            onKeep = keeping::keep,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
          )
        }
      },
      body = { BookListBody(load, items, route, listState, results, locked, signedIn, readingOf, keeping, cursor, listKey, ::open) },
      state = shelfScroll,
    )
  }
}

@Composable
private fun BookListBody(
  load: LoadState,
  items: LazyPagingItems<BookListItem>,
  route: Route.Books,
  listState: LazyGridState,
  results: PagedBookResults,
  locked: Boolean,
  signedIn: Boolean,
  readingOf: (BookDto) -> SavedReading?,
  keeping: BookKeeping,
  cursor: BookCursor,
  listKey: (KeyEvent) -> Boolean,
  open: (item: BookListItem, index: Int, inNewTab: Boolean) -> Unit,
) {
  Box(Modifier.fillMaxSize()) {
    when {
      load is LoadState.Loading && items.itemCount == 0 -> DelayedLoading()
      load is LoadState.Error && items.itemCount == 0 -> BooksMessage { LoadFailed(load.error, items::retry) }
      load is LoadState.NotLoading && items.itemCount == 0 -> BooksMessage { EmptyBooks(route) }
      else -> BookRows(listState) {
        items(count = items.itemCount, key = items.itemKey { it.key }) { index ->
          val item = items[index] ?: return@items
          val pictures = item.asResult().book
          val cover = results.coverTracker(pictures.id)
          val coverHidden = results.hiddenCover == pictures.id
          when (item) {
            is BookListItem.Book -> BookRow(
              pictures = item.book,
              text = item.book.rowText(readingOf(item.book)),
              locked = locked,
              signedIn = signedIn,
              favourite = item.book,
              onOpen = { open(item, index, false) },
              onOpenInNewTab = { open(item, index, true) },
              coverHidden = coverHidden,
              coverModifier = cover,
              marks = keeping.marks(item.book),
              cursor = cursor.index == index,
              modifier = Modifier.bookCursorFocus(cursor, index, listKey),
            )
            is BookListItem.Series -> BookRow(
              pictures = item.first,
              text = BookRowText(
                title = item.series.title.ifBlank { item.first.title.ifBlank { UNTITLED } },
                count = booksLabel(item.series.poolCount),
                favCount = null,
                rating = Rating.ofCode(item.series.rating),
                reading = null,
              ),
              locked = locked,
              signedIn = signedIn,
              onOpen = { open(item, index, false) },
              onOpenInNewTab = { open(item, index, true) },
              coverHidden = coverHidden,
              coverModifier = cover,
              cursor = cursor.index == index,
              modifier = Modifier.bookCursorFocus(cursor, index, listKey),
            )
          }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "footer", contentType = "footer") {
          when (val append = items.loadState.append) {
            is LoadState.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { AppLoadingIndicator() }
            is LoadState.Error -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadFailed(append.error, items::retry) }
            is LoadState.NotLoading -> Unit
          }
        }
      }
    }
  }
}

@Composable
fun BookSeriesScreen(
  route: Route.BookSeries,
  reader: BookReader = koinInject(),
  session: AuthSession = koinInject(),
  actions: BookActions = koinInject(),
) {
  val viewModel = koinViewModel<BookSeriesViewModel>(key = "series-${route.id}") { parametersOf(route.id) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val locked by viewModel.ratingLocked.collectAsStateWithLifecycle()
  val signedIn by session.isSignedIn.collectAsStateWithLifecycle()
  val keeping = rememberBookKeeping()
  val readingOf = rememberReadings(signedIn)
  RetryWhenBack { if ((state as? SeriesState.Failed)?.error is ApiError.Network) viewModel.retry() }
  val loaded = (state as? SeriesState.Loaded)?.series
  val listState = rememberLazyGridState()
  val lockedNow by rememberUpdatedState(locked)
  val results = remember(loaded, listState) { loaded?.let { SeriesBookResults(it.books, it.id, listState) { lockedNow } } }
  val cursor = rememberBookCursor(listState, results, reader)
  val open = { index: Int, inNewTab: Boolean ->
    loaded?.books?.getOrNull(index)?.let { book ->
      if (inNewTab) keeping.keep(book) else results?.let { reader.open(it, index) }
    }
  }
  val seriesKey = { event: KeyEvent ->
    cursor.onKey(event, loaded?.books?.size ?: 0) { key, index ->
      val book = loaded?.books?.getOrNull(index)
      when (key) {
        BookKey.Open -> open(index, false)
        BookKey.Keep -> open(index, true)
        BookKey.Favourite -> book?.let(actions::toggleFavourite)
      }
      book != null
    }
  }
  BooksFrame(refreshing = false, onRefresh = viewModel::retry, onKey = seriesKey, onFocusLost = cursor::letGo, reader = reader) {
    SeriesHeading(
      loaded?.title?.ifBlank { null } ?: route.title,
      loaded?.let { seriesCount(it.books.size, maxOf(it.poolCount, it.books.size)) },
    )
    Box(Modifier.fillMaxSize()) {
      when (val shown = state) {
        SeriesState.Loading -> DelayedLoading()
        is SeriesState.Failed -> BooksMessage { LoadFailed(shown.error, viewModel::retry) }
        is SeriesState.Loaded -> if (shown.series.books.isEmpty()) {
          BooksMessage { NoBooksInSeries(locked) }
        } else if (results != null) {
          BookRows(listState) {
            items(shown.series.books.size, key = { shown.series.books[it].id }) { index ->
              val book = shown.series.books[index]
              BookRow(
                pictures = book,
                text = book.rowText(readingOf(book)),
                locked = locked,
                signedIn = signedIn,
                favourite = book,
                onOpen = { open(index, false) },
                onOpenInNewTab = { open(index, true) },
                coverHidden = results.hiddenCover == book.id,
                coverModifier = results.coverTracker(book.id),
                marks = keeping.marks(book),
                cursor = cursor.index == index,
                modifier = Modifier.bookCursorFocus(cursor, index, seriesKey),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BooksFrame(
  refreshing: Boolean,
  onRefresh: () -> Unit,
  onKey: (KeyEvent) -> Boolean,
  onFocusLost: () -> Unit,
  reader: BookReader,
  content: @Composable () -> Unit,
) {
  val tab = LocalTabEntry.current?.tab
  val readerOpen = reader.session.collectAsStateWithLifecycle().value != null
  PullToRefresh(
    refreshing,
    onRefresh = onRefresh,
    enabled = platformIsTouch,
    modifier = Modifier.fillMaxSize().padding(LocalChromePadding.current).queueSwipe(enabled = true, from = tab?.id),
  ) {
    Column(
      Modifier
        .fillMaxSize()
        .onFocusChanged { if (!it.hasFocus) onFocusLost() }
        .onKeyEvent { !readerOpen && onKey(it) },
    ) {
      if (!readerOpen) KeyFocusHolder()
      content()
    }
  }
}

@Composable
private fun BookRows(state: LazyGridState, content: LazyGridScope.() -> Unit) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns = booksColumns(maxWidth)
    val bottom = if (platformIsTouch) BottomRowLayout.WindowRoom + BottomRowLayout.Height else EDGE
    LazyVerticalGrid(
      columns = GridCells.Fixed(columns),
      state = state,
      contentPadding = PaddingValues(start = EDGE, end = EDGE, top = CURSOR_REACH, bottom = bottom),
      horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
      verticalArrangement = Arrangement.spacedBy(ROW_GAP),
      modifier = Modifier.fillMaxSize(),
      content = content,
    )
  }
}

internal fun booksColumns(width: Dp): Int = if (width >= TWO_COLUMNS) 2 else 1

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookFilters(tags: List<String>, onChange: (List<String>) -> Unit) {
  val sort = BookSearch.sortOf(tags)
  val english = BookSearch.englishOnly(tags)
  FlowRow(
    Modifier.fillMaxWidth().padding(horizontal = EDGE, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    BookSort.entries.forEach { option ->
      FilterChip(
        selected = sort == option,
        onClick = { if (sort != option) onChange(BookSearch.withSort(tags, option)) },
        label = { Text(option.label) },
      )
    }
    FilterChip(
      selected = english,
      onClick = { onChange(BookSearch.withEnglishOnly(tags, !english)) },
      label = { Text(ENGLISH_ONLY) },
      leadingIcon = if (english) ({ Icon(check, contentDescription = null) }) else null,
    )
  }
}

@Composable
private fun SeriesHeading(title: String, count: String?) {
  Column(Modifier.fillMaxWidth().padding(horizontal = EDGE + 4.dp, vertical = 8.dp)) {
    Text(title.ifBlank { UNTITLED }, style = MaterialTheme.typography.titleLarge)
    if (count != null) {
      Text(count, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

internal fun seriesCount(shown: Int, total: Int): String =
  if (shown < total) "$shown of ${booksLabel(total)} · G only" else booksLabel(shown)

@Composable
private fun DelayedLoading() {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    val shown by produceState(false) {
      delay(LOADING_INDICATOR_DELAY_MS)
      value = true
    }
    if (shown) AppLoadingIndicator(size = LargeIndicatorSize)
  }
}

@Composable
private fun BooksMessage(content: @Composable () -> Unit) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val scroll = if (platformIsTouch) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Box(scroll.fillMaxWidth().height(maxHeight), contentAlignment = Alignment.Center) { content() }
  }
}

@Composable
private fun LoadFailed(error: Throwable, onRetry: () -> Unit) {
  if (error is ApiError.Network) {
    GridOfflineState(onRetry = onRetry)
  } else {
    ApiErrorState(
      error,
      onRetry = onRetry,
      onEditSearch = LocalOpenSearch.current,
      onSignIn = koinInject<SignInController>()::open,
    )
  }
}

@Composable
private fun EmptyBooks(
  route: Route.Books,
  navigator: Navigator = koinInject(),
  tags: TagsRepository = koinInject(),
  session: AuthSession = koinInject(),
  privacy: PrivacySettings = koinInject(),
) {
  val known by tags.known.collectAsStateWithLifecycle()
  val unknown by tags.unknown.collectAsStateWithLifecycle()
  val signedIn by session.isSignedIn.collectAsStateWithLifecycle()
  val gOnly by privacy.gOnly.collectAsStateWithLifecycle()
  val searched = route.searchTags
  LaunchedEffect(searched) { tags.lookUp(withoutRating(searched)) }
  EmptySearchContent(
    tokens = EmptySearchTokens.of(searched, unknown, ratingLocked(signedIn, gOnly)),
    known = known,
    me = session.signedInName(),
    onRemove = { token -> navigator.replaceTop(Route.Books(searched.filterNot { it == token })) },
    onEdit = LocalOpenSearch.current,
    title = "No books for this search",
  )
}

@Composable
private fun NoBooksInSeries(locked: Boolean) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(24.dp)) {
    Icon(menu_book, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      if (locked) "No G books in this series" else "No books in this series",
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.Center,
    )
  }
}

internal const val ENGLISH_ONLY = "English only"

internal val TWO_COLUMNS = 1000.dp

private val EDGE = 12.dp

private val ROW_GAP = if (platformIsTouch) 8.dp else platformCellGap

private const val LOADING_INDICATOR_DELAY_MS = 300L

private const val REFRESH_START_TIMEOUT_MS = 2_000L
