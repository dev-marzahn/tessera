package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.AccessibilitySettings
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.ui.Tips
import dev.marzahn.sankaku.ui.components.BottomRowLayout
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalTestApi::class)
class LeavingBlockOffersTest {
  private val tabs = TabsController()

  private val seen = SeenStore(InMemoryTextFile(), object : Clock {
    override fun now() = Clock.System.now() + 1.hours
  })
  private val undo = ClosedTabUndo(tabs)
  private val offer = BlockOffer()

  private fun post(id: String) = Route.Post(id, md5 = null, originTags = emptyList(), label = id, rating = "s")

  private fun keep(id: String, from: String) = tabs.openInBlock(listOf(Route.Grid(listOf(from), at = id), post(id)))

  private fun id(title: String) = tabs.tabs.value.single { it.title == title }.id

  private fun titles() = tabs.tabs.value.map { it.title }

  init {
    tabs.open(Route.Grid(listOf("landscape")), background = false)
    keep("l1", "landscape")
    keep("l2", "landscape")
    tabs.open(Route.Grid(listOf("cat")), background = false)
  }

  private fun ComposeUiTest.showShell(quiet: Boolean = false) {
    setContent {
      SankakuTheme {
        LeavingBlockOffers(offer, quiet = quiet, controller = tabs, seen = seen, undo = undo, blockFinds = null)
        Box(Modifier.size(412.dp, 300.dp)) {
          QueueBar(
            onOpenQueue = {},
            bottom = BottomRowLayout.roomAbove(navigationBar = 24.dp),
            controller = tabs,
            undo = undo,
            settings = AccessibilitySettings(InMemoryTextFile()),
            tips = Tips(InMemoryTextFile(), touch = false),
            upNext = noUpNext(tabs),
            blockFinds = null,
            blockOffer = offer,
            modifier = Modifier.align(Alignment.BottomCenter),
          )
        }
      }
    }
  }

  private fun ComposeUiTest.leaveLandscape() {
    tabs.activate(id("l2"))
    waitForIdle()
    tabs.activate(id("Cat"))
    waitForIdle()
  }

  @Test
  fun leavingASourcesBlockOffersToCloseItOverThePill() = runComposeUiTest {
    showShell()
    leaveLandscape()

    assertEquals(id("Landscape"), offer.pending)
    val chip = onNodeWithContentDescription("Close Landscape · 2 kept stay")
    chip.assertHasClickAction()
    val pill = onNodeWithTag(COUNT_PILL).getUnclippedBoundsInRoot()
    val line = chip.getUnclippedBoundsInRoot()
    assertTrue(line.bottom <= pill.top, "$line over $pill")
  }

  @Test
  fun closeClosesTheSourceAndItsKeptPostsStay() = runComposeUiTest {
    showShell()
    leaveLandscape()

    onNodeWithContentDescription("Close Landscape", substring = true).performClick()
    waitForIdle()

    assertEquals(listOf("Browse", "l1", "l2", "Cat"), titles())
    assertEquals(id("Cat"), tabs.active.value)
    assertNull(offer.pending)
    assertNotNull(undo.pending.value)
    onNodeWithContentDescription("Close Landscape", substring = true).assertDoesNotExist()
    runOnIdle { undo.undo() }
    assertEquals(listOf("Browse", "Landscape", "l1", "l2", "Cat"), titles())
  }

  @Test
  fun keptPostsNotGoneThroughOrLaterDontHoldItBack() = runComposeUiTest {
    showShell()
    tabs.activate(id("l2"))
    waitForIdle()
    runOnIdle { tabs.tabs.value.single { it.title == "l2" }.markLater() }
    tabs.activate(id("Cat"))
    waitForIdle()

    assertEquals(id("Landscape"), offer.pending)
  }

  @Test
  fun droppingTheBlocksLastKeptPostOffersItsSource() = runComposeUiTest {
    seen.record("l1", null)
    showShell()
    tabs.activate(id("l1"))
    waitForIdle()
    runOnIdle { undo.close(id("l1")) }
    waitForIdle()
    runOnIdle { undo.close(id("l2")) }
    waitForIdle()

    assertEquals(id("Cat"), tabs.active.value)
    assertEquals(id("Landscape"), offer.pending)
  }

  @Test
  fun theNextActionEndsTheOffer() = runComposeUiTest {
    showShell()
    leaveLandscape()
    tabs.open(Route.Grid(listOf("dog")), background = false)
    waitForIdle()

    assertNull(offer.pending)
    onNodeWithContentDescription("Close Landscape", substring = true).assertDoesNotExist()
  }

  @Test
  fun aSourceWithNoKeptPostsIsNotOffered() = runComposeUiTest {
    tabs.open(Route.Grid(listOf("dog")), background = true)
    showShell()
    tabs.activate(id("Dog"))
    waitForIdle()

    assertNull(offer.pending)
  }

  @Test
  fun aCloseOnTheQueuePageOffersNothing() = runComposeUiTest {
    showShell(quiet = true)
    leaveLandscape()

    assertNull(offer.pending)
  }
}
