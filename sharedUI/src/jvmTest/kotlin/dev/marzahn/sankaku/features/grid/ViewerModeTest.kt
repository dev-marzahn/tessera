package dev.marzahn.sankaku.features.grid

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewerModeTest {
  @Test
  fun onlyALonePostsSidewaysSwipeMovesBetweenQueueItems() {
    assertTrue(ViewerMode.Tab.swipesQueue, "a kept post: the swipe moves on in the queue")
    assertFalse(ViewerMode.Overlay.swipesQueue, "over a grid: the swipe steps to the grid's next post")
  }
}
