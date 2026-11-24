package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.ui.Message
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.components.alreadySpoken
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class ReturnToOpenerTest {
  private val messages = Messages()
  private var tabs = TabsController()
  private var navigator = TabsNavigator(tabs, messages)

  private val artist = Route.Grid(listOf("artist_x"))
  private val tagY = Route.Grid(listOf("tag_y"))

  private fun post(id: String, from: Route.Grid = artist) = Route.Post(id, "m$id", from.tags, label = id)

  private val titles get() = tabs.tabs.value.map { it.title }
  private val active get() = tabs.activeTab.title

  private fun show(title: String) = tabs.activate(tabs.tabs.value.first { it.title == title }.id)

  private fun touchActiveGrid() = tabs.activeTab.markTouched(tabs.activeTab.top as Route.Grid)

  private fun tapTagOnK3() {
    navigator.goTo(artist)
    touchActiveGrid()
    listOf("k1", "k2", "k3", "k4").forEach { navigator.openInNewTab(post(it)) }
    show("k3")
    navigator.goTo(tagY)
    assertEquals(listOf("Tag Y", "Artist X", "k1", "k2", "k3", "k4"), titles)
    assertEquals("Tag Y", active)
  }

  @Test
  fun clearViewedTakingTheLastPostKeptFromTheSearchReturnsToThePost() {
    tapTagOnK3()
    touchActiveGrid()
    listOf("y1", "y2").forEach { navigator.openInNewTab(post(it, from = tagY)) }
    val seen = SeenStore(InMemoryTextFile(), object : Clock {
      override fun now() = Clock.System.now() + 1.hours
    })
    listOf("y1", "y2").forEach { seen.record(it, "m$it") }
    show("y2")

    tabs.clearViewed(seen)

    assertEquals("k3", active)
  }

  @Test
  fun closingTheSearchReturnsToThePost() {
    tapTagOnK3()

    tabs.close(tabs.active.value)

    assertEquals("k3", active)
  }

  @Test
  fun movingOnPastTheSearchReturnsToThePost() {
    tapTagOnK3()

    assertEquals("k3", tabs.neighbour(forward = true)?.title)
    tabs.next()

    assertEquals("k3", active)
  }

  @Test
  fun closingTheLastPostKeptFromTheSearchReturnsToThePost() {
    tapTagOnK3()
    touchActiveGrid()
    listOf("y1", "y2").forEach { navigator.openInNewTab(post(it, from = tagY)) }

    tabs.close(tabs.active.value)
    assertEquals("y1", active)
    tabs.close(tabs.active.value)
    assertEquals("y2", active)
    tabs.close(tabs.active.value)

    assertEquals("k3", active)
  }

  @Test
  fun movingOnPastTheSearchSaysItsNameForTheWayBackTipButClosingItDoesnt() = runTest(UnconfinedTestDispatcher()) {
    val left = mutableListOf<String>()
    backgroundScope.launch { tabs.wayBacks.collect { left += it } }
    tapTagOnK3()

    tabs.next()
    assertEquals("k3", active)
    assertEquals(listOf("Tag Y"), left)

    tabs.next()
    assertEquals(listOf("Tag Y"), left)

    show("k1")
    navigator.goTo(Route.Grid(listOf("tag_z")))
    assertEquals("Tag Z", active)
    tabs.close(tabs.active.value)
    assertEquals("k1", active)
    assertEquals(listOf("Tag Y"), left)
  }

  @Test
  fun closingTheLastPostOfTheSearchsBlockReturnsToThePostWhileTheSearchStays() {
    tapTagOnK3()
    touchActiveGrid()
    navigator.openInNewTab(post("y1", from = tagY))
    tabs.next()
    assertEquals("y1", active)

    tabs.close(tabs.active.value)

    assertEquals("k3", active)
    assertTrue("Tag Y" in titles)
  }

  @Test
  fun aSearchOpenedFromTheSearchUnwindsOneStepAtATime() {
    tapTagOnK3()
    touchActiveGrid()
    navigator.goTo(Route.Grid(listOf("tag_z")))

    tabs.close(tabs.active.value)
    assertEquals("Tag Y", active)
    tabs.close(tabs.active.value)

    assertEquals("k3", active)
  }

  @Test
  fun undoingTheCloseBringsTheWayBackToo() {
    tapTagOnK3()
    touchActiveGrid()
    navigator.openInNewTab(post("y1", from = tagY))
    tabs.next()
    tabs.close(tabs.active.value)
    assertEquals("k3", active)

    tabs.reopenClosed()
    assertEquals("y1", active)
    tabs.next()

    assertEquals("k3", active)
  }

  @Test
  fun undoingACloseThatDidNotLandThereKeepsTheWayBackUsedUp() {
    tapTagOnK3()
    touchActiveGrid()
    listOf("y1", "y2").forEach { navigator.openInNewTab(post(it, from = tagY)) }
    show("y1")
    tabs.close(tabs.active.value)
    assertEquals("y2", active)
    tabs.next()
    assertEquals("k3", active)

    tabs.reopenClosed()
    show("y2")
    tabs.next()

    assertEquals("Artist X", active)
  }

  @Test
  fun theWayBackHoldsAfterARelaunch() {
    tapTagOnK3()
    val saved = tabs.saved()
    assertTrue(saved.returns.isNotEmpty())

    tabs = TabsController(FakeStore(saved))
    navigator = TabsNavigator(tabs, messages)
    assertEquals("Tag Y", active)
    tabs.close(tabs.active.value)

    assertEquals("k3", active)
  }

  @Test
  fun theWayBackIsUsedUpOnceThePostIsShown() {
    tapTagOnK3()
    tabs.next()
    show("Tag Y")

    tabs.close(tabs.active.value)

    assertEquals("Artist X", active)
  }

  @Test
  fun aTagWhoseSourceIsOpenFurtherOnReturnsToThePostToo() {
    navigator.goTo(artist)
    touchActiveGrid()
    navigator.openInNewTab(post("k1"))
    navigator.openInNewTab(Route.Grid(listOf("far")))
    navigator.openInNewTab(tagY)
    show("k1")

    navigator.goTo(tagY)
    assertEquals("Tag Y", active)
    assertEquals("k1", tabs.neighbour(forward = true)?.title)
    tabs.close(tabs.active.value)

    assertEquals("k1", active)
  }

  @Test
  fun aSearchOpenWithTheSameFiltersSaysAlreadyInTab() = runTest(UnconfinedTestDispatcher()) {
    val shown = mutableListOf<Message>()
    backgroundScope.launch { messages.messages.collect { shown += it } }
    navigator.goTo(artist)
    touchActiveGrid()
    navigator.openInNewTab(post("k1"))
    show("k1")

    navigator.goTo(Route.Grid(listOf("Artist_X ")))
    assertEquals("Artist X", active)
    assertEquals(listOf(alreadySpoken(1)), shown.map { it.text })

    show("k1")
    assertEquals(1, navigator.openInNewTab(artist))
    assertEquals(listOf(alreadySpoken(1), alreadySpoken(1)), shown.map { it.text })
  }

  @Test
  fun aSearchOpenWithOtherFiltersGoesThereSilently() = runTest(UnconfinedTestDispatcher()) {
    val shown = mutableListOf<Message>()
    backgroundScope.launch { messages.messages.collect { shown += it } }
    navigator.goTo(artist)
    touchActiveGrid()
    navigator.openInNewTab(post("k1"))
    show("k1")

    navigator.goTo(Route.Grid(listOf("order:popularity", "artist_x")))
    assertEquals("Artist X", active)
    show("k1")
    assertEquals(1, navigator.openInNewTab(Route.Grid(listOf("artist_x", "rating:s"))))

    assertTrue(shown.isEmpty())
  }

  @Test
  fun theShownSourceItselfSaysNothing() = runTest(UnconfinedTestDispatcher()) {
    val shown = mutableListOf<Message>()
    backgroundScope.launch { messages.messages.collect { shown += it } }
    navigator.goTo(artist)
    touchActiveGrid()

    navigator.goTo(artist)
    assertEquals(1, navigator.openInNewTab(artist))

    assertTrue(shown.isEmpty())
  }

  private class FakeStore(val saved: SavedTabs?) : TabStateStore {
    override fun load() = saved

    override fun save(tabs: SavedTabs) = Unit

    override fun clear() = Unit
  }
}
