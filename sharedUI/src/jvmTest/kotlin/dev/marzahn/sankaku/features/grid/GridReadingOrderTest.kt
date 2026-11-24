package dev.marzahn.sankaku.features.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GridReadingOrderTest {
  private val aspects = Random(110).let { random -> List(300) { 0.3f + random.nextFloat() * 2.7f } }
  private val ids = List(aspects.size) { "p$it" }

  private fun readingOrderOf(laidOut: LaidOutCells): List<Int> {
    val order = readingOrder(ids.size, ids::getOrNull, laidOut)
    return ids.indices.map(order::indexAt)
  }

  private val paused = mutableStateOf(false)

  private fun gridTest(block: ComposeUiTest.(LazyStaggeredGridState, () -> LaidOutCells) -> Unit) = runComposeUiTest {
    lateinit var state: LazyStaggeredGridState
    lateinit var laidOut: State<LaidOutCells>
    setContent {
      state = rememberLazyStaggeredGridState()
      laidOut = rememberLaidOutCells(state) { paused.value }
      LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(3), Modifier.size(360.dp, 800.dp), state, verticalItemSpacing = 4.dp) {
        items(aspects.size, key = { ids[it] }) { Box(Modifier.fillMaxWidth().aspectRatio(aspects[it])) }
      }
    }
    block(state) { runOnIdle { laidOut.value } }
  }

  @Test
  fun filledDownFromTheTopTheReadingOrderIsTheListOrder() = gridTest { state, laidOut ->
    repeat(15) {
      scroll(state, 500f)
    }
    val seen = laidOut()
    assertEquals(ids.indices.toList(), readingOrderOf(seen))
    assertTrue(seen.cells.size > 60)
  }

  @Test
  fun aboveAJumpTheGridIsLaidOutOtherwiseAndTheOrderFollowsIt() = gridTest { state, laidOut ->
    runOnIdle { state.requestScrollToItem(200) }
    repeat(6) { scroll(state, -500f) }
    val seen = laidOut()
    val order = readingOrderOf(seen)
    assertNotEquals(ids.indices.toList(), order)
    val onScreen = state.layoutInfo.visibleItemsInfo.sortedWith(compareBy({ it.offset.y }, { it.lane })).map { it.index }
    val drawnOrder = order.filter { "p$it" in seen.cells }
    val window = drawnOrder.filter { it in onScreen }
    assertEquals(onScreen, window)
  }

  @Test
  fun pausedTheOrderIgnoresAJumpAndPicksUpAgainAfter() = gridTest { state, laidOut ->
    scroll(state, 300f)
    val before = laidOut()
    paused.value = true
    runOnIdle { state.requestScrollToItem(200) }
    scroll(state, 10f)
    assertSame(before, laidOut())
    paused.value = false
    waitForIdle()
    assertTrue("p200" in laidOut().cells)
  }

  @Test
  fun pausedFromTheStartItStillTakesTheFirstLayout() {
    paused.value = true
    gridTest { state, laidOut ->
      assertTrue("p0" in laidOut().cells)
      val first = laidOut()
      scroll(state, 300f)
      assertSame(first, laidOut())
    }
  }

  private fun ComposeUiTest.scroll(state: LazyStaggeredGridState, px: Float) {
    runOnIdle { state.dispatchRawDelta(px) }
    waitForIdle()
  }
}
