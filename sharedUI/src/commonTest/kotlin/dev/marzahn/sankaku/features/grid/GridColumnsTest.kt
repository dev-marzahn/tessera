package dev.marzahn.sankaku.features.grid

import kotlin.test.Test
import kotlin.test.assertEquals

class GridColumnsTest {
  @Test
  fun columnsAreCountedAsAdaptiveCellsCountThem() {
    assertEquals(8, gridColumns(width = 1904, minCell = 220, gap = 8))
    assertEquals(4, gridColumns(width = 1904, minCell = 460, gap = 8))
    assertEquals(4, gridColumns(width = 424, minCell = 100, gap = 8))
    assertEquals(3, gridColumns(width = 423, minCell = 100, gap = 8))
  }

  @Test
  fun aPhonesDefaultFitsThreeColumnsOn360Dp() {
    val density = 3
    val grid = (360 - 2 * 6) * density
    assertEquals(3, gridColumns(width = grid, minCell = 110 * density, gap = 4 * density))
    assertEquals(2, gridColumns(width = grid, minCell = 120 * density, gap = 4 * density))
    assertEquals(4, gridColumns(width = grid, minCell = 80 * density, gap = 4 * density))
    assertEquals(1, gridColumns(width = grid, minCell = 200 * density, gap = 4 * density))
  }

  @Test
  fun aGridNarrowerThanOneColumnStillHasOne() {
    assertEquals(1, gridColumns(width = 50, minCell = 220, gap = 8))
    assertEquals(1, gridColumns(width = 0, minCell = 220, gap = 8))
  }
}
