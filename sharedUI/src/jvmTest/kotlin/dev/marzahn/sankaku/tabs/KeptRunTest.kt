package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KeptRunTest {
  private val tabs = TabsController()
  private val navigator = TabsNavigator(tabs)
  private val upNext = noUpNext(tabs)

  private fun shown() = (tabs.activeTab.top as? Route.Post)?.id

  private fun run(): List<TabState> {
    val a = tabs.openSource("artist_a")
    val b = tabs.openSource("artist_b")
    val p1 = tabs.keepInto(a, "p1")
    val p2 = tabs.keepInto(a, "p2")
    val p3 = tabs.keepInto(b, "p3")
    val order = listOf(p3, p1, p2)
    tabs.startKeptRun(order.map { it.id }, p3.id)
    return order
  }

  @Test
  fun movingOnGoesThroughTheKeptPostsOnlyAndNothingPastTheLast() {
    val before = tabs.tabs.value.map { it.id }
    run()
    assertEquals("p3", shown())
    assertEquals(1 to 3, tabs.keptPlace())

    assertTrue(tabs.next())
    assertEquals("p1", shown())
    assertTrue(tabs.next())
    assertEquals("p2", shown())
    assertEquals(3 to 3, tabs.keptPlace())
    assertNull(tabs.neighbour(forward = true))
    assertIs<Ahead.End>(upNext.ahead())
    assertFalse(upNext.forward())
    assertEquals(UpNextCard.KeptEnd, upNext.card)

    assertTrue(tabs.previous())
    assertEquals("p1", shown())
    assertEquals(before.size + 5, tabs.tabs.value.size)
  }

  @Test
  fun aCloseLandsOnTheNextKeptPost() {
    val (p3, p1, _) = run()

    tabs.close(p3.id)

    assertSame(p1, tabs.activeTab)
    assertEquals(1 to 2, tabs.keptPlace())
  }

  @Test
  fun aSearchOpenedFromAKeptPostIsntInTheRunAndBackReturnsIntoIt() {
    run()
    tabs.next()
    val p1 = tabs.activeTab

    navigator.goTo(Route.Grid(listOf("tag_t")))
    assertNull(tabs.keptPlace())

    navigator.goBack()
    assertSame(p1, tabs.activeTab)
    assertEquals(2 to 3, tabs.keptPlace())
    assertTrue(tabs.next())
    assertEquals("p2", shown())
  }

  @Test
  fun endingItMovesOnThroughTheQueueAgain() {
    run()
    tabs.endKeptRun()
    assertNull(tabs.keptPlace())
    assertFalse(tabs.neighbour(forward = true)?.top == keptPost("p1"))
  }

  @Test
  fun undoOfACloseBringsThePostBackIntoTheRun() {
    val (p3, _, _) = run()
    tabs.close(p3.id)

    tabs.reopenClosed()

    assertEquals("p3", shown())
    assertEquals(1 to 3, tabs.keptPlace())
  }

  @Test
  fun anotherItemShownPlainlyLeavesTheRunForGood() {
    val (p3, p1, _) = run()
    val b = tabs.tabs.value.first { it.top == Route.Grid(listOf("artist_b")) }

    tabs.activate(b.id)
    tabs.activate(p1.id)

    assertNull(tabs.keptPlace())
    assertFalse(tabs.neighbour(forward = false) === p3)
  }

  @Test
  fun closingTheLastPostLeftEndsTheRun() {
    val (p3, p1, p2) = run()
    tabs.close(p3.id)
    tabs.close(p1.id)

    tabs.close(p2.id)

    assertNull(tabs.keptPlace())
  }

  @Test
  fun backOnAKeptPostGoneThroughGoesToTheQueuePage() {
    assertEquals(QueueBack.QueuePage, queueBack(cardUp = false, lonePost = true, previous = true, keptRun = true))
    assertEquals(QueueBack.Previous, queueBack(cardUp = true, lonePost = true, previous = true, keptRun = true))
  }

  @Test
  fun thePillCountsTheRunsDecisions() {
    val text = runPillText(KeptRunKind.Kept, KeptProgress(decided = 24, total = 48, later = 5))
    assertEquals(KEPT_PILL, text.source)
    assertEquals("24 of 48", text.figures)
    assertEquals("5 later", text.later)
    assertEquals(0.5f, text.progress)
    val later = runPillText(KeptRunKind.Later, KeptProgress(decided = 3, total = 8, later = 6))
    assertEquals(LATER_PILL, later.source)
    assertNull(later.later)
  }
}
