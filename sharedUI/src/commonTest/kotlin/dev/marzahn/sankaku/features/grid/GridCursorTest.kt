package dev.marzahn.sankaku.features.grid

import androidx.compose.ui.unit.IntRect
import dev.marzahn.sankaku.api.dto.PostDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class GridCursorTest {
  @Test
  fun aCursorRequestGoesToTheFirstGridThatAsksAndLapsesAfterItsLifetime() {
    val time = TestTimeSource()
    val request = GridCursorRequest(time, lifetime = 1.seconds)

    request.request()
    assertTrue(request.take(sky))
    assertFalse(request.take(sky), "only the first grid gets it")

    request.request()
    time += 1.seconds
    assertFalse(request.take(sky))

    request.request()
    request.lapse()
    assertFalse(request.take(sky))
  }

  @Test
  fun aCursorRequestForASearchGoesOnlyToThatSearchsGrid() {
    val request = GridCursorRequest(TestTimeSource())

    request.request(sky)
    assertFalse(request.take(listOf("sea")))
    assertTrue(request.take(sky))
  }

  private val sky = listOf("sky")

  private val cells = mapOf(
    0 to IntRect(0, 0, 100, 150),
    1 to IntRect(110, 0, 210, 80),
    2 to IntRect(220, 0, 320, 100),
    4 to IntRect(110, 90, 210, 300),
    5 to IntRect(220, 110, 320, 170),
    3 to IntRect(0, 160, 100, 260),
    6 to IntRect(220, 180, 320, 400),
  )

  @Test
  fun downGoesToTheCellBelowInTheSameColumnNotTheNextIndex() {
    assertEquals(3, nextCell(cells, from = 0, GridMove.Down))
    assertEquals(4, nextCell(cells, from = 1, GridMove.Down))
  }

  @Test
  fun upGoesToTheCellAboveInTheSameColumn() {
    assertEquals(5, nextCell(cells, from = 6, GridMove.Up))
  }

  @Test
  fun sidewaysGoesToTheNeighbouringColumnsCellThatOverlapsMostVertically() {
    assertEquals(3, nextCell(cells, from = 4, GridMove.Left))
    assertEquals(6, nextCell(cells, from = 4, GridMove.Right))
    assertEquals(1, nextCell(cells, from = 0, GridMove.Right))
  }

  @Test
  fun sidewaysTakesTheCellThatOverlapsMostEvenWhenItStartsAbove() {
    val grid = mapOf(
      10 to IntRect(0, 100, 100, 300),
      11 to IntRect(110, 0, 210, 250),
      12 to IntRect(110, 250, 210, 500),
    )
    assertEquals(11, nextCell(grid, from = 10, GridMove.Right))
    assertEquals(10, nextCell(grid, from = 12, GridMove.Left), "the only cell beside it")
  }

  @Test
  fun aTallCellLeadsBackToTheShortCellBesideItThatItOverlapsMostWhereverTheCursorCameFrom() {
    val grid = mapOf(
      21 to IntRect(0, 0, 100, 140),
      22 to IntRect(0, 150, 100, 300),
      20 to IntRect(110, 0, 210, 300),
    )
    assertEquals(20, nextCell(grid, from = 21, GridMove.Right))
    assertEquals(20, nextCell(grid, from = 22, GridMove.Right))
    assertEquals(22, nextCell(grid, from = 20, GridMove.Left))
  }

  @Test
  fun anExactTieSidewaysGoesToTheUpperCell() {
    val grid = mapOf(
      30 to IntRect(0, 0, 100, 200),
      32 to IntRect(110, 100, 210, 200),
      31 to IntRect(110, 0, 210, 100),
    )
    assertEquals(31, nextCell(grid, from = 30, GridMove.Right))
  }

  @Test
  fun aCellMissingFromTheCellsIsPassedOverToTheNextBesideIt() {
    val grid = mapOf(
      40 to IntRect(0, 0, 100, 120),
      41 to IntRect(110, 0, 210, 100),
      43 to IntRect(110, 160, 210, 300),
      42 to IntRect(220, 0, 320, 150),
    ) - 41
    assertEquals(42, nextCell(grid, from = 40, GridMove.Right))
    assertEquals(40, nextCell(grid, from = 42, GridMove.Left))
  }

  @Test
  fun aCellKeepsItsPicturesShape() {
    val wide = PostDto(id = "1", md5 = "m1", rating = "s", status = "active", previewWidth = 300, previewHeight = 100)
    assertEquals(3f, gridCellRatio(wide))
  }

  @Test
  fun nothingInThatDirectionGivesNull() {
    assertNull(nextCell(cells, from = 0, GridMove.Left))
    assertNull(nextCell(cells, from = 6, GridMove.Right))
    assertNull(nextCell(cells, from = 1, GridMove.Up))
    assertNull(nextCell(cells, from = 6, GridMove.Down))
  }
}
