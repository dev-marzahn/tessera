package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.data.InMemoryTextFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JumpBackTest {
  private val file = InMemoryTextFile(null)
  private val tabs = TabsController(FileTabStateStore(file))
  private val navigator = TabsNavigator(tabs)
  private val artist = Route.Grid(listOf("artist_x"))
  private val cloud = Route.Grid(listOf("cloud"))
  private val sky = Route.Grid(listOf("sky"))

  private fun post(id: String) = Route.Post(id, "m$id", artist.tags, label = id)

  private fun ids() = tabs.tabs.value.map { it.id }

  private fun tabOf(route: Route) = tabs.tabs.value.first { it.top == route }

  private fun onALonePost(): TabState {
    tabs.open(artist, background = false)
    navigator.openInNewTab(post("k1"))
    navigator.openInNewTab(cloud)
    navigator.openInNewTab(sky)
    navigator.goTo(post("p"))
    assertEquals(listOf(Route.Grid(emptyList()), artist, post("p"), post("k1"), cloud, sky), tabs.tabs.value.map { it.top })
    return tabs.activeTab
  }

  @Test
  fun backOnAnOpenSourceATagJumpedToReturnsToThePostAndClosesNothing() {
    val p = onALonePost()
    val before = ids()

    navigator.goTo(cloud)
    val source = tabs.activeTab
    assertSame(tabOf(cloud), source)
    assertSame(p, tabs.jumpedFrom(source))
    assertNull(tabs.openerOf(source))

    navigator.goBack()

    assertSame(p, tabs.activeTab)
    assertEquals(before, ids())
    assertTrue(tabs.closed.value.isEmpty())
  }

  @Test
  fun diversionsUnwindOneStepAtATime() {
    val p = onALonePost()
    navigator.goTo(cloud)
    navigator.goTo(sky)
    assertSame(tabOf(sky), tabs.activeTab)

    navigator.goBack()
    assertSame(tabOf(cloud), tabs.activeTab)
    navigator.goBack()
    assertSame(p, tabs.activeTab)
    assertNull(tabs.jumpedFrom(p))
  }

  @Test
  fun aJumpBackAndForthStillUnwindsToWhereItStarted() {
    val p = onALonePost()
    navigator.goTo(cloud)
    navigator.goTo(sky)
    navigator.goTo(cloud)

    navigator.goBack()
    assertSame(tabOf(sky), tabs.activeTab)
    navigator.goBack()
    assertSame(tabOf(cloud), tabs.activeTab)
    navigator.goBack()
    assertSame(p, tabs.activeTab)
  }

  @Test
  fun movingOnFromAnItemKeepsTheWayBackFromIt() {
    val p = onALonePost()
    navigator.goTo(cloud)
    val source = tabs.activeTab

    assertTrue(tabs.next())
    navigator.goBack()
    assertSame(source, tabs.activeTab)
    assertSame(p, tabs.jumpedFrom(source))
  }

  @Test
  fun anItemShownByNoJumpOrMoveForgetsTheWayBack() {
    onALonePost()
    navigator.goTo(cloud)
    val source = tabs.activeTab

    assertTrue(tabs.next())
    tabs.activate(source.id)

    assertNull(tabs.jumpedFrom(source))
  }

  @Test
  fun anItemReachedByASwipeGoesBackAlongTheSwipeThenTheJumps() {
    val p = onALonePost()
    navigator.goTo(cloud)
    val source = tabs.activeTab
    navigator.goTo(sky)
    assertTrue(tabs.previous())
    assertSame(source, tabs.activeTab)
    assertSame(tabOf(sky), tabs.jumpedFrom(source))

    navigator.goBack()
    assertSame(tabOf(sky), tabs.activeTab)
    navigator.goBack()
    assertSame(source, tabs.activeTab)
    navigator.goBack()
    assertSame(p, tabs.activeTab)
  }

  @Test
  fun aQueueRowTappedIsAJump() {
    val p = onALonePost()
    val source = tabOf(sky)

    tabs.jumpTo(source.id)

    assertSame(source, tabs.activeTab)
    assertTrue(tabs.backFromJump(source.id))
    assertSame(p, tabs.activeTab)
    assertFalse(tabs.backFromJump(p.id))
  }

  @Test
  fun aPostSomeTabShowsAlreadyIsAJump() {
    val p = onALonePost()

    navigator.goTo(post("k1"))
    val kept = tabs.activeTab
    assertEquals(post("k1"), kept.top)

    navigator.goBack()

    assertSame(p, tabs.activeTab)
  }

  @Test
  fun aSearchRunInTheJumpedToGridIsUndoneFirst() {
    val p = onALonePost()
    navigator.goTo(cloud)
    val source = tabs.activeTab
    navigator.goTo(Route.Grid(listOf("cloud", "order:popularity")))
    assertEquals(2, source.backStack.size)
    assertNull(tabs.jumpedFrom(source))

    navigator.goBack()
    assertSame(source, tabs.activeTab)
    assertEquals(cloud, source.top)
    navigator.goBack()
    assertSame(p, tabs.activeTab)
  }

  @Test
  fun aJumpComesBeforeClosingAGridOpenedFromAnItem() {
    val p = onALonePost()
    val tag = Route.Grid(listOf("tag_t"))
    navigator.goTo(tag)
    val grid = tabs.activeTab
    assertSame(p, tabs.openerOf(grid))
    tabs.jumpTo(tabOf(sky).id)
    tabs.jumpTo(grid.id)

    navigator.goBack()
    assertSame(tabOf(sky), tabs.activeTab)
    assertTrue(grid in tabs.tabs.value)
  }

  @Test
  fun theWayBackHoldsAfterARelaunch() {
    onALonePost()
    navigator.goTo(cloud)
    FileTabStateStore(file).save(tabs.saved())

    val relaunched = TabsController(FileTabStateStore(file))
    val source = relaunched.activeTab
    assertEquals(cloud, source.top)
    assertEquals(post("p"), relaunched.jumpedFrom(source)?.top)
    assertTrue(relaunched.backFromJump(source.id))
    assertEquals(post("p"), relaunched.activeTab.top)
  }

  @Test
  fun aFileWithoutJumpsWritesNone() {
    TabsController(FileTabStateStore(file)).let { FileTabStateStore(file).save(it.saved()) }
    assertFalse(file.read().orEmpty().contains("jumps"))
  }

  @Test
  fun backOnAJumpedToItemReturnsOnAPhone() {
    assertEquals(QueueBack.Return, queueBack(cardUp = false, lonePost = false, previous = true, jumped = true))
    assertEquals(QueueBack.Return, queueBack(cardUp = false, lonePost = true, previous = true, opened = true, jumped = true))
    assertEquals(QueueBack.Previous, queueBack(cardUp = true, lonePost = false, previous = true, jumped = true))
    assertEquals(QueueBack.Deck, queueBack(cardUp = false, lonePost = false, previous = true, sorting = true, jumped = true))
    assertEquals(QueueBack.Return, queueBack(cardUp = false, lonePost = false, previous = true, deckWaiting = true, jumped = true))
  }

  @Test
  fun aTagWhoseSearchIsOpenKnowsItsQueueItem() {
    onALonePost()
    assertEquals(5, tabs.queuePositionOf(Route.Grid(listOf("cloud", "order:popularity"))))
    assertEquals(2, tabs.queuePositionOf(artist))
    assertNull(tabs.queuePositionOf(Route.Grid(listOf("rain"))))
  }

  @Test
  fun theQueuePositionsFoundOnceAnswerAsEachLookUp() {
    onALonePost()
    val positions = tabs.queuePositions()
    for (route in listOf(Route.Grid(listOf("cloud", "order:popularity")), artist, Route.Grid(listOf("rain")), Route.Grid(emptyList()))) {
      assertEquals(tabs.queuePositionOf(route), positions(route), "$route")
    }
  }
}
