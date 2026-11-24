package dev.marzahn.sankaku

import dev.marzahn.sankaku.features.grid.gridColumns
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneGridColumnsTest {
  private val density = 3f

  private fun px(dp: Float) = (dp * density).roundToInt()

  private fun columnsAt(minCellDp: Int): Int = gridColumns(
    width = px(360f) - px(platformGridEdge.value) * 2 - px(gridScrollbarRoom.value),
    minCell = px(minCellDp.toFloat()),
    gap = px(platformCellGap.value),
  )

  @Test
  fun theDefaultFitsThreeColumnsOn360Dp() {
    assertEquals(3, columnsAt(platformPostSizes.default))
  }

  @Test
  fun theSlidersEndsMakeFourAndOneColumns() {
    assertEquals(4, columnsAt(platformPostSizes.min))
    assertEquals(1, columnsAt(platformPostSizes.max))
  }
}
