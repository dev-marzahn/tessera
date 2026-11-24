package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.ui.Message
import dev.marzahn.sankaku.ui.Messages
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ClearViewedTest {
  private var now = Instant.parse("2026-11-01T12:00:00Z")
  private val clock = object : Clock {
    override fun now() = now
  }
  private val seen = SeenStore(InMemoryTextFile(), clock)
  private val tabs = TabsController(clock = clock)
  private val messages = Messages()
  private val clearViewed = ClearViewed(tabs, seen, messages)

  private fun grid(tag: String) = Route.Grid(listOf(tag))

  private fun post(id: String) = Route.Post(id, "md5$id", listOf("a"), label = "Post $id")

  private fun titles() = tabs.tabs.value.map { it.title }

  private fun queue() {
    tabs.open(grid("a"), background = false)
    listOf("p1", "p2", "p3").forEach { tabs.openInBlock(listOf(grid("a"), post(it))) }
    tabs.open(grid("b"), background = true)
    tabs.open(Route.Grid(listOf("feed:following")), background = true)
  }

  private fun see(id: String) {
    now += 1.minutes
    seen.record(id, "md5$id")
  }

  @Test
  fun itClearsExactlyTheKeptPostsSeenSinceTheyWereKept() {
    queue()
    now += 1.minutes
    see("p1")
    see("p3")

    val closed = tabs.clearViewed(seen)!!

    assertEquals(2, closed.size)
    assertEquals(listOf("Browse", "A", "Post p2", "B", "Following"), titles())
  }

  @Test
  fun aPostSeenBeforeItWasKeptIsNotCleared() {
    queue()
    see("p1")
    now += 1.minutes
    tabs.keepAgain(tabs.tabs.value.single { it.top == post("p1") }.id)

    assertNull(tabs.clearViewed(seen))
    assertEquals(0, tabs.viewedKeptPosts(seen).size)
  }

  @Test
  fun theCurrentItemSeenIsClearedTooAndTheNextItemLeftComesForward() {
    queue()
    see("p1")
    see("p2")
    tabs.activate(tabs.tabs.value.single { it.top == post("p1") }.id)

    assertEquals(2, clearViewed.status().count)
    val closed = tabs.clearViewed(seen)!!

    assertEquals(2, closed.size)
    assertEquals(listOf("Browse", "A", "Post p3", "B", "Following"), titles())
    assertEquals("Post p3", tabs.activeTab.title)
  }

  @Test
  fun goingThroughKeptTheClearedCurrentPostGivesWayToTheRunsNextPost() {
    queue()
    val (p1, p3) = listOf("p1", "p3").map { id -> tabs.tabs.value.single { it.top == post(id) }.id }
    tabs.startKeptRun(listOf(p1, p3), at = p1)
    see("p1")

    tabs.clearViewed(seen)!!

    assertEquals(p3, tabs.active.value)
    assertEquals(1 to 1, tabs.keptPlace())
  }

  @Test
  fun goingThroughKeptTheClearedLastPostOfTheRunEndsItAndMovesOnInTheQueue() {
    queue()
    val p1 = tabs.tabs.value.single { it.top == post("p1") }.id
    tabs.startKeptRun(listOf(p1), at = p1)
    see("p1")

    tabs.clearViewed(seen)!!

    assertEquals("Post p2", tabs.activeTab.title)
    assertNull(tabs.keptPlace())
    assertNull(tabs.keptRunKind())
  }

  @Test
  fun theCurrentItemLastInTheQueueGivesWayToTheItemBefore() {
    tabs.open(grid("a"), background = false)
    listOf("p1", "p2").forEach { tabs.openInBlock(listOf(grid("a"), post(it))) }
    see("p1")
    see("p2")
    tabs.activate(tabs.tabs.value.single { it.top == post("p2") }.id)

    tabs.clearViewed(seen)!!

    assertEquals(listOf("Browse", "A"), titles())
    assertEquals("A", tabs.activeTab.title)
  }

  @Test
  fun clearingEveryItemLeavesAFreshBrowseGrid() {
    tabs.open(grid("a"), background = false)
    tabs.openInBlock(listOf(grid("a"), post("p1")))
    tabs.tabs.value.filter { !it.kind.isKept }.forEach { tabs.close(it.id) }
    see("p1")
    tabs.activate(tabs.tabs.value.single { it.top == post("p1") }.id)

    tabs.clearViewed(seen)!!

    assertEquals(listOf("Browse"), titles())
    assertEquals("Browse", tabs.activeTab.title)
  }

  @Test
  fun undoBringsTheClearedCurrentItemBackAsTheCurrentItem() {
    queue()
    see("p1")
    see("p2")
    val p1 = tabs.tabs.value.single { it.top == post("p1") }.id
    tabs.activate(p1)
    val before = titles()

    val closed = tabs.clearViewed(seen)!!
    tabs.reopen(closed)

    assertEquals(before, titles())
    assertEquals(post("p1"), tabs.activeTab.top)
  }

  @Test
  fun itIsOneEntryInRecentlyClosedAndUndoRestoresThemAtTheirPlaces() {
    queue()
    see("p1")
    see("p3")
    val before = tabs.tabs.value.map { it.id to it.title }
    val active = tabs.active.value

    val closed = tabs.clearViewed(seen)!!

    assertEquals(listOf(closed), tabs.closed.value)
    assertTrue(closed.cleared)
    assertEquals("2 posts", closed.title)
    tabs.reopen(closed)

    assertEquals(before.map { it.second }, titles())
    assertEquals(active, tabs.active.value)
    assertTrue(tabs.closed.value.isEmpty())
    val head = tabs.tabs.value.single { it.title == "A" }
    assertEquals(listOf("Post p1", "Post p2", "Post p3"), tabs.tabs.value.filter { it.block == head.id }.map { it.title })
    assertEquals(2, tabs.viewedKeptPosts(seen).size)
  }

  @Test
  fun aSinglePostIsAlsoOneClearedEntryAndUndoDoesNotActivateIt() {
    queue()
    see("p2")
    val active = tabs.active.value

    val closed = tabs.clearViewed(seen)!!

    assertEquals("1 post", closed.title)
    tabs.reopen(closed)
    assertEquals(active, tabs.active.value)
  }

  @Test
  fun clearingEndsTheSessionSoLaterGoesAfterOnlyWhatWasAddedSince() {
    queue()
    see("p1")
    val first = tabs.sessions.current

    tabs.clearViewed(seen)

    assertNotEquals(first, tabs.sessions.current)
    tabs.open(grid("fresh"), background = true)
    tabs.open(grid("fresher"), background = true)
    assertEquals(listOf("Fresh", "Fresher"), titles().takeLast(2))
  }

  @Test
  fun nothingToClearChangesNothingAndKeepsTheSession() {
    queue()
    val session = tabs.sessions.current
    val before = titles()

    assertNull(tabs.clearViewed(seen))
    assertEquals(0, clearViewed())

    assertEquals(session, tabs.sessions.current)
    assertEquals(before, titles())
    assertTrue(tabs.closed.value.isEmpty())
  }

  @Test
  fun relaunchingDoesNotStartANewSessionButClearViewedDoes() {
    queue()
    see("p1")
    val session = tabs.sessions.current

    val relaunched = TabsController(RecordingTabStateStore(tabs.saved()), clock = clock)
    assertEquals(session, relaunched.sessions.current)
    relaunched.open(grid("fresh"), background = true)
    assertEquals("Fresh", relaunched.tabs.value.last { it.addedIn == session }.title)

    tabs.clearViewed(seen)
    val afterClear = TabsController(RecordingTabStateStore(tabs.saved()), clock = clock)
    assertNotEquals(session, tabs.sessions.current)
    assertEquals(tabs.sessions.current, afterClear.sessions.current)
  }

  @Test
  fun aFileWithoutASessionStartsOne() {
    val controller = TabsController(RecordingTabStateStore(SavedTabs(0, listOf(SavedTab("old", listOf(grid("old")))))), clock = clock)

    assertEquals(controller.sessions.current.value, controller.saved().session)
    assertNotEquals("", controller.sessions.current.value)
  }

  @Test
  fun everyKeptPostSeenSuggestsClearingAndAnUnseenOneDoesNot() {
    queue()
    assertFalse(clearViewed.status().suggested)
    see("p1")
    see("p2")
    assertFalse(clearViewed.status().suggested)
    assertEquals(2, clearViewed.status().count)

    see("p3")

    assertTrue(clearViewed.status().suggested)
    assertEquals(3, clearViewed.status().count)
  }

  @Test
  fun noKeptPostsSuggestsNothing() {
    assertFalse(clearViewed.status().suggested)
    assertEquals(0, clearViewed.status().count)
  }

  @Test
  fun theCountIncludesTheCurrentItemWhenItIsSeen() {
    queue()
    see("p1")
    see("p2")
    see("p3")
    tabs.activate(tabs.tabs.value.single { it.top == post("p3") }.id)

    val status = clearViewed.status()

    assertEquals(3, status.count)
    assertTrue(status.suggested)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun theTopLineSaysClearedWithAShortUndoThatRestores() = runTest(UnconfinedTestDispatcher()) {
    queue()
    see("p1")
    see("p2")
    val shown = mutableListOf<Message>()
    backgroundScope.launch { messages.messages.collect { shown += it } }

    assertEquals(2, clearViewed())

    val message = shown.single()
    assertEquals("Cleared 2", message.text)
    assertEquals("Undo", message.actionLabel)
    assertTrue(message.short)
    assertFalse(tabs.tabs.value.any { it.top == post("p1") })

    message.onAction()

    assertTrue(tabs.tabs.value.any { it.top == post("p1") })
    assertTrue(tabs.tabs.value.any { it.top == post("p2") })
  }

  @Test
  fun theAccessibleNameCountsTheKeptPosts() {
    assertEquals("Clear viewed, 118 kept posts", clearViewedLabel(118))
    assertEquals("Clear viewed, 1 kept post", clearViewedLabel(1, touch = true))
    assertEquals("Clear viewed, 1 kept posts", clearViewedLabel(1, touch = false))
  }
}
