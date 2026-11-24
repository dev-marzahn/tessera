package dev.marzahn.sankaku.features.grid

import androidx.paging.LoadState
import kotlin.test.Test
import kotlin.test.assertEquals

class ViewerEndStateTest {
  @Test
  fun theTrailingPageFollowsTheNextPagesLoad() {
    assertEquals(ViewerEnd.Loading, viewerEnd(LoadState.Loading))
    assertEquals(ViewerEnd.Loading, viewerEnd(LoadState.NotLoading(endOfPaginationReached = false)))
    val failure = IllegalStateException("offline")
    assertEquals(ViewerEnd.Failed(failure), viewerEnd(LoadState.Error(failure)))
    assertEquals(ViewerEnd.NoMore, viewerEnd(LoadState.NotLoading(endOfPaginationReached = true)))
  }
}
