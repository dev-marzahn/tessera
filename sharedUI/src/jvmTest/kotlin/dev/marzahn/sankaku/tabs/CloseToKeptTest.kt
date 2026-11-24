package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.features.grid.EndOfSource
import dev.marzahn.sankaku.ui.components.CLOSE_NEXT_CARD
import dev.marzahn.sankaku.ui.components.CLOSE_TO_KEPT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloseToKeptTest {
  private val tabs = TabsController()
  private val undo = ClosedTabUndo(tabs)
  private val deck = SortDeck(tabs, undo)

  private fun id(title: String) = tabs.tabs.value.single { it.title == title }.id

  private fun shown() = tabs.activeTab.title

  private fun goneIntoWithKept(): Pair<TabState, TabState> {
    val a = tabs.openSource("artist_a")
    val b = tabs.openSource("artist_b")
    deck.start()
    deck.goIn()
    tabs.keep("k1", "k2", "k3")
    return a to b
  }

  @Test
  fun fromSortCloseShowsTheFirstKeptPostAndCountsAsDone() {
    val (a, b) = goneIntoWithKept()

    deck.closeAndNext()

    assertFalse(tabs.tabs.value.any { it.id == a.id })
    assertEquals(listOf("k1", "k2", "k3"), tabs.tabs.value.filter { it.closedSource != null }.map { it.title })
    assertNotNull(undo.pending.value)
    assertEquals("k1", shown())
    assertFalse(deck.shown)
    assertTrue(deck.inKept)
    assertEquals(SortDeck.Tally(done = 1, doneWentIn = 1), deck.tally)
    deck.itemShown(tabs.active.value)
    assertFalse(deck.shown)

    tabs.next()
    tabs.next()
    deck.itemShown(tabs.active.value)
    assertEquals("k3", shown())
    assertFalse(deck.shown)

    tabs.next()
    deck.itemShown(tabs.active.value)
    assertTrue(deck.shown)
    assertFalse(deck.inKept)
    assertNull(deck.goneIn)
    assertEquals(b, deck.card)
    assertEquals(SortDeck.Tally(done = 1, doneWentIn = 1), deck.tally)
  }

  @Test
  fun goingBackBeforeTheKeptPostsIsNotPastThem() {
    goneIntoWithKept()
    deck.closeAndNext()

    tabs.previous()
    deck.itemShown(tabs.active.value)

    assertFalse(deck.shown)
    assertTrue(deck.inKept)
  }

  @Test
  fun thePillFromTheKeptPostsShowsTheNextCard() {
    val (_, b) = goneIntoWithKept()
    deck.closeAndNext()

    assertEquals(0, deck.position)
    assertEquals(2, deck.total)
    deck.backToDeck()

    assertTrue(deck.shown)
    assertEquals(b, deck.card)
    assertEquals(1, deck.tally.done)
  }

  @Test
  fun closingTheLastOfTheKeptPostsShowsTheNextCard() {
    val (_, b) = goneIntoWithKept()
    deck.closeAndNext()

    listOf("k1", "k2", "k3").forEach { tabs.close(id(it)) }
    deck.itemShown(tabs.active.value)

    assertTrue(deck.shown)
    assertEquals(b, deck.card)
  }

  @Test
  fun thePillsUndoWhileGoingThroughThemBringsTheSourceBackGoneIntoAndItsCardUnanswered() {
    goneIntoWithKept()
    deck.closeAndNext()

    undo.undo()
    deck.itemShown(tabs.active.value)

    val reopened = tabs.tabs.value.single { it.top == Route.Grid(listOf("artist_a")) }
    assertEquals(reopened.id, tabs.active.value)
    assertEquals(reopened.id, deck.goneIn)
    assertFalse(deck.inKept)
    assertFalse(deck.shown)
    assertEquals(0, deck.tally.sorted)
    assertEquals(3, tabs.keptPostsOf(reopened))
    deck.backToDeck()
    assertEquals(reopened, deck.card)
  }

  @Test
  fun followAndCloseFromTheGridGoesThroughTheKeptPostsAndCountsAsFollowed() {
    val (a, b) = goneIntoWithKept()
    var unfollowed = 0

    deck.followedInstead(a.id, { unfollowed++ })

    assertEquals("k1", shown())
    assertTrue(deck.inKept)
    assertEquals(SortDeck.Tally(followed = 1, followedWentIn = 1), deck.tally)
    repeat(3) { tabs.next() }
    deck.itemShown(tabs.active.value)
    assertEquals(b, deck.card)
    assertEquals(0, unfollowed)
  }

  @Test
  fun fromSortWithNothingKeptCloseShowsTheNextCardAsBefore() {
    tabs.openSource("artist_a")
    val b = tabs.openSource("artist_b")
    deck.start()
    deck.goIn()

    deck.closeAndNext()

    assertTrue(deck.shown)
    assertFalse(deck.inKept)
    assertEquals(b, deck.card)
  }

  @Test
  fun notFromSortCloseShowsTheFirstKeptPostAndMovingOnPastThemTheNextItem() {
    val a = tabs.openSource("artist_a")
    val next = tabs.openSource("cat")
    tabs.activate(a.id)
    tabs.keep("k1", "k2")

    undo.close(a.id)

    assertEquals("k1", shown())
    assertNotNull(undo.pending.value)
    tabs.next()
    tabs.next()
    assertEquals(next.id, tabs.active.value)
  }

  @Test
  fun theCloseSaysJustCloseWhenItGoesToKeptPosts() {
    assertEquals(CLOSE_TO_KEPT, EndOfSource("artist_x", keptStay = 3, onClose = {}, sortLater = {}).closeLabel)
    assertEquals(CLOSE_TO_KEPT, EndOfSource("artist_x", keptStay = 3, onClose = {}).closeLabel)
    assertEquals(CLOSE_NEXT_CARD, EndOfSource("artist_x", keptStay = 0, onClose = {}, sortLater = {}).closeLabel)
    assertEquals("Close artist_x", EndOfSource("artist_x", keptStay = 0, onClose = {}).closeLabel)
  }
}
