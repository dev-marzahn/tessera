package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PostTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class LoadedTitleTest {
  private val file = InMemoryTextFile(null)
  private val store = FileTabStateStore(file)
  private val tabs = TabsController(store)
  private val parent = Route.Post("p1", null, listOf("artist_a"))

  private fun openParent(): TabState {
    tabs.openLone(parent)
    return tabs.activeTab
  }

  @Test
  fun aPostWithNoLabelIsUntitledUntilItLoads() {
    val tab = openParent()
    assertEquals(PostTitle.UNTITLED, tab.title)
    assertTrue(tab.untitled)

    tabs.loadedTitle(parent, "Monochrome · 1boy")

    assertEquals("Monochrome · 1boy", tab.title)
    assertFalse(tab.untitled)
  }

  @Test
  fun aKeptPostAnEarlierVersionFadedKeepsItsNameAsItComesBack() {
    val oldStore = FileTabStateStore(InMemoryTextFile(null))
    val faded = SavedFaded(
      at = 0,
      tabs = listOf(
        SavedTab("a", listOf(Route.Grid(listOf("a")))),
        SavedTab("kept", listOf(parent), block = "a", keptAt = 0, title = "Monochrome · 1boy"),
      ),
    )
    oldStore.save(SavedTabs(activeIndex = 0, tabs = listOf(SavedTab("browse", listOf(Route.Grid(emptyList())))), faded = listOf(faded)))

    val after = TabsController(oldStore)

    assertEquals("Monochrome · 1boy", after.tabs.value.single { it.top == parent }.title)
  }

  @Test
  fun aLabelledPostKeepsItsLabel() {
    val route = Route.Post("p2", "m2", emptyList(), label = "Zelda")
    tabs.openLone(route)

    tabs.loadedTitle(route, "Something else")

    assertEquals("Zelda", tabs.activeTab.title)
    assertFalse(file.read().orEmpty().contains("Something else"))
  }

  @Test
  fun aPostWithNoNameStaysUntitled() {
    val tab = openParent()

    tabs.loadedTitle(parent, null)
    tabs.loadedTitle(parent, " ")

    assertEquals(PostTitle.UNTITLED, tab.title)
    store.save(tabs.saved())
    assertFalse(file.read()!!.contains("title"))
  }

  @Test
  fun aReloadThatNamesNothingForgetsTheOldName() {
    val tab = openParent()
    tabs.loadedTitle(parent, "Monochrome · 1boy")

    tabs.loadedTitle(parent, null)

    assertEquals(PostTitle.UNTITLED, tab.title)
    store.save(tabs.saved())
    assertFalse(file.read()!!.contains("title"))
  }

  @Test
  fun theTitleIsSavedWithTheTabAndRestored() {
    openParent()
    tabs.loadedTitle(parent, "Monochrome · 1boy")
    store.save(tabs.saved())

    assertTrue(file.read()!!.contains(""""title":"Monochrome · 1boy""""))
    val restored = TabsController(store).tabs.value.single { it.top == parent }
    assertEquals("Monochrome · 1boy", restored.title)
    assertFalse(restored.untitled)
  }

  @Test
  fun theTitleBelongsToThePostAndNotTheTab() {
    val tab = openParent()
    tabs.loadedTitle(parent, "Monochrome · 1boy")

    tabs.close(tab.id)
    tabs.reopenClosed()
    val other = Route.Post("p3", null, emptyList())
    tabs.openLone(other)

    assertEquals("Monochrome · 1boy", tabs.tabs.value.single { it.top == parent }.title)
    assertEquals(PostTitle.UNTITLED, tabs.tabs.value.single { it.top == other }.title)
  }

  @Test
  fun aClosedPostIsNamedInRecentlyClosedAndKeepsTheNameOnReopening() {
    val tab = openParent()
    tabs.loadedTitle(parent, "Monochrome · 1boy")
    val closed = tabs.close(tab.id)!!

    assertEquals("Monochrome · 1boy", closed.title)
    assertFalse(closed.untitled)
    tabs.forgetTitles()
    tabs.reopen(closed)
    assertEquals("Monochrome · 1boy", tabs.activeTab.title)
  }

  @Test
  fun aFileWithoutTitlesLoadsAndSavesBackUnchanged() {
    val saved = """{"version":2,"session":"s1","activeIndex":0,"tabs":[""" +
      """{"id":"a","routes":[{"type":"dev.marzahn.sankaku.Route.Grid","tags":["artist_a"]}],"kind":"source","touched":1000,"addedIn":"s1"},""" +
      """{"id":"b","routes":[{"type":"dev.marzahn.sankaku.Route.Post","id":"p1","md5":null,"originTags":[]}],"block":"a","kind":"post","touched":1000,"addedIn":"s1","keptAt":1000}""" +
      """]}"""
    val existing = InMemoryTextFile(saved)
    val existingStore = FileTabStateStore(existing)
    val before = existingStore.load()

    val clock = object : Clock {
      override fun now() = Instant.fromEpochSeconds(1000)
    }
    val controller = TabsController(existingStore, clock = clock)

    assertEquals(before, controller.saved())
    existingStore.save(controller.saved())
    assertFalse(existing.read()!!.contains("title"))
    assertEquals(before, existingStore.load())
  }

  @Test
  fun clearEverythingForgetsTheTitles() {
    val tab = openParent()
    tabs.loadedTitle(parent, "Monochrome · 1boy")

    tabs.forgetTitles()

    assertEquals(PostTitle.UNTITLED, tab.title)
  }
}
