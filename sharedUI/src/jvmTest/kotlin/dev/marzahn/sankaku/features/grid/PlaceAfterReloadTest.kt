package dev.marzahn.sankaku.features.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.data.GridPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PlaceAfterReloadTest {
  private val posts = List(100) { PostDto(id = "p$it", md5 = "m$it", rating = "s", status = "active") }

  @Test
  fun aScrolledGridShownAgainOnAnEmptyReloadKeepsItsPlace() = runDesktopComposeUiTest(width = 400, height = 800) {
    val data = MutableStateFlow(PagingData.from(posts))
    val reported = mutableListOf<GridPlace?>()
    var shown by mutableStateOf(true)
    lateinit var state: LazyStaggeredGridState
    lateinit var scope: CoroutineScope
    var itemCount = { 0 }
    setContent {
      state = rememberLazyStaggeredGridState()
      scope = rememberCoroutineScope()
      val items = data.collectAsLazyPagingItems()
      itemCount = { items.itemCount }
      if (shown) {
        LaunchedEffect(state) {
          state.placeReports(items, midway = false, cursorOf = { null }).collect { reported += it }
        }
        LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(3), state = state, modifier = Modifier.fillMaxSize()) {
          items(items.itemCount) { Box(Modifier.height(120.dp)) }
        }
      }
    }
    waitForIdle()
    scope.launch { state.scrollToItem(28) }
    waitForIdle()
    assertEquals(GridPlace("p28", index = 28), reported.last(), "scrolled: the first post whose top shows")
    val before = reported.size

    shown = false
    waitForIdle()
    data.value = PagingData.empty()
    waitForIdle()
    shown = true
    waitForIdle()

    assertEquals(before, reported.size, "nothing reported while the list is empty, so the saved place stays")

    data.value = PagingData.from(posts)
    waitUntil(timeoutMillis = 5_000) { mainClock.advanceTimeByFrame(); itemCount() > 0 }
    waitForIdle()
    assertEquals(before + 1, reported.size, "one report once the list is back")
    assertEquals(GridPlace("p28", index = 28), reported.last(), "the refilled grid is where it was")
  }

  @Test
  fun aGridOpenedMidwayCountsFromItsStartPlace() = runDesktopComposeUiTest(width = 400, height = 800) {
    val data = MutableStateFlow(PagingData.from(posts))
    val reported = mutableListOf<GridPlace?>()
    lateinit var state: LazyStaggeredGridState
    lateinit var scope: CoroutineScope
    var start by mutableStateOf<GridPlace?>(GridPlace("p3", "c8", 8, index = 237))
    setContent {
      state = rememberLazyStaggeredGridState()
      scope = rememberCoroutineScope()
      val items = data.collectAsLazyPagingItems()
      LaunchedEffect(state, start) {
        state.placeReports(items, midway = true, cursorOf = { null }, start = start).collect { reported += it }
      }
      LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(3), state = state, modifier = Modifier.fillMaxSize()) {
        items(items.itemCount) { Box(Modifier.height(120.dp)) }
      }
    }
    waitForIdle()
    scope.launch { state.scrollToItem(28) }
    waitForIdle()
    assertEquals(237 - 3 + 28, reported.last()?.index)

    start = GridPlace("p3", "c8", 8)
    waitForIdle()
    assertEquals(null, reported.last()?.index)
  }
}
