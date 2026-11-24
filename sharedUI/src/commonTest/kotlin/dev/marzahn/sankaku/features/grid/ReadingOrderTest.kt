package dev.marzahn.sankaku.features.grid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ReadingOrderTest {
  private fun ids(count: Int) = (0 until count).map { "p$it" }

  private fun cells(vararg cells: Pair<Int, GridCell>) = cells.associate { (index, cell) -> "p$index" to cell }

  private fun order(count: Int, laidOut: LaidOutCells): List<Int> {
    val ids = ids(count)
    val order = readingOrder(count, ids::getOrNull, laidOut)
    return (0 until count).map(order::indexAt)
  }

  @Test
  fun drawnCellsAreReadByTopEdgeThenLeftToRight() {
    val laidOut = LaidOutCells.None.seen(
      cells(
        0 to GridCell(lane = 0, top = 0),
        1 to GridCell(lane = 1, top = 150),
        2 to GridCell(lane = 2, top = 300),
        3 to GridCell(lane = 0, top = 100),
        4 to GridCell(lane = 1, top = 300),
        5 to GridCell(lane = 2, top = 0),
      ),
    )
    assertEquals(listOf(0, 5, 3, 1, 4, 2), order(6, laidOut))
    val order = readingOrder(6, ids(6)::getOrNull, laidOut)
    assertEquals(listOf(0, 3, 5, 2, 4, 1), (0 until 6).map(order::positionOf))
  }

  @Test
  fun undrawnPostsKeepTheirListOrderAroundTheDrawnOnes() {
    val laidOut = LaidOutCells.None.seen(
      cells(
        2 to GridCell(lane = 1, top = 50),
        3 to GridCell(lane = 0, top = 0),
        5 to GridCell(lane = 2, top = 10),
      ),
    )
    assertEquals(listOf(0, 1, 3, 4, 5, 2, 6, 7), order(8, laidOut))
  }

  @Test
  fun withNothingDrawnItsTheListOrder() {
    assertEquals(listOf(0, 1, 2), order(3, LaidOutCells.None))
  }

  @Test
  fun scrolledCellsKeepTheirPlaceAgainstTheOnesOnScreen() {
    val first = LaidOutCells.None.seen(cells(0 to GridCell(0, 0), 1 to GridCell(1, 250)))
    val scrolled = first.seen(cells(1 to GridCell(1, 50), 2 to GridCell(0, 100)))
    assertEquals(GridCell(0, 300), scrolled.cells["p2"])
    assertEquals(GridCell(0, 0), scrolled.cells["p0"])
    assertEquals(listOf(0, 1, 2), order(3, scrolled))
  }

  @Test
  fun aLayoutWithNothingNewKeepsTheSameState() {
    val first = LaidOutCells.None.seen(cells(0 to GridCell(0, 0), 1 to GridCell(1, 250)))
    assertSame(first, first.seen(cells(0 to GridCell(0, -40), 1 to GridCell(1, 210))))
    assertSame(first, first.seen(emptyMap()))
  }

  @Test
  fun aJumpOrReflowStartsAgainFromTheScreen() {
    val first = LaidOutCells.None.seen(cells(0 to GridCell(0, 0), 1 to GridCell(1, 0), 2 to GridCell(2, 0)))
    assertEquals(setOf("p40", "p41"), first.seen(cells(40 to GridCell(0, 0), 41 to GridCell(1, 0))).cells.keys)
    assertEquals(setOf("p0", "p3"), first.seen(cells(0 to GridCell(1, 0), 3 to GridCell(0, 0))).cells.keys)
    assertEquals(setOf("p0", "p1"), first.seen(cells(0 to GridCell(0, 0), 1 to GridCell(1, 30))).cells.keys)
  }

  @Test
  fun aLoadedPageSlotsInWithoutMovingWhatsAlreadyOrdered() {
    val laidOut = LaidOutCells.None.seen(
      cells(0 to GridCell(0, 0), 1 to GridCell(1, 0), 2 to GridCell(2, 0), 3 to GridCell(1, 120)),
    )
    val before = order(4, laidOut)
    val after = order(6, laidOut)
    assertEquals(before, after.take(4))
    assertEquals(listOf(4, 5), after.drop(4))
    val drawn = laidOut.seen(cells(3 to GridCell(1, 120), 4 to GridCell(0, 100), 5 to GridCell(2, 130)))
    assertEquals(listOf(0, 1, 2, 4, 3, 5), order(6, drawn))
  }

  @Test
  fun outsideTheOrderTheIndexStands() {
    val order = ReadingOrder.ListOrder
    assertEquals(7, order.indexAt(7))
    assertEquals(7, order.positionOf(7))
  }
}
