package dev.marzahn.sankaku.features.grid

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridLayoutInfo
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull

internal data class GridCell(val lane: Int, val top: Int)

internal class LaidOutCells private constructor(val cells: Map<String, GridCell>) {
  fun seen(visible: Map<String, GridCell>): LaidOutCells {
    if (visible.isEmpty()) return this
    val shifts = visible.mapNotNull { (id, now) ->
      val then = cells[id] ?: return@mapNotNull null
      if (then.lane != now.lane) return LaidOutCells(visible)
      then.top - now.top
    }.distinct()
    val shift = shifts.singleOrNull() ?: return LaidOutCells(visible)
    if (visible.all { (id, now) -> cells[id]?.top == now.top + shift }) return this
    return LaidOutCells(cells + visible.mapValues { (_, now) -> now.copy(top = now.top + shift) })
  }

  companion object {
    val None = LaidOutCells(emptyMap())
  }
}

internal fun LazyStaggeredGridLayoutInfo.visibleCells(): Map<String, GridCell> =
  visibleItemsInfo.mapNotNull { item -> (item.key as? String)?.let { it to GridCell(item.lane, item.offset.y) } }.toMap()

internal fun readingOrder(count: Int, idAt: (Int) -> String?, laidOut: LaidOutCells): ReadingOrder {
  val drawn = (0 until count).mapNotNull { index -> idAt(index)?.let(laidOut.cells::get)?.let { index to it } }
  if (drawn.isEmpty()) return ReadingOrder.ListOrder
  val first = drawn.first().first
  val last = drawn.last().first
  val isDrawn = BooleanArray(count).also { array -> drawn.forEach { array[it.first] = true } }
  val order = ArrayList<Int>(count)
  (0 until first).forEach(order::add)
  drawn.sortedWith(compareBy({ it.second.top }, { it.second.lane }, { it.first })).forEach { (index) ->
    order.add(index)
    var next = index + 1
    while (next < last && !isDrawn[next]) order.add(next++)
  }
  (last + 1 until count).forEach(order::add)
  return ReadingOrder(order.toIntArray())
}

internal class ReadingOrder(private val indices: IntArray) {
  private val positions = IntArray(indices.size).also { for (position in indices.indices) it[indices[position]] = position }

  fun indexAt(position: Int): Int = indices.getOrElse(position) { position }

  fun positionOf(index: Int): Int = positions.getOrElse(index) { index }

  companion object {
    val ListOrder = ReadingOrder(IntArray(0))
  }
}

@Composable
internal fun rememberLaidOutCells(state: LazyStaggeredGridState, paused: () -> Boolean = { false }): State<LaidOutCells> {
  val laidOut = remember(state) { mutableStateOf(LaidOutCells.None) }
  LaunchedEffect(state) {
    snapshotFlow {
      val cells = state.layoutInfo.visibleCells()
      cells.takeUnless { paused() && laidOut.value.cells.isNotEmpty() }
    }.filterNotNull().collect { laidOut.value = laidOut.value.seen(it) }
  }
  return laidOut
}
