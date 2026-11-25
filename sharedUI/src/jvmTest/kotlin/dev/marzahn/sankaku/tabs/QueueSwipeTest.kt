package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.Feed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class QueueSwipeTest {
  private val slop = 8f

  @Test
  fun aDeliberateSwipeIsTakenOnlyPastTwiceTheSlopBeforeOneSlopUpOrDown() {
    assertEquals(SwipeStart.Wait, swipeStart(dx = 1.5f * slop, dy = 0f, slop))
    assertEquals(SwipeStart.Take, swipeStart(dx = -2f * slop, dy = 0.9f * slop, slop))
    assertEquals(SwipeStart.Take, swipeStart(dx = 2f * slop, dy = 0f, slop))
    assertEquals(SwipeStart.LetGo, swipeStart(dx = 2f * slop, dy = slop, slop))
    assertEquals(SwipeStart.LetGo, swipeStart(dx = 0f, dy = -slop, slop))
  }

  @Test
  fun aLetGoSettlesAsThePagerDoes() {
    val width = 400f
    val fling = 400f
    fun target(offset: Float, velocity: Float, previous: Boolean = true, next: Boolean = true) =
      swipeTarget(offset, velocity, width, fling, previous, next)

    assertEquals(QueueMove.Next, target(-201f, 0f))
    assertEquals(QueueMove.Stay, target(-199f, 0f))
    assertEquals(QueueMove.Previous, target(201f, 0f))
    assertEquals(QueueMove.Next, target(-20f, -fling))
    assertEquals(QueueMove.Stay, target(-300f, fling))
    assertEquals(QueueMove.Stay, target(-300f, -fling, next = false))
    assertEquals(QueueMove.Stay, target(300f, fling, previous = false))
  }

  @Test
  fun backOnTheFirstItemsLonePostLeavesTheApp() {
    assertEquals(QueueBack.Tab, queueBack(cardUp = false, lonePost = true, previous = false))
    assertEquals(QueueBack.Previous, queueBack(cardUp = false, lonePost = true, previous = true))
    for (lonePost in listOf(true, false)) {
      for (previous in listOf(true, false)) {
        assertEquals(QueueBack.Previous, queueBack(cardUp = true, lonePost = lonePost, previous = previous), "card")
      }
    }
    assertEquals(QueueBack.Tab, queueBack(cardUp = false, lonePost = false, previous = false))
    assertEquals(QueueBack.Tab, queueBack(cardUp = false, lonePost = false, previous = true))
  }

  @Test
  fun onACardASwipeGoesBackOnlyAndOnAnItemEitherWayWithSomewhereToGo() {
    val item = Ahead.Item(TabsController().activeTab)
    val end = Ahead.End(END_PANE)
    for (ahead in listOf(item, Ahead.Sort, end, Ahead.Nothing)) {
      for (previous in listOf(true, false)) {
        assertEquals(SwipeWays(back = true, forward = false), swipeWays(cardUp = true, previous = previous, ahead = ahead), "card, $ahead")
      }
    }
    assertEquals(SwipeWays(back = true, forward = true), swipeWays(cardUp = false, previous = true, ahead = item))
    assertEquals(SwipeWays(back = false, forward = true), swipeWays(cardUp = false, previous = false, ahead = Ahead.Sort))
    assertEquals(SwipeWays(back = false, forward = true), swipeWays(cardUp = false, previous = false, ahead = end))
    assertEquals(SwipeWays(back = true, forward = false), swipeWays(cardUp = false, previous = true, ahead = Ahead.Nothing))

    val card = swipeWays(cardUp = true, previous = false, ahead = end)
    assertEquals(QueueMove.Stay, swipeTarget(-399f, -1000f, 400f, 400f, card.back, card.forward))
    assertEquals(QueueMove.Previous, swipeTarget(201f, 0f, 400f, 400f, card.back, card.forward))
    assertEquals(QueueMove.Previous, swipeTarget(20f, 1000f, 400f, 400f, card.back, card.forward))
  }

  private fun landsOn(count: Int = 3, gesture: TouchInjectionScope.() -> Unit): Int {
    val tabs = TabsController()
    repeat(count - 1) { tabs.open(Route.Grid(listOf("t$it")), background = true) }
    if (count > 1) tabs.next()
    return landsOn(tabs, gesture)
  }

  private fun landsOn(tabs: TabsController, gesture: TouchInjectionScope.() -> Unit): Int {
    runComposeUiTest {
      setContent {
        val activeId by tabs.active.collectAsState()
        val tabList by tabs.tabs.collectAsState()
        val active = tabList.first { it.id == activeId }
        Box(Modifier.size(400.dp)) {
          QueueSwipeHost(active, tabs = tabs, upNext = noUpNext(tabs), undo = ClosedTabUndo(tabs), preload = { _, _ -> emptyMap() }, content = {
            Box(Modifier.size(400.dp).testTag("grid").queueSwipe(enabled = true, from = active.id)) {
              Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { Box(Modifier.height(4000.dp)) }
            }
          })
        }
      }
      onNodeWithTag("grid").performTouchInput(gesture)
      waitForIdle()
    }
    return tabs.tabs.value.indexOfFirst { it.id == tabs.active.value }
  }

  @Test
  fun aSwipeTowardsTheStartMovesToTheNextItem() {
    assertEquals(2, landsOn { swipe(Offset(width - 20f, centerY), Offset(20f, centerY), 300) })
  }

  @Test
  fun aSwipeTowardsTheEndMovesToThePreviousItem() {
    assertEquals(0, landsOn { swipe(Offset(20f, centerY), Offset(width - 20f, centerY), 300) })
  }

  @Test
  fun aShortFlingRightAfterADragDownCloseMovesToTheNextItem() {
    val tabs = TabsController()
    tabs.open(Route.Grid(listOf("artist_x")), background = false)
    tabs.openLone(Route.Post("p1", "m1", listOf("artist_x"), "One"))
    val closing = tabs.activeTab
    tabs.openLone(Route.Post("p2", "m2", listOf("artist_x"), "Two"))
    val landing = tabs.activeTab
    tabs.openLone(Route.Post("p3", "m3", listOf("artist_x"), "Three"))
    tabs.activate(closing.id)
    ClosedTabUndo(tabs).dragClose(closing.id, noUpNext(tabs))
    assertEquals(landing, tabs.activeTab)
    val next = tabs.neighbour(forward = true)

    landsOn(tabs) { swipe(Offset(centerX + 80f, centerY), Offset(centerX - 80f, centerY), 100) }

    assertSame(next, tabs.activeTab)
  }

  @Test
  fun aSlowDragJustPastHalfTheWidthMoves() {
    assertEquals(2, landsOn {
      val half = width / 4 + viewConfiguration.touchSlop / 2
      swipe(Offset(centerX + half, centerY), Offset(centerX - half, centerY), 3000)
    })
  }

  @Test
  fun aSlowShortDragSpringsBack() {
    assertEquals(1, landsOn { swipe(Offset(centerX, centerY), Offset(centerX - 100f, centerY), 3000) })
  }

  @Test
  fun twentyDiagonalScrollsMoveNothing() {
    repeat(20) { i ->
      val sideways = (if (i % 2 == 0) 1f else -1f) * (120f + 4f * i)
      val down = if (i % 4 < 2) -160f - 2f * i else 160f + 2f * i
      assertEquals(1, landsOn { swipe(Offset(centerX, centerY), Offset(centerX + sideways, centerY + down), 250) }, "scroll $i")
    }
  }

  @Test
  fun aVerticalScrollMovesNothing() {
    assertEquals(1, landsOn { swipe(Offset(centerX, height - 20f), Offset(centerX, 20f), 300) })
  }

  @Test
  fun aSidewaysDragShortOfTwiceTheSlopMovesNothing() {
    assertEquals(1, landsOn { swipe(Offset(centerX, centerY), Offset(centerX - 1.5f * viewConfiguration.touchSlop, centerY), 200) })
  }

  @Test
  fun aSidewaysPinchMovesNothing() {
    assertEquals(1, landsOn {
      pinch(
        Offset(centerX - 10f, centerY), Offset(centerX - 150f, centerY),
        Offset(centerX + 10f, centerY), Offset(centerX + 150f, centerY),
        durationMillis = 300,
      )
    })
  }

  @Test
  fun aDoubleTapMovesNothing() {
    assertEquals(1, landsOn { doubleClick(Offset(centerX, centerY)) })
  }

  @Test
  fun withOneItemASwipeMovesNothing() {
    assertEquals(0, landsOn(count = 1) { swipe(Offset(width - 20f, centerY), Offset(20f, centerY), 300) })
  }

  @Test
  fun eachItemIsAPaneTitledByItsPlaceInTheQueue() {
    val tabs = TabsController()
    tabs.open(Route.Grid(listOf("artist_x")), background = false)
    val source = tabs.activeTab
    tabs.openLone(Route.Post("p1", "m1", listOf("artist_x"), "Original"))
    val kept = tabs.activeTab

    assertEquals("Queue item 2 of 3, source Artist X", queueItemDescription(2, 3, source, null))
    assertEquals("Queue item 3 of 3, kept post from Artist X", queueItemDescription(3, 3, kept, tabs.sourceOf(kept)))
    assertEquals("Queue item 3 of 3, kept post, Original", queueItemDescription(3, 3, kept, null))
    assertEquals("Kept · from Artist X", kindLabel(kept, source))
  }

  @Test
  fun aFeedIsNamedByTheCountedItemsBeforeItWhereverItStands() {
    val tabs = TabsController()
    tabs.pinFeeds(listOf(Feed.Following))
    tabs.open(Route.Grid(listOf("artist_x")), background = true)
    tabs.open(Route.Grid(listOf("artist_y")), background = true)
    val all = tabs.tabs.value
    assertEquals(listOf("Browse", "Following", "Artist X", "Artist Y"), all.map { it.title })
    val feed = all[1]

    val description = queueItemDescription(countedPosition(all, 1), countedItems(all), feed, null)

    assertEquals("Queue, feed Following, after 1 item", description)
    tabs.moveInQueue(feed.id, down = false)
    val moved = tabs.tabs.value
    assertEquals("Queue, feed Following, first", queueItemDescription(countedPosition(moved, 0), countedItems(moved), feed, null))
  }
}
