package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.ui.Message
import dev.marzahn.sankaku.ui.Messages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalTestApi::class)
class LeavingBlockMessagesTest {
  private val tabs = TabsController()

  private val seen = SeenStore(InMemoryTextFile(), object : Clock {
    override fun now() = Clock.System.now() + 1.hours
  })
  private val messages = Messages()
  private val offer = BlockOffer()

  private fun post(id: String) = Route.Post(id, md5 = null, originTags = emptyList(), label = id, rating = "s")

  private fun keep(id: String, from: String) = tabs.openInBlock(listOf(Route.Grid(listOf(from), at = id), post(id)))

  private fun id(title: String) = tabs.tabs.value.single { it.title == title }.id

  init {
    tabs.open(Route.Grid(listOf("landscape")), background = false)
    keep("l1", "landscape")
    keep("l2", "landscape")
    tabs.open(Route.Grid(listOf("cat")), background = false)
    keep("c1", "cat")
    seen.record("l1", null)
    seen.record("l2", null)
  }

  private fun androidx.compose.ui.test.ComposeUiTest.leaveLandscape() {
    setContent { LeavingBlockMessages(offer, tabs, seen, ClearViewed(tabs, seen, ClearedUndoOnTopLine(ClosedTabUndo(tabs), Messages())), messages) }
    tabs.activate(id("l2"))
    waitForIdle()
    tabs.activate(id("Cat"))
    waitForIdle()
    assertEquals(id("Landscape"), offer.pending)
  }

  @Test
  fun clearViewedTakingTheShownPostOutOfItsBlockOffersNothing() = runComposeUiTest {
    setContent { LeavingBlockMessages(offer, tabs, seen, ClearViewed(tabs, seen, ClearedUndoOnTopLine(ClosedTabUndo(tabs), messages)), messages) }
    tabs.activate(id("l2"))
    waitForIdle()

    ClearViewed(tabs, seen, ClearedUndoOnTopLine(ClosedTabUndo(tabs), messages))()
    waitForIdle()

    assertEquals(id("Cat"), tabs.active.value)
    assertNull(offer.pending)
    assertEquals(emptySet(), offer.offered)
  }

  @Test
  fun swipingPastTheLastKeptPostOffersToClearItWithTheSeenOnes() = runComposeUiTest {
    tabs.open(Route.Grid(listOf("dog")), background = false)
    val shown = mutableListOf<Message>()
    setContent {
      LaunchedEffect(messages) { messages.messages.collect { shown += it } }
      LeavingBlockMessages(offer, tabs, seen, ClearViewed(tabs, seen, ClearedUndoOnTopLine(ClosedTabUndo(tabs), Messages())), messages)
    }
    tabs.activate(id("c1"))
    waitForIdle()

    tabs.next()
    waitForIdle()

    assertEquals(id("Dog"), tabs.active.value)
    assertEquals(true, tabs.tabs.value.single { it.title == "c1" }.later)
    assertEquals(listOf("Queue done · 1 for later" to "Clear viewed · 3"), shown.map { it.text to it.actionLabel })
  }

  @Test
  fun seeingTheLastKeptPostSaysNothingKeptIsLeft() = runComposeUiTest {
    tabs.open(Route.Grid(listOf("dog")), background = false)
    val shown = mutableListOf<Message>()
    setContent {
      LaunchedEffect(messages) { messages.messages.collect { shown += it } }
      LeavingBlockMessages(offer, tabs, seen, ClearViewed(tabs, seen, ClearedUndoOnTopLine(ClosedTabUndo(tabs), Messages())), messages)
    }
    tabs.activate(id("c1"))
    waitForIdle()

    seen.record("c1", null)
    tabs.activate(id("Dog"))
    waitForIdle()

    assertEquals(listOf("Queue done · nothing kept is left" to "Clear viewed · 3"), shown.map { it.text to it.actionLabel })
  }

  @Test
  fun anotherMessageEndsTheOffer() = runComposeUiTest {
    leaveLandscape()
    messages.show(Message("Cleared 2", actionLabel = "Undo"))
    waitForIdle()
    assertNull(offer.pending)
  }

  @Test
  fun goingBackIntoTheBlockEndsTheOffer() = runComposeUiTest {
    leaveLandscape()
    tabs.activate(id("Landscape"))
    waitForIdle()
    assertNull(offer.pending)
  }

  @Test
  fun takingTheOfferClosesTheBlock() = runComposeUiTest {
    leaveLandscape()
    offer.take(tabs, messages)
    assertEquals(listOf("Browse", "Cat", "c1"), tabs.tabs.value.map { it.title })
  }
}
