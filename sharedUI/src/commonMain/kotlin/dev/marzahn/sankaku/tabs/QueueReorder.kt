package dev.marzahn.sankaku.tabs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.LocalPinnableContainer
import kotlin.math.abs
import kotlin.math.min

internal class DragSpan(val own: IntRange, val passed: IntRange, val between: IntRange)

internal fun dragSpan(rows: List<QueueRow>, id: TabId, down: Boolean): DragSpan? {
  val items = rows.indices.filter { rows[it] !is QueueRow.Inline }
  val at = items.indexOfFirst { rows[it].isTab(id) }
  if (at < 0) return null
  fun isKept(i: Int) = (items.getOrNull(i)?.let(rows::get) as? QueueRow.Item)?.indented == true
  fun isHead(i: Int) = items.getOrNull(i)?.let(rows::get).let { it is QueueRow.ClosedBlock || (it is QueueRow.Item && !it.indented) }
  val keptPost = isKept(at)
  var end = at
  if (!keptPost) while (isKept(end + 1)) end++
  val passed = if (down) {
    val first = end + 1
    when {
      keptPost -> if (isKept(first) || isHead(first)) first..first else return null
      !isHead(first) -> return null
      else -> {
        var last = first
        while (isKept(last + 1)) last++
        first..last
      }
    }
  } else {
    var first = at - 1
    when {
      keptPost -> if (first >= 0) first..first else return null
      else -> {
        while (isKept(first)) first--
        if (!isHead(first)) return null
        first..at - 1
      }
    }
  }
  val own = items[at]..items[end]
  val crossed = items[passed.first]..items[passed.last]
  val between = if (down) own.last + 1 until crossed.first else crossed.last + 1 until own.first
  return DragSpan(own, crossed, between)
}

internal class Reorder {
  var dragging by mutableStateOf<TabId?>(null)
    private set

  var drags by mutableIntStateOf(0)
    private set

  private var key: Any? = null
  private var grabTop = 0f

  private var moved by mutableFloatStateOf(0f)

  private var slot by mutableFloatStateOf(0f)

  private var size = 0f

  private var placedTop by mutableFloatStateOf(0f)

  private var stepFrom = -1
  private var waited = 0

  private var index: () -> Int = { -1 }

  private var released = false

  fun start(id: TabId, rowKey: Any, list: LazyListState) {
    val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == rowKey }
    val top = info?.offset?.toFloat() ?: 0f
    val shown = if (dragging == id && key == rowKey) shownTop(list) else top
    key = rowKey
    size = info?.size?.toFloat() ?: 0f
    grabTop = shown
    slot = top
    placedTop = top
    moved = 0f
    stepFrom = -1
    released = false
    dragging = id
    drags++
  }

  fun drag(dy: Float) {
    if (dragging != null && !released) moved += dy
  }

  fun release() {
    released = true
  }

  fun placed(top: Float) {
    placedTop = top
  }

  fun offset(list: LazyListState): Float = shownTop(list) - placedTop

  private fun fromSlot(list: LazyListState): Float = shownTop(list) - (laidOut(list)?.offset?.toFloat() ?: slot)

  private fun laidOut(list: LazyListState): LazyListItemInfo? =
    list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.takeIf { stepFrom < 0 || caughtUp(it) }

  private fun caughtUp(info: LazyListItemInfo): Boolean {
    val now = index()
    return now != stepFrom && info.index == now
  }

  private fun visibleEnd(list: LazyListState) = list.layoutInfo.let { it.viewportEndOffset - it.afterContentPadding }.toFloat()

  private fun shownTop(list: LazyListState): Float {
    val height = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.size?.toFloat() ?: size
    return (grabTop + moved).coerceIn(0f, (visibleEnd(list) - height).coerceAtLeast(0f))
  }

  suspend fun run(
    list: LazyListState,
    rows: () -> List<QueueRow>,
    rowKey: (QueueRow) -> Any,
    step: (down: Boolean) -> Int?,
    rowHeight: Float,
    edge: Float,
    speed: Float,
  ) {
    val id = dragging ?: return
    val drag = drags
    index = { rows().indexOfFirst { it.isTab(id) } }
    var last = withFrameNanos { it }
    while (drag == drags) {
      val now = withFrameNanos { it }
      val seconds = (now - last) / 1_000_000_000f
      last = now
      settle(list)
      val stepping = stepPastNeighbour(list, rows(), rowKey, id, rowHeight, step)
      if (released && !stepping) break
      if (!released && !stepping) scrollNearEdge(list, rowHeight, edge, speed * seconds)
    }
    if (drag != drags) return
    settle(list)
    moved = shownTop(list) - grabTop
    val from = offset(list)
    if (from != 0f) {
      animate(from, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
        settle(list)
        moved += value - offset(list)
      }
    }
    if (drag != drags) return
    dragging = null
    key = null
  }

  private fun settle(list: LazyListState) {
    val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    if (info == null) {
      stepFrom = -1
      return
    }
    if (stepFrom >= 0 && !caughtUp(info) && ++waited < MAX_WAIT_FRAMES) return
    stepFrom = -1
    slot = info.offset.toFloat()
    size = info.size.toFloat()
  }

  private suspend fun scrollNearEdge(list: LazyListState, rowHeight: Float, edge: Float, step: Float) {
    val height = size.takeIf { it > 0f } ?: rowHeight
    val top = grabTop + moved
    val end = visibleEnd(list)
    val zone = min(edge, end / 4)
    if (zone <= 0f) return
    val by = when {
      moved < 0 && top < zone -> -step * min(1f, (zone - top) / zone)
      moved > 0 && top + height > end - zone -> step * min(1f, (top + height - (end - zone)) / zone)
      else -> return
    }
    slot -= list.scrollBy(by)
  }

  private fun stepPastNeighbour(
    list: LazyListState,
    rows: List<QueueRow>,
    rowKey: (QueueRow) -> Any,
    id: TabId,
    rowHeight: Float,
    step: (down: Boolean) -> Int?,
  ): Boolean {
    if (stepFrom >= 0) return true
    val offset = fromSlot(list)
    if (offset == 0f) return false
    val down = offset > 0
    val span = dragSpan(rows, id, down) ?: return false
    val visible = list.layoutInfo.visibleItemsInfo
    fun height(range: IntRange) =
      range.sumOf { i -> (visible.firstOrNull { it.key == rowKey(rows[i]) }?.size?.toFloat() ?: rowHeight).toDouble() }
        .toFloat()
    val between = height(span.between)
    val passed = height(span.passed)
    if (abs(offset) <= between + passed / 2) return false
    val first = list.firstVisibleItemIndex
    val firstOffset = list.firstVisibleItemScrollOffset
    val before = rows.indexOfFirst { it.isTab(id) }
    val by = step(down) ?: return false
    list.requestScrollToItem(first, firstOffset)
    if (by == 0) return false
    stepFrom = before
    waited = 0
    slot += if (down) between + passed else -(between + passed)
    return true
  }
}

private const val MAX_WAIT_FRAMES = 10

@Composable
internal fun PinWhile(pinned: Boolean) {
  val container = LocalPinnableContainer.current
  DisposableEffect(pinned, container) {
    val handle = if (pinned) container?.pin() else null
    onDispose { handle?.release() }
  }
}
