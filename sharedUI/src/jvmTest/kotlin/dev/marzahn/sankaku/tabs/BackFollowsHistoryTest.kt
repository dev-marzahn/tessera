package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.data.InMemoryTextFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BackFollowsHistoryTest {
  private val file = InMemoryTextFile(null)
  private val tabs = TabsController(FileTabStateStore(file))
  private val navigator = TabsNavigator(tabs)
  private val artist = Route.Grid(listOf("artist_x"))
  private val cloud = Route.Grid(listOf("cloud"))
  private val sky = Route.Grid(listOf("sky"))

  private fun post(id: String) = Route.Post(id, "m$id", artist.tags, label = id)

  private fun tabOf(route: Route) = tabs.tabs.value.first { it.top == route }

  private fun fourGrids() {
    navigator.openInNewTab(artist)
    navigator.openInNewTab(cloud)
    navigator.openInNewTab(sky)
    assertEquals(listOf(Route.Grid(emptyList()), artist, cloud, sky), tabs.tabs.value.map { it.top })
    assertEquals(Route.Grid(emptyList()), tabs.activeTab.top)
  }

  private fun phoneBack(): QueueBack = tabs.activeTab.let { active ->
    queueBack(
      cardUp = false,
      lonePost = active.backsLikeALonePost,
      previous = tabs.neighbour(forward = false) != null,
      opened = tabs.openerOf(active) != null,
      jumped = tabs.backReturnOf(active) != null,
    )
  }

  @Test
  fun backOnAGridReachedByMovingOnShowsTheItemMovedOnFrom() {
    fourGrids()
    val browse = tabs.activeTab
    val before = tabs.tabs.value.map { it.id }
    assertTrue(tabs.next())
    val grid = tabs.activeTab
    assertEquals(artist, grid.top)

    assertSame(browse, tabs.backReturnOf(grid))
    assertEquals(QueueBack.Return, phoneBack())

    navigator.goBack()

    assertSame(browse, tabs.activeTab)
    assertEquals(before, tabs.tabs.value.map { it.id })
    assertTrue(tabs.closed.value.isEmpty())
  }

  @Test
  fun backAfterMovingTowardsTheStartShowsTheItemMovedOnFrom() {
    fourGrids()
    assertTrue(tabs.next())
    assertTrue(tabs.next())
    assertTrue(tabs.previous())
    assertSame(tabOf(artist), tabs.activeTab)

    assertSame(tabOf(cloud), tabs.backReturnOf(tabs.activeTab))
  }

  @Test
  fun backWalksTheHistoryOnceThenLeavesTheApp() {
    fourGrids()
    repeat(3) { assertTrue(tabs.next()) }
    assertTrue(tabs.previous())

    val walked = mutableListOf<Route?>()
    while (phoneBack() == QueueBack.Return) {
      navigator.goBack()
      walked += tabs.activeTab.top
      assertTrue(walked.size < 20, "Back loops: $walked")
    }

    assertEquals(listOf<Route?>(sky, cloud, artist, Route.Grid(emptyList())), walked)
    assertEquals(QueueBack.Tab, phoneBack())
    assertTrue(tabs.closed.value.isEmpty())
  }

  private fun walkBack(): List<Route?> {
    val walked = mutableListOf<Route?>()
    while (phoneBack() != QueueBack.Tab) {
      navigator.goBack()
      walked += tabs.activeTab.top
      assertTrue(walked.size < 20, "Back loops: $walked")
    }
    return walked
  }

  private fun aKeptPostAlone(): TabState {
    navigator.openInNewTab(post("k1"))
    tabs.close(tabs.activeTab.id)
    assertEquals(listOf<Route?>(post("k1")), tabs.tabs.value.map { it.top })
    return tabs.activeTab
  }

  @Test
  fun backOnAGridOpenedByATapReturnsToWhereItWasTapped() {
    aKeptPostAlone()
    val tag = Route.Grid(listOf("tag_t"))
    navigator.goTo(tag)
    assertEquals(tag, tabs.activeTab.top)

    assertEquals(QueueBack.Close, phoneBack())
    assertEquals(listOf<Route?>(post("k1")), walkBack())
  }

  @Test
  fun aUsedSourceJustBeforeItsItemIsReturnedFromOnce() {
    aKeptPostAlone()
    val tag = Route.Grid(listOf("tag_t"))
    navigator.goTo(tag)
    tabs.activeTab.reportPlace(tag, GridPlace("p1", "c1", 0, index = 0))
    assertEquals(listOf<Route?>(tag, post("k1")), tabs.tabs.value.map { it.top })

    assertEquals(listOf<Route?>(post("k1"), tag), walkBack())
  }

  @Test
  fun aLonePostOnTheFirstItemWithNoHistoryLeavesTheApp() {
    navigator.openInNewTab(post("k1"))
    tabs.close(tabs.activeTab.id)
    assertEquals(listOf<Route?>(post("k1")), tabs.tabs.value.map { it.top })

    assertEquals(QueueBack.Tab, phoneBack())
  }

  @Test
  fun backOnALonePostToThePreviousItemAddsNoStep() {
    navigator.openInNewTab(post("k1"))
    navigator.openInNewTab(post("k2"))
    tabs.activate(tabs.tabs.value[2].id)
    assertEquals(QueueBack.Previous, phoneBack())

    assertTrue(tabs.backToPrevious())

    assertEquals(post("k1"), tabs.activeTab.top)
    assertNull(tabs.backReturnOf(tabs.activeTab))
    assertEquals(QueueBack.Previous, phoneBack())
  }

  @Test
  fun backOnALonePostUsesUpTheStepFromThePreviousItem() {
    navigator.openInNewTab(post("k1"))
    navigator.openInNewTab(post("k2"))
    tabs.activate(tabs.tabs.value[1].id)
    assertTrue(tabs.next())
    assertSame(tabs.tabs.value[1], tabs.backReturnOf(tabs.activeTab))

    assertTrue(tabs.backToPrevious())

    assertTrue(tabs.next())
    assertTrue(tabs.backReturn(tabs.activeTab.id))
    assertEquals(post("k1"), tabs.activeTab.top)
    assertNull(tabs.backReturnOf(tabs.activeTab))
  }

  @Test
  fun closingTheItemMovedOnFromPassesItOver() {
    fourGrids()
    assertTrue(tabs.next())
    assertTrue(tabs.next())
    tabs.close(tabOf(cloud).id)
    assertEquals(sky, tabs.activeTab.top)

    assertEquals(artist, tabs.backReturnOf(tabs.activeTab)?.top)
    navigator.goBack()
    assertEquals(artist, tabs.activeTab.top)
    navigator.goBack()
    assertEquals(Route.Grid(emptyList()), tabs.activeTab.top)
  }

  @Test
  fun theHistoryHoldsAfterARelaunch() {
    fourGrids()
    assertTrue(tabs.next())
    assertTrue(tabs.next())
    FileTabStateStore(file).save(tabs.saved())

    val relaunched = TabsController(FileTabStateStore(file))
    val navigator = TabsNavigator(relaunched)
    assertEquals(cloud, relaunched.activeTab.top)
    assertEquals(artist, relaunched.backReturnOf(relaunched.activeTab)?.top)
    navigator.goBack()
    assertEquals(artist, relaunched.activeTab.top)
    navigator.goBack()
    assertEquals(Route.Grid(emptyList()), relaunched.activeTab.top)
    assertNull(relaunched.backReturnOf(relaunched.activeTab))
  }

  @Test
  fun aStepSavesAsMovedAndAJumpAsBefore() {
    fourGrids()
    assertTrue(tabs.next())
    tabs.jumpTo(tabOf(sky).id)
    FileTabStateStore(file).save(tabs.saved())

    val saved = FileTabStateStore(file).load()?.jumps.orEmpty()
    assertEquals(listOf(true, false), saved.map { it.moved })
    assertEquals(1, Regex("\"moved\"").findAll(file.read().orEmpty()).count())
  }

  @Test
  fun whileTheDeckWaitsBackOnAnItemWanderedOnToStillReturnsToTheDeck() {
    fourGrids()
    tabs.activate(tabOf(artist).id)
    assertTrue(tabs.next())
    val wandered = tabs.activeTab

    assertNull(tabs.backReturnOf(wandered, moves = false))
    assertFalse(tabs.backReturn(wandered.id, moves = false))
    assertEquals(
      QueueBack.Deck,
      queueBack(cardUp = false, lonePost = false, previous = true, deckWaiting = true, jumped = tabs.backReturnOf(wandered, moves = false) != null),
    )
    tabs.jumpTo(tabOf(sky).id)
    assertSame(wandered, tabs.backReturnOf(tabs.activeTab, moves = false))
  }
}
