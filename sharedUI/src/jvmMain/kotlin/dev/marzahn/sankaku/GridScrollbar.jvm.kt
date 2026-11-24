package dev.marzahn.sankaku

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor

@Composable
actual fun GridScrollbar(state: LazyStaggeredGridState, postCount: Int, modifier: Modifier) {
  val count by rememberUpdatedState(postCount)
  val adapter = remember(state) { PostIndexScrollbarAdapter(state) { count } }
  VerticalScrollbar(
    adapter = adapter,
    modifier = modifier,
    style = defaultScrollbarStyle().copy(
      minimalHeight = 48.dp,
      thickness = SCROLLBAR_THICKNESS,
      shape = RoundedCornerShape(4.dp),
      unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
      hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    ),
  )
}

internal data class VisiblePost(val index: Int, val lane: Int, val top: Int, val bottom: Int)

internal data class PostRows(val offset: Double, val viewport: Double, val content: Double, val lanes: Int)

internal fun postRows(posts: List<VisiblePost>, viewStart: Int, viewEnd: Int, postCount: Int): PostRows {
  val cells = posts.filter { it.index < postCount && it.bottom > it.top }.distinctBy { it.index }
  val lanes = (cells.maxOfOrNull { it.lane } ?: 0).coerceAtLeast(0) + 1
  val last = cells.maxOfOrNull { it.index } ?: -1
  val hiddenAbove = (last + 1 - cells.size).coerceAtLeast(0)
  val scrolledPast = cells.sumOf { ((viewStart - it.top).toDouble() / (it.bottom - it.top)).coerceIn(0.0, 1.0) }
  val offset = (hiddenAbove + scrolledPast) / lanes
  val inView = cells.sumOf { (minOf(it.bottom, viewEnd) - maxOf(it.top, viewStart)).coerceAtLeast(0).toDouble() / (it.bottom - it.top) }
  val viewport = inView / lanes
  return PostRows(offset, viewport, maxOf(postCount.toDouble() / lanes, offset + viewport), lanes)
}

internal class PostIndexScrollbarAdapter(
  private val state: LazyStaggeredGridState,
  private val postCount: () -> Int,
) : ScrollbarAdapter {
  private val rows: PostRows
    get() {
      val info = state.layoutInfo
      val posts = info.visibleItemsInfo.map { VisiblePost(it.index, it.lane, it.offset.y, it.offset.y + it.size.height) }
      return postRows(posts, 0, info.viewportSize.height - info.beforeContentPadding, postCount())
    }

  override val scrollOffset: Double get() = rows.offset * ROW
  override val contentSize: Double get() = rows.content * ROW
  override val viewportSize: Double get() = rows.viewport * ROW

  override suspend fun scrollTo(scrollOffset: Double) {
    val count = postCount()
    if (count == 0) return
    val target = (scrollOffset / ROW).coerceAtLeast(0.0)
    val start = rows
    if (abs(target - start.offset) > maxOf(start.viewport, 1.0) * JUMP_VIEWPORTS) {
      state.scrollToItem(floor(target * start.lanes).toInt().coerceAtMost(count - 1))
    }
    repeat(CORRECTIONS) {
      val diff = target - rows.offset
      if (abs(diff) < PRECISION) return
      val rowPixels = rowPixels() ?: return
      if (state.scrollBy((diff * rowPixels).toFloat()) == 0f) return
    }
  }

  private fun rowPixels(): Double? {
    val info = state.layoutInfo
    val count = postCount()
    val heights = info.visibleItemsInfo.filter { it.index < count && it.size.height > 0 }.map { it.size.height }
    if (heights.isEmpty()) return null
    return heights.average() + info.mainAxisItemSpacing
  }

  private companion object {
    const val ROW = 1_000.0

    const val JUMP_VIEWPORTS = 2.0
    const val CORRECTIONS = 4
    const val PRECISION = 0.001
  }
}

private val SCROLLBAR_THICKNESS = 8.dp

actual val gridScrollbarRoom: Dp = SCROLLBAR_THICKNESS + 4.dp
