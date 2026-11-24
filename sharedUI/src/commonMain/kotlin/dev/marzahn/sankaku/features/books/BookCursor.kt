package dev.marzahn.sankaku.features.books

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.IntRect
import dev.marzahn.sankaku.platformIsTouch
import dev.marzahn.sankaku.ui.components.LocalKeyFocus
import dev.marzahn.sankaku.features.grid.GridMove
import dev.marzahn.sankaku.features.grid.cellNear
import dev.marzahn.sankaku.features.grid.firstShown
import dev.marzahn.sankaku.features.grid.gridMoveFor
import dev.marzahn.sankaku.features.grid.nextCell
import dev.marzahn.sankaku.ui.components.OncePerPress
import dev.marzahn.sankaku.ui.components.Shortcuts
import dev.marzahn.sankaku.ui.components.isCommand
import dev.marzahn.sankaku.ui.components.tabStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal enum class BookKey {
  Open,

  Keep,

  Favourite,
}

@Stable
internal class BookCursor(
  private val listState: LazyGridState,
  private val scope: CoroutineScope,
  private val saved: MutableState<Int?>,
) {
  val index: Int? get() = saved.value

  var pendingFocus: Int? by mutableStateOf(null)
    private set

  private fun put(index: Int?) {
    saved.value = index
    pendingFocus = index
  }

  fun focused(index: Int) {
    saved.value = index
  }

  fun focusTaken() {
    pendingFocus = null
  }

  fun keysReturned() {
    pendingFocus = saved.value?.takeIf(::laidOut)
  }

  private fun laidOut(index: Int) = listState.layoutInfo.visibleItemsInfo.any { it.index == index }

  private var step: Job? by mutableStateOf(null)

  private val presses = OncePerPress()

  private var opening: Int? = null

  fun letGo() {
    presses.letGo()
    opening = null
  }

  fun onKey(event: KeyEvent, count: Int, act: (BookKey, Int) -> Boolean): Boolean {
    val newPress = presses.isNewPress(event)
    if (event.type == KeyEventType.KeyUp && event.key == Shortcuts.OpenHere.key) {
      val at = opening ?: return false
      opening = null
      if (at < count && saved.value == at) act(BookKey.Open, at)
      return true
    }
    if (event.type != KeyEventType.KeyDown) return false
    if (event.isCommand || event.isAltPressed || event.tabStep != null) return false
    val move = gridMoveFor(event.key)
    if (move != null) {
      if (step?.isActive != true) {
        val from = saved.value
        val page = event.isShiftPressed && (move == GridMove.Up || move == GridMove.Down)
        step = scope.launch { put(listState.moveBookCursor(from, move, page, count)) }
      }
      return true
    }
    val at = saved.value?.takeIf { it < count } ?: return false
    val key = when (event.key) {
      Shortcuts.OpenHere.key -> BookKey.Open
      Shortcuts.OpenInTab.key -> BookKey.Keep
      Shortcuts.Favourite.key -> BookKey.Favourite
      else -> return false
    }
    if (!newPress && key != BookKey.Keep) return true
    if (key == BookKey.Open) {
      opening = at
      return true
    }
    return act(key, at)
  }

  fun hide() {
    put(null)
    opening = null
  }

  internal fun readerClosedOn(index: Int) {
    if (saved.value == null) return
    saved.value = index
    pendingFocus = index.takeIf(::laidOut)
  }
}

@Composable
internal fun Modifier.bookCursorFocus(cursor: BookCursor, index: Int, onKey: (KeyEvent) -> Boolean): Modifier {
  if (platformIsTouch) return this
  val focus = remember { FocusRequester() }
  var ownFocus by remember { mutableStateOf(false) }
  if (cursor.pendingFocus == index) {
    LaunchedEffect(Unit) {
      withFrameNanos {}
      focus.requestFocus()
      cursor.focusTaken()
    }
  }
  return focusRequester(focus)
    .onFocusChanged {
      ownFocus = it.isFocused
      if (it.hasFocus) cursor.focused(index)
    }
    .onPreviewKeyEvent { ownFocus && it.key == Shortcuts.OpenHere.key && onKey(it) }
}

@Composable
internal fun rememberBookCursor(listState: LazyGridState, results: BookResults?, reader: BookReader): BookCursor {
  val saved = rememberSaveable { mutableStateOf<Int?>(null) }
  val scope = rememberCoroutineScope()
  val cursor = remember(listState) { BookCursor(listState, scope, saved) }
  val keyReturns = LocalKeyFocus.current.returns
  LaunchedEffect(cursor, keyReturns) { if (reader.session.value == null) cursor.keysReturned() }
  LaunchedEffect(cursor, results) {
    if (results == null) return@LaunchedEffect
    var last: Int? = null
    reader.session.filter { last != null || it?.results === results }.collect { session ->
      if (session?.results === results) {
        last = session.index
      } else {
        last?.let(cursor::readerClosedOn)
        last = null
      }
    }
  }
  return cursor
}

private fun LazyGridState.rowBounds(count: Int): Map<Int, IntRect> =
  layoutInfo.visibleItemsInfo.filter { it.index < count }.associate { it.index to IntRect(it.offset, it.size) }

private fun LazyGridState.shownBottom(): Int =
  layoutInfo.viewportSize.height - layoutInfo.beforeContentPadding - layoutInfo.afterContentPadding

private fun LazyGridState.fullyShows(r: IntRect): Boolean = r.top >= 0 && r.bottom <= shownBottom()

private suspend fun LazyGridState.moveBookCursor(from: Int?, move: GridMove, page: Boolean, count: Int): Int? {
  val rows = rowBounds(count)
  val at = from?.let(rows::get) ?: return firstShown(rows) { fullyShows(it) } ?: from
  val height = layoutInfo.viewportSize.height.toFloat()
  val down = if (move == GridMove.Down) 1 else -1
  var scrolled = 0
  suspend fun rowsScrolled(share: Float): Map<Int, IntRect> {
    scrolled += scrollBy(down * height * share).roundToInt()
    withFrameNanos {}
    return rowBounds(count) + (from to at.translate(0, -scrolled))
  }
  val next = when {
    page -> cellNear(rowsScrolled(PAGE_JUMP), at.center.x, at.center.y)
    move == GridMove.Left || move == GridMove.Right -> nextCell(rows, from, move)
    else -> nextCell(rows, from, move) ?: (1..LOOK_AHEAD_STEPS).firstNotNullOfOrNull { nextCell(rowsScrolled(0.5f), from, move) }
  }
  showWhole(next ?: from)
  return next ?: from
}

private suspend fun LazyGridState.showWhole(index: Int) {
  if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
  val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
  val top = item.offset.y
  val bottom = top + item.size.height
  val delta = when {
    top < 0 -> top
    bottom > shownBottom() -> minOf(bottom - shownBottom(), top)
    else -> 0
  }
  if (delta != 0) scrollBy(delta.toFloat())
}

private const val PAGE_JUMP = 0.85f

private const val LOOK_AHEAD_STEPS = 4
