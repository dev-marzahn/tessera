package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.queue.QueueKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LonePostsTest {
  private val tabs = TabsController()
  private val navigator = TabsNavigator(tabs)
  private val artist = Route.Grid(listOf("artist_x"))
  private fun post(id: String, tags: List<String> = artist.tags) = Route.Post(id, "m$id", tags)

  @Test
  fun aLonePostOpensRightAfterTheActiveItemNotAtTheBlocksEnd() {
    navigator.goTo(artist)
    navigator.openInNewTab(post("k1"))
    navigator.openInNewTab(post("k2"))

    navigator.goTo(post("p"))

    assertEquals(listOf(artist, post("p"), post("k1"), post("k2")), tabs.tabs.value.map { it.top })
  }

  @Test
  fun closingToThePreviousLandsOnTheItemBefore() {
    navigator.goTo(artist)
    val source = tabs.activeTab
    navigator.openInNewTab(post("k1"))
    navigator.goTo(post("p"))

    tabs.close(tabs.activeTab.id, toPrevious = true)

    assertSame(source, tabs.activeTab)
    assertEquals(listOf(artist, post("k1")), tabs.tabs.value.map { it.top })
  }

  @Test
  fun closingTheFirstItemToThePreviousLandsOnTheNext() {
    tabs.open(artist, background = true)
    val first = tabs.tabs.value.first()

    tabs.close(first.id, toPrevious = true)

    assertEquals(artist, tabs.activeTab.top)
  }

  @Test
  fun theNeighboursFollowTheActiveItem() {
    assertEquals(null, tabs.neighbour(forward = false))
    assertEquals(null, tabs.neighbour(forward = true))
    tabs.open(artist, background = true)
    assertEquals(artist, tabs.neighbour(forward = true)?.top)

    assertEquals(true, tabs.next())
    assertEquals(Route.Grid(emptyList()), tabs.neighbour(forward = false)?.top)
    assertEquals(false, tabs.next())
    assertEquals(true, tabs.previous())
    assertEquals(false, tabs.previous())
  }

  @Test
  fun theNeighbourIsWhereAMoveLands() {
    navigator.goTo(artist)
    navigator.openInNewTab(post("k1"))
    tabs.next()
    navigator.goTo(Route.Grid(listOf("tag_y")))

    val next = tabs.neighbour(forward = true)

    assertEquals(post("k1"), next?.top)
    tabs.next()
    assertSame(next, tabs.activeTab)
  }

  private fun saved(id: String, vararg routes: Route, block: String? = null) = SavedTab(id, routes.toList(), block)

  @Test
  fun aPostOverACopyOfItsGridKeepsOnlyThePost() {
    val tabs = listOf(
      saved("a", artist),
      saved("b", Route.Grid(artist.tags, at = "p1"), post("p1"), block = "a"),
    )

    assertEquals(listOf(listOf<Route>(artist), listOf(post("p1"))), loneAtLaunch(tabs).map { it.routes })
  }

  @Test
  fun aPostOverASearchAnotherTabShowsKeepsOnlyThePost() {
    val tabs = listOf(
      saved("a", Route.Grid(listOf("order:popularity", "artist_x"))),
      saved("b", artist, post("p1")),
    )

    val lone = loneAtLaunch(tabs)

    assertEquals(listOf("a", "b"), lone.map { it.id })
    assertEquals(listOf<Route>(post("p1")), lone[1].routes)
  }

  @Test
  fun aGridOnlyUnderAPostIsSplitOutBeforeItAndHeadsItsBlock() {
    val tabs = listOf(saved("a", Route.Grid(emptyList()), artist, post("p1")), saved("c", Route.Grid(listOf("other"))))

    val lone = loneAtLaunch(tabs, newId = { "new" })

    assertEquals(listOf("new", "a", "c"), lone.map { it.id })
    assertEquals(listOf(Route.Grid(emptyList()), artist), lone[0].routes)
    assertEquals(QueueKind.Source, lone[0].kind)
    assertEquals(listOf<Route>(post("p1")), lone[1].routes)
    assertEquals("new", lone[1].block)
  }

  @Test
  fun theTabsOfASplitPostsBlockFollowItIntoTheGridsBlock() {
    val tabs = listOf(saved("a", artist, post("p1")), saved("u", post("p2"), block = "a"))

    val lone = loneAtLaunch(tabs, newId = { "new" })

    assertEquals(listOf("new" to null, "a" to "new", "u" to "new"), lone.map { it.id to it.block })
    val controller = TabsController(object : TabStateStore {
      override fun load() = SavedTabs(activeIndex = 0, tabs = tabs)

      override fun save(tabs: SavedTabs) = Unit

      override fun clear() = Unit
    })
    val grid = controller.tabs.value.first()
    assertEquals(listOf(grid, grid), controller.tabs.value.drop(1).map { controller.sourceOf(it) })
  }

  @Test
  fun twoPostsOverTheSameOnlyGridSplitItOutOnce() {
    val tabs = listOf(saved("a", artist, post("p1")), saved("b", artist, post("p2")))

    val lone = loneAtLaunch(tabs, newId = { "new" })

    assertEquals(listOf("new", "a", "b"), lone.map { it.id })
    assertEquals(listOf<Route>(post("p2")), lone[2].routes)
  }

  @Test
  fun theActiveTabStaysActiveWhenAGridIsSplitOutBeforeIt() {
    val store = object : TabStateStore {
      override fun load() = SavedTabs(
        activeIndex = 1,
        tabs = listOf(saved("a", artist, post("p1")), saved("b", Route.Grid(listOf("other")))),
      )

      override fun save(tabs: SavedTabs) = Unit

      override fun clear() = Unit
    }

    val controller = TabsController(store)

    assertEquals(3, controller.tabs.value.size)
    assertEquals(Route.Grid(listOf("other")), controller.activeTab.top)
  }
}
