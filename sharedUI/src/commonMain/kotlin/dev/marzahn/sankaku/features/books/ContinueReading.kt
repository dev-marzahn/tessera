package dev.marzahn.sankaku.features.books

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.marzahn.sankaku.api.ApiError
import dev.marzahn.sankaku.api.dto.BookDto
import dev.marzahn.sankaku.auth.AuthSession
import dev.marzahn.sankaku.auth.AuthState
import dev.marzahn.sankaku.data.BooksRepository
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.data.ReadingProgress
import dev.marzahn.sankaku.data.SavedReading
import dev.marzahn.sankaku.features.grid.openInBackgroundLabel
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.ui.components.spatialSpec
import dev.marzahn.sankaku.ui.openOnClick
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class ContinueReadingViewModel(
  private val repository: BooksRepository,
  session: AuthSession,
  privacy: PrivacySettings,
  progress: ReadingProgress,
) : ViewModel() {
  private val _books = MutableStateFlow<List<BookDto>>(emptyList())

  val books: StateFlow<List<BookDto>> = _books.asStateFlow()

  private val asks = MutableStateFlow(0)
  private val shown = MutableStateFlow(false)
  private var unreachable = false

  init {
    viewModelScope.launch {
      session.state
        .filter { it !is AuthState.Restoring }
        .map { (it as? AuthState.SignedIn)?.user?.id }
        .distinctUntilChanged()
        .drop(1)
        .collect { _books.value = emptyList() }
    }
    viewModelScope.launch {
      val locked = session.booksRatingLocked(privacy, viewModelScope)
      shown.flatMapLatest { on ->
        if (!on) emptyFlow() else combine(locked, session.isSignedIn, progress.landed, asks) { l, signedIn, _, _ -> l to signedIn }
      }.collectLatest { (l, signedIn) ->
        if (!signedIn) {
          _books.value = emptyList()
          return@collectLatest
        }
        try {
          _books.value = repository.reading(l)
          unreachable = false
        } catch (e: ApiError) {
          unreachable = e is ApiError.Network
        }
      }
    }
  }

  fun shown(on: Boolean) {
    shown.value = on
  }

  fun refresh() {
    asks.value++
  }

  fun retryIfUnreachable() {
    if (unreachable) refresh()
  }
}

internal fun shelfBooks(books: List<BookDto>, readingOf: (BookDto) -> SavedReading?): List<BookDto> =
  books.filter { book -> readingOf(book)?.finished == false }

internal class ShelfResults(val row: LazyListState, private val isLocked: () -> Boolean) : BookResults() {
  var books: List<BookDto> by mutableStateOf(emptyList())

  override val count: Int get() = books.size
  override val locked: Boolean get() = isLocked()

  override fun bookAt(index: Int): ResultBook? = peek(index)

  override fun peek(index: Int): ResultBook? = books.getOrNull(index)?.let(::ResultBook)

  override suspend fun reveal(index: Int) {
    val info = row.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null || item.offset < info.viewportStartOffset || item.offset + item.size > info.viewportEndOffset) {
      row.scrollToItem(index)
    }
  }
}

@Composable
internal fun rememberShelfResults(locked: () -> Boolean): ShelfResults {
  val row = rememberLazyListState()
  return remember(row) { ShelfResults(row, locked) }
}

@Composable
internal fun ContinueReadingShelf(
  results: ShelfResults,
  locked: Boolean,
  readingOf: (BookDto) -> SavedReading?,
  onOpen: (index: Int) -> Unit,
  onKeep: (BookDto) -> Unit,
  modifier: Modifier = Modifier,
) {
  val books = results.books
  Column(modifier.fillMaxWidth().semantics { testTag = SHELF_TAG }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = SHELF_EDGE + 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(CONTINUE_READING, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
      Text("${books.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    LazyRow(
      state = results.row,
      contentPadding = PaddingValues(horizontal = SHELF_EDGE),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      itemsIndexed(books, key = { _, book -> book.id }) { index, book ->
        ShelfBook(
          book = book,
          reading = readingOf(book),
          locked = locked,
          coverHidden = results.hiddenCover == book.id,
          coverModifier = results.coverTracker(book.id),
          onOpen = { onOpen(index) },
          onKeep = { onKeep(book) },
        )
      }
    }
  }
}

@Composable
private fun ShelfBook(
  book: BookDto,
  reading: SavedReading?,
  locked: Boolean,
  coverHidden: Boolean,
  coverModifier: Modifier,
  onOpen: () -> Unit,
  onKeep: () -> Unit,
) {
  val title = book.title.ifBlank { UNTITLED }
  Column(
    Modifier
      .width(SHELF_COVER_WIDTH)
      .openOnClick(onOpen, onKeep, longPressLabel = openInBackgroundLabel())
      .semantics(mergeDescendants = true) {
        role = Role.Button
        testTag = SHELF_BOOK_TAG
        customActions = listOf(CustomAccessibilityAction(openInBackgroundLabel()) { onKeep(); true })
      },
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(coverModifier.size(SHELF_COVER_WIDTH, SHELF_COVER_WIDTH * 1.5f).alpha(if (coverHidden) 0f else 1f)) {
      BookPicture(book.coverPost, locked, Modifier.matchParentSize())
      reading?.let { CoverProgress(it, Modifier.matchParentSize()) }
    }
    Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    reading?.let {
      Text(readingLabel(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
internal fun ScrollAwayTop(
  bodyAtTop: () -> Boolean,
  top: @Composable () -> Unit,
  body: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  state: ScrollAway = remember { ScrollAway() },
) {
  val scope = rememberCoroutineScope()
  val atTop by rememberUpdatedState(bodyAtTop)
  val spec by rememberUpdatedState(spatialSpec<Float>())
  Layout(
    content = {
      Box { top() }
      Box { body() }
    },
    modifier = modifier
      .nestedScroll(state)
      .pointerInput(state) {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val up = event.type == PointerEventType.Scroll && event.changes.any { it.scrollDelta.y < 0f }
            if (up && atTop() && state.hidden > 0f) scope.launch { state.reveal(spec) }
          }
        }
      }
      .clipToBounds(),
  ) { measurables, constraints ->
    val topPlaceable = measurables[0].measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    if (topPlaceable.height == 0) {
      state.hidden = 0f
    } else if (state.height == 0) {
      state.hidden = if (bodyAtTop()) 0f else topPlaceable.height.toFloat()
    }
    state.height = topPlaceable.height
    val hidden = state.hidden.coerceIn(0f, topPlaceable.height.toFloat()).roundToInt()
    val shown = topPlaceable.height - hidden
    val bodyHeight = (constraints.maxHeight - shown).coerceAtLeast(0)
    val bodyPlaceable = measurables[1].measure(constraints.copy(minHeight = bodyHeight, maxHeight = bodyHeight))
    layout(constraints.maxWidth, constraints.maxHeight) {
      topPlaceable.place(0, -hidden)
      bodyPlaceable.place(0, shown)
    }
  }
}

@Stable
internal class ScrollAway : NestedScrollConnection {
  var height = 0
  var hidden by mutableFloatStateOf(0f)

  private val revealing = MutatorMutex()

  suspend fun show() {
    revealing.mutate { hidden = 0f }
  }

  suspend fun reveal(spec: AnimationSpec<Float>) {
    if (hidden <= 0f) return
    revealing.mutate { animate(hidden, 0f, animationSpec = spec) { value, _ -> hidden = value } }
  }

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    if (available.y >= 0f || height == 0) return Offset.Zero
    val take = maxOf(available.y, -(height - hidden))
    hidden -= take
    return Offset(0f, take)
  }

  override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
    if (available.y <= 0f || height == 0) return Offset.Zero
    val give = minOf(available.y, hidden)
    hidden -= give
    return Offset(0f, give)
  }
}

internal const val CONTINUE_READING = "Continue reading"
internal const val SHELF_TAG = "continue-reading"
internal const val SHELF_BOOK_TAG = "continue-reading-book"

private val SHELF_EDGE = 12.dp
private val SHELF_COVER_WIDTH = if (platformIsTouch) 88.dp else 104.dp
