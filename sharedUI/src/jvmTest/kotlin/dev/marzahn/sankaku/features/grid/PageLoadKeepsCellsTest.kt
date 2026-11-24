package dev.marzahn.sankaku.features.grid

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)
class PageLoadKeepsCellsTest {
  private val fixture = GridScreenFixture()

  @BeforeTest
  fun start() = fixture.start()

  @AfterTest
  fun stop() = fixture.stop()

  private fun posts(numbers: IntRange) = numbers.map {
    PostDto(id = "p$it", md5 = "m$it", rating = "s", status = "active", previewWidth = 100, previewHeight = 100)
  }

  private fun pages(second: CompletableDeferred<Unit>) = object : PagingSource<Int, PostDto>() {
    override fun getRefreshKey(state: PagingState<Int, PostDto>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PostDto> = when (params.key) {
      null -> LoadResult.Page(posts(1..12), prevKey = null, nextKey = 1)
      else -> {
        second.await()
        LoadResult.Page(posts(13..40), prevKey = null, nextKey = null)
      }
    }
  }

  @Test
  fun aPageLoadingDoesNotRecomposeTheCellsOnScreen() = runDesktopComposeUiTest(width = 1200, height = 900) {
    val second = CompletableDeferred<Unit>()
    val flow = Pager(PagingConfig(pageSize = 12, initialLoadSize = 12)) { pages(second) }.flow
    val composed = mutableMapOf<String, Int>()
    var count = { 0 }
    setContent {
      SankakuTheme {
        SharedTransitionLayout {
          val items = flow.collectAsLazyPagingItems()
          count = { items.itemCount }
          PostGrid(
            items = items,
            overrides = emptyMap(),
            state = rememberLazyStaggeredGridState(),
            hiddenId = null,
            sharedId = null,
            viewerClosing = true,
            cursor = null,
            onOpen = {},
            onOpenInNewTab = {},
            onToggleFavourite = { _, _ -> false },
            isSeen = {
              composed[it.id] = (composed[it.id] ?: 0) + 1
              false
            },
          )
        }
      }
    }
    waitUntil(timeoutMillis = 5_000) { mainClock.advanceTimeByFrame(); composed.size >= 6 }
    waitForIdle()
    val before = composed.toMap()

    second.complete(Unit)
    waitUntil(timeoutMillis = 5_000) { mainClock.advanceTimeByFrame(); count() == 40 }
    waitForIdle()

    assertEquals(before, composed.filterKeys { it in before })
  }

  @Test
  fun aReloadThatChangesAPostOnScreenRecomposesItsCell() = runDesktopComposeUiTest(width = 1200, height = 900) {
    val first = posts(1..12)
    val data = MutableStateFlow(PagingData.from(first))
    val seen = mutableMapOf<String, Boolean>()
    setContent {
      SankakuTheme {
        SharedTransitionLayout {
          PostGrid(
            items = data.collectAsLazyPagingItems(),
            overrides = emptyMap(),
            state = rememberLazyStaggeredGridState(),
            hiddenId = null,
            sharedId = null,
            viewerClosing = true,
            cursor = null,
            onOpen = {},
            onOpenInNewTab = {},
            onToggleFavourite = { _, _ -> false },
            isSeen = {
              seen[it.id] = it.isFavorited
              false
            },
          )
        }
      }
    }
    waitUntil(timeoutMillis = 5_000) { mainClock.advanceTimeByFrame(); "p1" in seen }
    assertEquals(false, seen["p1"])

    data.value = PagingData.from(listOf(first[0].copy(isFavorited = true)) + first.drop(1))
    waitUntil(timeoutMillis = 5_000) { mainClock.advanceTimeByFrame(); seen["p1"] == true }
  }
}
