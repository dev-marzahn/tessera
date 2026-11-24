package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.GridPlace
import dev.marzahn.sankaku.data.InMemoryTextFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabPlaceTest {
  private val file = InMemoryTextFile(null)
  private val store = FileTabStateStore(file)
  private val tabs = TabsController(store)
  private val a = Route.Grid(listOf("artist_a"))
  private val deep = GridPlace("p9", cursor = "c5", page = 5, time = 1_761_739_200)

  private fun openA(): TabState {
    tabs.open(a, background = false)
    return tabs.activeTab
  }

  @Test
  fun thePlaceIsSavedAndRestoredWithItsTab() {
    openA().reportPlace(a, deep)
    store.save(tabs.saved())

    val after = TabsController(store)

    val restored = after.tabs.value.single { it.top == a }
    assertEquals(deep, restored.place)
    assertEquals(deep, restored.placeFor(a))
    assertTrue(file.read()!!.contains(""""place":{"post":"p9","cursor":"c5","page":5,"time":1761739200}"""))
  }

  @Test
  fun aPlaceOnTheFirstPageSavesOnlyItsPost() {
    openA().reportPlace(a, GridPlace("p2", time = null))
    store.save(tabs.saved())

    assertTrue(file.read()!!.contains(""""place":{"post":"p2"}"""))
    assertEquals(GridPlace("p2"), TabsController(store).tabs.value.single { it.top == a }.place)
  }

  @Test
  fun goingToTheTopClearsThePlace() {
    val tab = openA()
    tab.reportPlace(a, deep)
    tab.reportPlace(a, null)
    store.save(tabs.saved())

    assertFalse(file.read()!!.contains("place"))
    assertNull(TabsController(store).tabs.value.single { it.top == a }.place)
  }

  @Test
  fun thePlaceBelongsToTheGridItWasReportedFor() {
    val tab = openA()
    tab.reportPlace(a, deep)
    tab.backStack.add(Route.Grid(listOf("artist_b")))

    assertNull(tab.place)
    store.save(tabs.saved())
    assertFalse(file.read()!!.contains("place"))
  }

  @Test
  fun aClosedTabReopensWithItsPlace() {
    val tab = openA()
    tab.reportPlace(a, deep)

    tabs.close(tab.id)
    tabs.reopenClosed()

    assertEquals(deep, tabs.activeTab.place)
  }

  @Test
  fun clearingTheStoreForgetsEveryPlace() {
    openA().reportPlace(a, deep)
    store.save(tabs.saved())

    store.clear()

    assertTrue(TabsController(store).tabs.value.none { it.place != null })
  }
}
