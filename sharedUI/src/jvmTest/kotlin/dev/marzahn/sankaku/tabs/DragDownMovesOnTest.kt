package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.api.Feed
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Clock
import kotlin.time.Instant

class DragDownMovesOnTest {
  private val start = Instant.parse("2026-11-01T12:00:00Z")
  private val clock = object : Clock {
    override fun now() = start
  }
  private val tabs = TabsController(clock = clock)
  private val upNext = UpNextState(tabs, PrivacySettings(InMemoryTextFile()))
  private val undo = ClosedTabUndo(tabs)

  private fun grid(vararg tags: String) = Route.Grid(tags.toList())

  private fun post(id: String) = Route.Post(id, "m$id", listOf("artist_x"), label = "Post $id", rating = "s")

  private fun titles() = tabs.tabs.value.map { it.title }

  private fun tab(title: String) = tabs.tabs.value.single { it.title == title }

  private fun keptABC() {
    tabs.open(grid("artist_x"), background = false)
    listOf("A", "B", "C").forEach { tabs.openInBlock(listOf(post(it))) }
    assertEquals(listOf("Artist X", "Post A", "Post B", "Post C"), titles().takeLast(4))
  }

  private fun shellSeesTheItem() = upNext.itemShown(tabs.activeTab.id, tabs.activeTab.top)

  @Test
  fun aDragDownCloseOfBShowsCAndBIsInRecentlyClosedWithUndo() {
    keptABC()
    tabs.activate(tab("Post B").id)

    undo.dragClose(tabs.activeTab.id, upNext)

    assertEquals("Post C", tabs.activeTab.title)
    assertNull(upNext.card)
    assertEquals(listOf("Browse", "Artist X", "Post A", "Post C"), titles())
    val closed = tabs.closed.value.last()
    assertEquals(post("B"), closed.top)
    assertSame(closed, undo.pending.value?.closed)
  }

  @Test
  fun undoShowsBAgainInItsPlace() {
    keptABC()
    tabs.activate(tab("Post B").id)

    undo.dragClose(tabs.activeTab.id, upNext)
    undo.undo()

    assertEquals("Post B", tabs.activeTab.title)
    assertEquals(listOf("Browse", "Artist X", "Post A", "Post B", "Post C"), titles())
  }

  @Test
  fun closingTheLastItemShowsEndOfTheQueuePastTheOneBefore() {
    keptABC()
    tabs.activate(tab("Post C").id)

    undo.dragClose(tab("Post C").id, upNext)

    assertEquals("Post B", tabs.activeTab.title)
    assertEquals(UpNextCard.End, upNext.card)
    shellSeesTheItem()
    assertEquals(UpNextCard.End, upNext.card)
    undo.undo()
    shellSeesTheItem()
    assertEquals("Post C", tabs.activeTab.title)
    assertNull(upNext.card)
  }

  @Test
  fun closingAKeptPostWithFollowingAfterItShowsFollowing() {
    keptABC()
    tabs.pinFeeds(listOf(Feed.Following))
    tabs.activate(tab("Post C").id)

    undo.dragClose(tab("Post C").id, upNext)

    assertEquals("Following", tabs.activeTab.title)
    assertNull(upNext.card)
  }

  @Test
  fun aSearchRunInTheItemTheCardStandsPastStillTakesItAway() {
    keptABC()
    tabs.activate(tab("Post C").id)
    undo.dragClose(tab("Post C").id, upNext)
    shellSeesTheItem()

    upNext.itemShown(tabs.activeTab.id, grid("other"))

    assertNull(upNext.card)
  }

  @Test
  fun theOnlyItemClosedLeavesABrowseGridAndNoCard() {
    undo.dragClose(tabs.activeTab.id, upNext)

    assertEquals(listOf("Browse"), titles())
    assertNull(upNext.card)
  }
}
