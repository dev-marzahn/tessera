package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.TabsNavigator
import dev.marzahn.sankaku.data.InMemoryTextFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RefinedFromTest {
  private val file = InMemoryTextFile(null)
  private val store = FileTabStateStore(file)
  private val tabs = TabsController(store)
  private val navigator = TabsNavigator(tabs)
  private val landscape = Route.Grid(listOf("landscape"))
  private val landscapeSky = Route.Grid(listOf("landscape", "sky"))

  private val titles get() = tabs.tabs.value.map { it.title }

  private fun subtitle(tab: TabState) = queueSubtitle(tab, current = false, seen = false, rating = null)

  private fun onTouchedLandscape() {
    navigator.goTo(landscape)
    tabs.activeTab.markTouched(landscape)
  }

  @Test
  fun aSearchChangedOnATouchedGridKeepsItsTitleAndSaysWhereItCameFrom() {
    onTouchedLandscape()

    navigator.goTo(landscapeSky)

    assertEquals(listOf("Landscape Sky", "Landscape"), titles)
    assertEquals("Landscape", tabs.activeTab.refinedFrom)
    assertEquals("Grid · from Landscape", subtitle(tabs.activeTab))
    assertEquals("Grid", subtitle(tabs.tabs.value[1]))
  }

  @Test
  fun anyChangeReadsTheSame() {
    onTouchedLandscape()

    navigator.goTo(Route.Grid(listOf("Landscape", "-cloud", "tree", "river", "order:popularity")))

    assertEquals("Grid · popular · from Landscape", subtitle(tabs.activeTab))
  }

  @Test
  fun aSearchThatKeepsNoneOfTheGridsTagsSaysNothing() {
    onTouchedLandscape()

    navigator.goTo(Route.Grid(listOf("order:popularity")))
    assertNull(tabs.activeTab.refinedFrom)
    navigator.goTo(landscape)
    tabs.activeTab.markTouched(landscape)
    navigator.goTo(Route.Grid(listOf("parent:123")))
    assertNull(tabs.activeTab.refinedFrom)
    navigator.goTo(landscape)
    tabs.activeTab.markTouched(landscape)
    navigator.goTo(Route.Grid(listOf("sky")))
    assertNull(tabs.activeTab.refinedFrom)
  }

  @Test
  fun aSearchOnAnUntouchedGridReplacesItAndSaysNothing() {
    navigator.goTo(landscape)

    navigator.goTo(landscapeSky)

    assertEquals(listOf("Landscape Sky"), titles)
    assertNull(tabs.activeTab.refinedFrom)
  }

  @Test
  fun aSearchFromBrowseOrAPostSaysNothing() {
    tabs.activeTab.markTouched(Route.Grid(emptyList()))
    navigator.goTo(landscape)
    assertNull(tabs.activeTab.refinedFrom)

    tabs.openLone(Route.Post("p", "mp", originTags = listOf("landscape")))
    navigator.goTo(landscapeSky)
    assertNull(tabs.activeTab.refinedFrom)
  }

  @Test
  fun aSearchRunInPlaceOverItSaysNothingAndBackBringsItBack() {
    onTouchedLandscape()
    navigator.goTo(landscapeSky)
    val refined = tabs.activeTab

    navigator.goTo(Route.Grid(listOf("landscape", "night")))
    assertNull(refined.refinedFrom)

    navigator.goBack()
    assertEquals("Landscape", refined.refinedFrom)
  }

  @Test
  fun itStillSaysSoWhenItsParentCloses() {
    onTouchedLandscape()
    navigator.goTo(landscapeSky)

    tabs.close(tabs.tabs.value.single { it.title == "Landscape" }.id)

    assertEquals("Landscape", tabs.activeTab.refinedFrom)
  }

  @Test
  fun closedAndReopenedItStillSaysSo() {
    onTouchedLandscape()
    navigator.goTo(landscapeSky)

    tabs.reopen(tabs.close(tabs.activeTab.id)!!)

    assertEquals("Landscape", tabs.tabs.value.single { it.title == "Landscape Sky" }.refinedFrom)
  }

  @Test
  fun itStillSaysSoAfterARelaunch() {
    onTouchedLandscape()
    navigator.goTo(landscapeSky)
    store.save(tabs.saved())
    assertTrue(file.read()!!.contains(""""refines":["landscape"]"""), file.read())

    val relaunched = TabsController(FileTabStateStore(file))

    assertEquals("Landscape", relaunched.tabs.value.single { it.title == "Landscape Sky" }.refinedFrom)
  }

  @Test
  fun aTabOpenedFromNoGridSavesNoRefines() {
    navigator.goTo(landscape)
    store.save(tabs.saved())

    assertFalse(file.read()!!.contains("refines"))
  }
}
