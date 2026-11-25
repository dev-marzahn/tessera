package dev.marzahn.sankaku.tabs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class QueueDragTest {
  private val tabs = TabsController()

  private fun titles() = tabs.tabs.value.map { it.title }

  private val privacy = PrivacySettings(InMemoryTextFile())

  private fun ComposeUiTest.show() {
    val seen = SeenStore(InMemoryTextFile())
    setContent {
      SankakuTheme {
        PhoneQueuePage(onDismiss = {}, controller = tabs, posts = TabPosts.None, privacy = privacy, seen = seen, interacted = { false }, onInteract = {}, folding = false)
      }
    }
    waitForIdle()
  }

  private fun source(tag: String) = tabs.open(Route.Grid(listOf(tag)), background = true)

  private fun ComposeUiTest.handleOf(title: String): SemanticsNodeInteraction {
    val y = centreY(title)
    val handles = onAllNodesWithContentDescription("Move", useUnmergedTree = true)
    val nodes = handles.fetchSemanticsNodes()
    return handles[nodes.indices.minBy { abs(nodes[it].boundsInRoot.center.y - y) }]
  }

  private fun ComposeUiTest.drag(handle: SemanticsNodeInteraction, dy: Float, holdMillis: Long = 0) {
    val start = handle.fetchSemanticsNode().boundsInRoot.center
    mainClock.autoAdvance = false
    onRoot().performTouchInput { down(start) }
    repeat(40) {
      mainClock.advanceTimeBy(16)
      onRoot().performTouchInput { moveBy(Offset(0f, dy / 40)) }
    }
    mainClock.advanceTimeBy(holdMillis + 16)
    onRoot().performTouchInput { up() }
    mainClock.autoAdvance = true
    waitForIdle()
  }

  private fun ComposeUiTest.centreY(title: String) = onNodeWithText(title).fetchSemanticsNode().boundsInRoot.center.y

  @Test
  fun aRowDraggedUpPastRowsOfOtherHeightsLandsOnTop() = runComposeUiTest {
    source("a")
    tabs.activate(tabs.tabs.value[1].id)
    tabs.openInBlock(listOf(Route.Grid(listOf("a"), at = "p1"), Route.Post("p1", null, listOf("a"), label = "p1")))
    tabs.openInBlock(listOf(Route.Grid(listOf("a"), at = "p2"), Route.Post("p2", null, listOf("a"), label = "p2")))
    source("b")
    source("c")
    show()
    assertEquals(listOf("Browse", "A", "p1", "p2", "B", "C"), titles())

    drag(handleOf("C"), centreY("Browse") - centreY("C") - 20f)

    assertEquals(listOf("C", "Browse", "A", "p1", "p2", "B"), titles())
  }

  @Test
  fun aRowDraggedPartWayLandsAmongTheRowsItWasLetGoOver() = runComposeUiTest {
    for (tag in listOf("a", "b", "c", "d")) source(tag)
    show()

    drag(handleOf("Browse"), centreY("C") - centreY("Browse") + 10f)

    assertEquals(listOf("A", "B", "C", "Browse", "D"), titles())
  }

  @Test
  fun aRowDraggedAcrossTheInlineUndoRowLandsWhereItWasLetGo() = runComposeUiTest {
    for (tag in listOf("a", "b", "c")) source(tag)
    show()
    onNodeWithText("B").performTouchInput { swipeLeft() }
    waitForIdle()
    onNodeWithText("Closed “B”").assertExists()
    assertEquals(listOf("Browse", "A", "C"), titles())

    drag(handleOf("C"), centreY("Browse") - centreY("C") - 20f)

    assertEquals(listOf("C", "Browse", "A"), titles())
  }

  @Test
  fun aRowHeldAtTheListsBottomScrollsTheListAndKeepsGoing() = runComposeUiTest {
    repeat(40) { source("s$it") }
    show()
    val list = onNodeWithTag(QUEUE_PAGE_LIST_TAG).getBoundsInRoot()
    val handle = handleOf("Browse")
    val from = handle.fetchSemanticsNode().boundsInRoot.center.y

    drag(handle, list.bottom.value - 4f - from, holdMillis = 3_000)

    val at = titles().indexOf("Browse")
    assertTrue(at > 15, "Browse at $at: ${titles()}")
    val row = onNodeWithText("Browse").fetchSemanticsNode().boundsInRoot
    assertTrue(row.bottom <= list.bottom.value + 1f && row.top > list.top.value, "$row in $list")
  }

  private fun keep(id: String, from: String) =
    tabs.openInBlock(listOf(Route.Grid(listOf(from), at = id), Route.Post(id, null, listOf(from), label = id)))

  @Test
  fun aKeptPostDraggedPastItsBlocksEndLeavesItAndPassesTheNextBlock() = runComposeUiTest {
    source("a")
    tabs.activate(tabs.tabs.value[1].id)
    keep("p1", "a")
    keep("p2", "a")
    source("b")
    show()
    assertEquals(listOf("Browse", "A", "p1", "p2", "B"), titles())

    drag(handleOf("p2"), centreY("B") - centreY("p2") + 30f)

    assertEquals(listOf("Browse", "A", "p1", "B", "p2"), titles())
  }

  @Test
  fun underGOnlyARowPassesWhatsHiddenWithoutAStepOfItsOwn() = runComposeUiTest {
    source("a")
    source("rating:q")
    source("b")
    privacy.setGOnly(true)
    show()
    val hidden = tabs.tabs.value[2].title

    drag(handleOf("A"), centreY("B") - centreY("A") + 10f)

    assertEquals(listOf("Browse", hidden, "B", "A"), titles())
  }

  @Test
  fun aRowHeldAtTheTopPassesABlockTallerThanTheList() = runComposeUiTest {
    source("a")
    tabs.activate(tabs.tabs.value[1].id)
    repeat(15) { keep("p$it", "a") }
    source("b")
    tabs.activate(tabs.tabs.value.last().id)
    show()
    val list = onNodeWithTag(QUEUE_PAGE_LIST_TAG).getBoundsInRoot()
    val handle = handleOf("B")
    val from = handle.fetchSemanticsNode().boundsInRoot.center.y

    drag(handle, list.top.value + 4f - from, holdMillis = 6_000)

    assertTrue(titles().indexOf("B") < titles().indexOf("A"), "${titles()}")
  }

  @Test
  fun aRowDraggedPastTheInlineUndoRowKeepsGoingToTheBottom() = runComposeUiTest {
    repeat(40) { source("s$it") }
    show()
    onNodeWithText("S1").performTouchInput { swipeLeft() }
    waitForIdle()
    onNodeWithText("Closed “S1”").assertExists()
    val list = onNodeWithTag(QUEUE_PAGE_LIST_TAG).getBoundsInRoot()
    val handle = handleOf("S0")
    val from = handle.fetchSemanticsNode().boundsInRoot.center.y

    drag(handle, list.bottom.value - 4f - from, holdMillis = 3_000)

    val at = titles().indexOf("S0")
    assertTrue(at > 15, "S0 at $at: ${titles()}")
  }

  @Test
  fun aRowHeldAtTheListsTopScrollsItBackUp() = runComposeUiTest {
    repeat(40) { source("s$it") }
    tabs.activate(tabs.tabs.value.last().id)
    show()
    val handle = handleOf("S39")
    val list = onNodeWithTag(QUEUE_PAGE_LIST_TAG).getBoundsInRoot()
    val from = handle.fetchSemanticsNode().boundsInRoot.center.y

    drag(handle, list.top.value + 4f - from, holdMillis = 6_000)

    assertEquals("S39", titles().first(), "${titles()}")
  }
}
