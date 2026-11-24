package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.data.InMemoryTextFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class TouchedGridTest {
  private val file = InMemoryTextFile(null)
  private val store = FileTabStateStore(file)
  private val tabs = TabsController(store)
  private val browse = Route.Grid(emptyList())
  private val scenery = Route.Grid(listOf("scenery"))

  private val titles get() = tabs.tabs.value.map { it.title }

  @Test
  fun aSearchOnATouchedGridOpensANewSourceInFrontOfIt() {
    val grid = tabs.activeTab
    grid.markTouched(browse)

    TabsNavigator(tabs).goTo(scenery)

    assertEquals(listOf("Scenery", "Browse"), titles)
    assertEquals(listOf<Route>(browse), grid.backStack.toList())
  }

  @Test
  fun aSearchOnAnUntouchedGridStillReplacesItInPlace() {
    TabsNavigator(tabs).goTo(scenery)

    assertEquals(listOf("Scenery"), titles)
    assertEquals(listOf(browse, scenery), tabs.activeTab.backStack.toList())
  }

  @Test
  fun theFlagIsForTheGridItWasSetOnAndComesBackWithIt() {
    val tab = tabs.activeTab
    tab.markTouched(browse)
    assertTrue(tab.isTouched(browse))

    tab.backStack.add(Route.Grid(listOf("order:popularity")))
    assertFalse(tab.topIsTouchedGrid)
    tab.backStack.removeLast()
    assertTrue(tab.topIsTouchedGrid)
  }

  @Test
  fun aTouchedGridIsSavedWithItsTabAndStaysTouchedAfterARelaunch() {
    tabs.activeTab.markTouched(browse)
    store.save(tabs.saved())
    assertTrue(file.read()!!.contains(""""gridTouched":true"""), file.read())

    val relaunched = TabsController(store)
    assertTrue(relaunched.activeTab.topIsTouchedGrid)
    TabsNavigator(relaunched).goTo(scenery)

    assertEquals(listOf("Scenery", "Browse"), relaunched.tabs.value.map { it.title })
  }

  @Test
  fun anUntouchedGridSavesNoFlag() {
    tabs.open(scenery, background = true)
    store.save(tabs.saved())

    assertFalse(file.read()!!.contains("gridTouched"))
    assertTrue(TabsController(store).tabs.value.none { it.topIsTouchedGrid })
  }

  @Test
  fun aFileWithoutTheFlagLoadsAndSavesBackUnchanged() {
    val saved = """{"version":2,"session":"s1","activeIndex":0,"tabs":[""" +
      """{"id":"a","routes":[{"type":"dev.marzahn.sankaku.Route.Grid","tags":["artist_a"]}],"kind":"source","touched":1000,"addedIn":"s1"},""" +
      """{"id":"b","routes":[{"type":"dev.marzahn.sankaku.Route.Grid","tags":["artist_b"]}],"place":{"post":"p9"},"kind":"source","touched":1000,"addedIn":"s1"}""" +
      """]}"""
    val existing = InMemoryTextFile(saved)
    val existingStore = FileTabStateStore(existing)
    val before = existingStore.load()
    val clock = object : Clock {
      override fun now() = Instant.fromEpochSeconds(1000)
    }
    val controller = TabsController(existingStore, clock = clock)

    assertEquals(before, controller.saved())
    assertEquals(listOf(false, true), controller.tabs.value.map { it.topIsTouchedGrid })
    existingStore.save(controller.saved())
    assertFalse(existing.read()!!.contains("gridTouched"))
    assertEquals(before, existingStore.load())
  }

  @Test
  fun aV1FileMigratesUntouched() {
    val v1 = """{"activeIndex":0,"tabs":[{"id":"a","routes":[{"type":"dev.marzahn.sankaku.Route.Grid","tags":["artist_a"]}]}]}"""

    val migrated = TabsController(FileTabStateStore(InMemoryTextFile(v1))).saved()

    assertNull(migrated.tabs.single().gridTouched)
  }

  @Test
  fun aGridWithAPlaceIsTouchedBeforeItScrollsThereAndSavesNoFlag() {
    tabs.activeTab.reportPlace(browse, GridPlace("p30", time = null))

    assertTrue(tabs.activeTab.topIsTouchedGrid)
    assertNull(tabs.saved().tabs.single().gridTouched)
    TabsNavigator(tabs).goTo(scenery)
    assertEquals(listOf("Scenery", "Browse"), titles)
  }

  @Test
  fun theSameGridOpenedAgainAfterBackIsUntouched() {
    val navigator = TabsNavigator(tabs)
    navigator.goTo(scenery)
    tabs.activeTab.markTouched(scenery)

    navigator.goBack()
    navigator.goTo(scenery)

    assertFalse(tabs.activeTab.topIsTouchedGrid)
    assertEquals(listOf(browse, scenery), tabs.activeTab.backStack.toList())
  }

  @Test
  fun aTouchedGridReopenedFromRecentlyClosedIsStillTouched() {
    tabs.open(scenery, background = false)
    tabs.activeTab.markTouched(scenery)

    tabs.close(tabs.activeTab.id)
    tabs.reopenClosed()

    assertEquals("Scenery", tabs.activeTab.title)
    assertTrue(tabs.activeTab.topIsTouchedGrid)
  }

  @Test
  fun aTouchedGridAnEarlierVersionFadedComesBackTouchedAtLaunch() {
    val store = FileTabStateStore(InMemoryTextFile(null))
    val faded = SavedFaded(at = 0, tabs = listOf(SavedTab("scenery", listOf(scenery), gridTouched = true)))
    store.save(SavedTabs(activeIndex = 0, tabs = listOf(SavedTab("browse", listOf(browse))), faded = listOf(faded)))

    val relaunched = TabsController(store)

    assertTrue(relaunched.tabs.value.single { it.top == scenery }.topIsTouchedGrid)
  }

  @Test
  fun scrollingBackToTheTopLeavesItTouched() {
    val tab = tabs.activeTab
    tab.reportPlace(browse, GridPlace("p30", time = null))
    tab.markTouched(browse)
    tab.reportPlace(browse, null)

    assertTrue(tab.topIsTouchedGrid)
    assertEquals(true, tabs.saved().tabs.single().gridTouched)
  }
}
