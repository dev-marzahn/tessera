package dev.marzahn.sankaku.features.grid

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.IntRect
import dev.marzahn.sankaku.ui.components.Shortcuts
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal enum class GridMove { Up, Left, Down, Right }

internal fun gridMoveFor(key: Key): GridMove? = when {
  Shortcuts.Up.matches(key) -> GridMove.Up
  Shortcuts.Previous.matches(key) -> GridMove.Left
  Shortcuts.Down.matches(key) -> GridMove.Down
  Shortcuts.Next.matches(key) -> GridMove.Right
  else -> null
}

internal fun gridCellTag(index: Int) = "grid-cell-$index"

class GridCursorRequest(private val time: TimeSource = TimeSource.Monotonic, private val lifetime: Duration = 1.seconds) {
  private var until: TimeMark? = null
  private var tags: List<String>? = null

  fun request(tags: List<String>? = null) {
    until = time.markNow() + lifetime
    this.tags = tags
  }

  fun lapse() {
    until = null
  }

  fun take(tags: List<String>): Boolean {
    if (this.tags != null && this.tags != tags) return false
    return (until?.hasNotPassedNow() == true).also { until = null }
  }
}

internal fun LazyStaggeredGridState.cellBounds(postCount: Int, gridItems: GridItems = GridItems.None): Map<Int, IntRect> =
  layoutInfo.visibleItemsInfo
    .mapNotNull { info -> gridItems.postOf(info.index)?.let { it to info } }
    .filter { (post) -> post < postCount }
    .associate { (post, info) -> post to IntRect(info.offset, info.size) }

internal fun LazyStaggeredGridState.fullyShows(r: IntRect): Boolean =
  r.top >= 0 && r.bottom <= shownBottom()

internal fun LazyStaggeredGridState.shownBottom(): Int =
  layoutInfo.viewportSize.height - 2 * layoutInfo.beforeContentPadding

internal fun firstShown(cells: Map<Int, IntRect>, fullyShows: (IntRect) -> Boolean): Int? =
  cells.filter { fullyShows(it.value) }.ifEmpty { cells }
    .minWithOrNull(compareBy<Map.Entry<Int, IntRect>> { it.value.top }.thenBy { it.value.left })?.key

internal fun cellNear(cells: Map<Int, IntRect>, x: Int, y: Int): Int? =
  cells.minByOrNull { (_, r) ->
    val dx = (x - x.coerceIn(r.left, r.right)).toLong()
    val dy = (y - y.coerceIn(r.top, r.bottom)).toLong()
    dx * dx + dy * dy
  }?.key

private fun overlap(a: IntRect, b: IntRect) = min(a.bottom, b.bottom) - max(a.top, b.top)

private fun besideColumn(cells: Map<Int, IntRect>, from: Int, move: GridMove): Map<Int, IntRect> {
  val at = cells[from] ?: return emptyMap()
  val side = cells.filter { (i, r) -> i != from && if (move == GridMove.Right) r.left >= at.right else r.right <= at.left }
  val columns = side.values.map { it.left }.distinct().let { if (move == GridMove.Right) it.sorted() else it.sortedDescending() }
  val column = columns.firstOrNull { left -> side.values.any { it.left == left && overlap(it, at) > 0 } } ?: columns.firstOrNull()
  return side.filter { it.value.left == column }
}

internal fun nextCell(cells: Map<Int, IntRect>, from: Int, move: GridMove): Int? {
  val at = cells[from] ?: return null
  val others = cells.filterKeys { it != from }
  fun horizontal(r: IntRect) = min(r.right, at.right) - max(r.left, at.left)
  fun vertical(r: IntRect) = overlap(r, at)
  return when (move) {
    GridMove.Down -> others.filter { (_, r) -> r.top >= at.bottom && horizontal(r) > 0 }
      .minWithOrNull(compareBy<Map.Entry<Int, IntRect>> { it.value.top }.thenByDescending { horizontal(it.value) })?.key
    GridMove.Up -> others.filter { (_, r) -> r.bottom <= at.top && horizontal(r) > 0 }
      .minWithOrNull(compareByDescending<Map.Entry<Int, IntRect>> { it.value.bottom }.thenByDescending { horizontal(it.value) })?.key
    GridMove.Left, GridMove.Right -> besideColumn(cells, from, move).minWithOrNull(
      compareByDescending<Map.Entry<Int, IntRect>> { vertical(it.value) }.thenBy { it.value.top },
    )?.key
  }
}
