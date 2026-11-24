package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.queue.SeenStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class LaterTest {
  private var now = Instant.parse("2026-11-15T12:00:00Z")
  private val clock = object : Clock {
    override fun now() = now
  }
  private val store = Store()
  private val tabs = TabsController(store, clock = clock)
  private val upNext = noUpNext(tabs)
  private val undo = ClosedTabUndo(tabs)
  private val seen = SeenStore(InMemoryTextFile(), clock)

  private class Store(var saved: SavedTabs? = null) : TabStateStore {
    override fun load() = saved

    override fun save(tabs: SavedTabs) {
      saved = tabs
    }

    override fun clear() {
      saved = null
    }
  }

  private fun source(tag: String): TabState {
    val route = Route.Grid(listOf(tag))
    tabs.open(route, background = true)
    return tabs.tabs.value.last { it.top == route }
  }

  private fun keepInto(into: TabState, id: String): TabState {
    tabs.activate(into.id)
    tabs.openInBlock(listOf(Route.Post(id, md5 = "m$id", originTags = emptyList(), label = "Post $id", rating = "s")))
    return tabs.tabs.value.first { (it.top as? Route.Post)?.id == id }
  }

  private fun shown() = (tabs.activeTab.top as? Route.Post)?.id

  private fun tab(id: String) = tabs.tabs.value.first { (it.top as? Route.Post)?.id == id }

  private fun index(id: String) = tabs.tabs.value.indexOfFirst { (it.top as? Route.Post)?.id == id }

  private fun block(): List<TabState> {
    val a = source("artist_a")
    val kept = listOf("p1", "p2", "p3", "p4").map { keepInto(a, it) }
    val b = source("artist_b")
    keepInto(b, "p5")
    return kept
  }

  @Test
  fun aSwipeOnFromAKeptPostFilesItUnderLaterNotSeen() {
    val (p1, p2) = block()
    tabs.activate(p1.id)

    assertTrue(tabs.next())

    assertTrue(p1.later)
    assertFalse(p1.seenSinceKept(seen), "Later isn't seen: nothing was recorded")
    assertFalse(p2.later, "the post landed on isn't decided yet")
    tabs.previous()
    assertFalse(p2.later)
  }

  @Test
  fun laterSurvivesARelaunch() {
    val (p1) = block()
    tabs.activate(p1.id)
    tabs.next()

    val saved = tabs.saved()
    assertEquals(true, saved.tabs.first { (it.routes.last() as? Route.Post)?.id == "p1" }.later)
    assertNull(saved.tabs.first { (it.routes.last() as? Route.Post)?.id == "p2" }.later)
    val relaunched = TabsController(Store(saved), clock = clock)

    assertTrue(relaunched.tabs.value.first { (it.top as? Route.Post)?.id == "p1" }.later)
  }

  @Test
  fun markUnseenAndKeepingAgainEndLater() {
    val (p1) = block()
    tabs.activate(p1.id)
    tabs.next()

    tabs.keepAgain(p1.id)

    assertFalse(p1.later)
  }

  @Test
  fun theBlocksPillCountsDropsAndSwipesAndItsBarOnlyGrows() {
    val (p1, p2, p3) = block()
    tabs.activate(p1.id)
    fun decisions() = tabs.blockDecisions(tabs.tabs.value, tabs.tabs.value.indexOf(tabs.activeTab))!!
    assertEquals(KeptProgress(decided = 0, total = 4, later = 0), decisions())
    val bars = mutableListOf(decisions().fraction)

    tabs.next()
    assertEquals(KeptProgress(decided = 1, total = 4, later = 1), decisions())
    bars += decisions().fraction
    undo.dragClose(p2.id, upNext)
    assertEquals(KeptProgress(decided = 2, total = 4, later = 1), decisions())
    bars += decisions().fraction
    assertEquals(p3.id, tabs.activeTab.id)
    undo.dragClose(p1.id, upNext)
    assertEquals(KeptProgress(decided = 2, total = 4, later = 0), tabs.blockDecisions(tabs.tabs.value, index("p3")))

    assertEquals(bars, bars.sorted(), "the bar never shrinks as decisions are made")
  }

  @Test
  fun theBlocksPillSaysItsDecisions() {
    val place = BlockPlace.Kept("Landscape", position = 30, count = 31, kind = dev.marzahn.sankaku.queue.QueueKind.KeptPost)
    val text = pillText(place, 5, 11, feed = null, card = null, decisions = KeptProgress(24, 48, 5))
    assertEquals("Landscape", text.source)
    assertEquals("24 of 48", text.figures)
    assertEquals("5 later", text.later)
    assertEquals(0.5f, text.progress)
    assertTrue(text.spoken.contains("24 of 48 kept posts gone through, 5 later"))
  }

  @Test
  fun undoOfADropTakesTheDecisionBack() {
    val (p1, p2) = block()
    tabs.activate(p2.id)
    undo.dragClose(p2.id, upNext)
    undo.undo()

    assertEquals(KeptProgress(decided = 0, total = 4, later = 0), tabs.blockDecisions(tabs.tabs.value, index("p1")))
    assertEquals(p1.block, tab("p2").block)
  }

  @Test
  fun clearViewedTakesLaterPostsAndItsUndoBringsThemBackLater() {
    val (p1, p2, _, p4) = block()
    tabs.activate(p1.id)
    tabs.next()
    tabs.next()
    now += 1.minutes
    seen.record("p4", "mp4")
    tabs.activate(p4.id)
    val other = tabs.tabs.value.first { it.top == Route.Grid(listOf("artist_b")) }
    tabs.activate(other.id)

    assertEquals(listOf(p1.id, p2.id, p4.id), tabs.viewedKeptPosts(seen))
    val cleared = assertNotNull(tabs.clearViewed(seen))
    assertEquals(3, cleared.size)
    assertTrue(tabs.tabs.value.none { (it.top as? Route.Post)?.id in setOf("p1", "p2", "p4") })

    tabs.reopen(cleared)

    assertTrue(tab("p1").later)
    assertTrue(tab("p2").later)
    assertFalse(tab("p4").later)
  }

  @Test
  fun droppingAFavouritedPostLeavesTheQueueAtOnceIntoRecentlyClosed() {
    val (p1) = block()
    tabs.activate(p1.id)

    undo.dragClose(p1.id, upNext)

    assertTrue(tabs.tabs.value.none { (it.top as? Route.Post)?.id == "p1" })
    assertEquals("p1", (tabs.closed.value.last().top as? Route.Post)?.id)
  }

  @Test
  fun goingThroughKeptEndsOnItsTallyAndGoThroughLaterVisitsOnlyTheLaterPostsInQueueOrder() {
    val (p1, p2, p3, p4) = block()
    val p5 = tab("p5")
    tabs.startKeptRun(listOf(p1, p2, p3, p4, p5).map { it.id }, p1.id)
    assertEquals(KeptProgress(0, 5, 0), tabs.runProgress())

    upNext.forward()
    undo.dragClose(p2.id, upNext)
    assertEquals("p3", shown())
    upNext.forward()
    upNext.forward()
    assertEquals(KeptProgress(4, 5, 3), tabs.runProgress())
    assertEquals("p5", shown())
    upNext.forward()
    assertEquals(UpNextCard.KeptEnd, upNext.card)
    val tally = assertNotNull(tabs.keptTally())
    assertEquals(KeptRunKind.Kept, tally.kind)
    assertEquals(5, tally.goneThrough)
    assertEquals(listOf<String?>("p2"), tally.dropped)
    assertEquals(4, tally.later)

    val later = laterPosts(tabs.tabs.value, gOnly = false) { null }
    assertEquals(listOf("p1", "p3", "p4", "p5"), later.map { (it.top as Route.Post).id })
    upNext.goThroughLater(later.map { it.id })
    assertNull(upNext.card)
    assertEquals("p1", shown())
    assertEquals(KeptRunKind.Later, tabs.keptRunKind())
    assertEquals(KeptProgress(0, 4, 4), tabs.runProgress(), "a later pass starts with nothing decided")

    upNext.forward()
    assertTrue(tab("p1").later)
    undo.dragClose(tab("p3").id, upNext)
    assertEquals("p4", shown())
    assertEquals(KeptProgress(2, 4, 3), tabs.runProgress())
  }

  @Test
  fun droppingTheLastPostLeftStillShowsTheTally() {
    block()
    val later = listOf("p1", "p2").map(::tab)
    later.forEach { it.markLater() }
    tabs.startLaterRun(later.map { it.id })

    undo.dragClose(tab("p1").id, upNext)
    assertEquals("p2", shown())
    undo.dragClose(tab("p2").id, upNext)

    assertEquals(UpNextCard.KeptEnd, upNext.card)
    val tally = assertNotNull(tabs.keptTally())
    assertEquals(KeptRunKind.Later, tally.kind)
    assertEquals(2, tally.goneThrough)
    assertEquals(0, tally.later)
    upNext.dismiss()
    assertNull(tabs.keptTally())
  }

  @Test
  fun aRunOverFromAnotherCloseShowsNoStaleTallyOnTheNextDrop() {
    block()
    val later = listOf("p1").map(::tab)
    later.forEach { it.markLater() }
    tabs.startLaterRun(later.map { it.id })

    tabs.close(tab("p1").id)
    assertNull(upNext.card)

    undo.dragClose(tabs.activeTab.id, upNext)
    assertTrue(upNext.card != UpNextCard.KeptEnd)
    assertNull(tabs.keptTally())
  }

  @Test
  fun anotherCloseUnderTheEndCardKeepsItsTally() {
    block()
    val p1 = tab("p1")
    p1.markLater()
    tabs.startLaterRun(listOf(p1.id))
    undo.dragClose(p1.id, upNext)
    assertEquals(UpNextCard.KeptEnd, upNext.card)

    tabs.close(tab("p4").id)

    assertEquals(UpNextCard.KeptEnd, upNext.card)
    assertEquals(KeptRunKind.Later, tabs.keptTally()?.kind)
    assertEquals(1, tabs.keptTally()?.goneThrough)
  }

  @Test
  fun theEndCardSaysWhereTheLaterPostsStay() {
    assertEquals("The 8 stay in their blocks, across a relaunch, until you drop them or press Clear viewed.", keptEndDetail(8))
    assertEquals("It stays in its block, across a relaunch, until you drop it or press Clear viewed.", keptEndDetail(1))
    assertEquals("", keptEndDetail(0))
  }

  @Test
  fun goThroughKeptCountsThePostsAlreadyLaterAsDecided() {
    val (p1, p2) = block()
    p1.markLater()

    tabs.startKeptRun(listOf(p1.id, p2.id), p2.id)

    assertEquals(KeptProgress(decided = 1, total = 2, later = 1), tabs.runProgress())
  }

  @Test
  fun theLaterChipListsTheBlocksWithLaterPostsAndOnlyThose() {
    val (p1, _, p3) = block()
    p1.markLater()
    p3.markLater()

    val rows = queueRows(tabs.tabs.value, emptyList(), null, null, gOnly = false, QueueFilter.Later, emptySet()) { null }

    val head = rows.first() as QueueRow.Item
    assertEquals("Artist A", head.tab.title)
    assertEquals(2, head.fold?.kept)
    assertEquals("2 later", head.fold?.label)
    assertTrue(rows.filterIsInstance<QueueRow.Item>().none { it.tab.title == "Artist B" })
  }

  @Test
  fun theFoldSplitsLaterFromNotSeen() {
    val (p1, p2, _, p4) = block()
    p1.markLater()
    p2.markLater()
    now += 1.minutes
    seen.record("p4", "mp4")

    val rows = queueRows(tabs.tabs.value, emptyList(), null, null, gOnly = false, QueueFilter.All, emptySet(), seen = { it.seenSinceKept(seen) }) { null }

    val head = rows.filterIsInstance<QueueRow.Item>().first { it.tab.title == "Artist A" }
    assertEquals("2 later · 1 seen · 1 not seen", head.fold?.label)
    assertTrue(queueSubtitle(p1, current = false, seen = true, rating = null).endsWith("later"))
    assertTrue(queueSubtitle(p4, current = false, seen = true, rating = null).endsWith("seen"))
  }
}
