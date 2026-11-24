package dev.marzahn.sankaku.tabs

import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.images.PostImage
import kotlin.test.Test
import kotlin.test.assertEquals

class TabSwitcherPicturesTest {
  private val grid = Route.Grid(listOf("scenery"))

  private fun gridTab(vararg thumbs: QueueThumb) =
    TabState(TabId("t"), listOf(grid), mosaic = QueueMosaic(thumbs.toList(), posts = thumbs.size))

  @Test
  fun gOnlyShowsOnlyTheMosaicsGThumbnails() {
    val tab = gridTab(QueueThumb("g1", "s"), QueueThumb("q1", "q"), QueueThumb("e1", "e"), QueueThumb("none"))
    assertEquals(listOf("g1"), switcherPictures(tab, gOnly = true).map { it.md5 })
    assertEquals(listOf("g1", "q1", "e1", "none"), switcherPictures(tab, gOnly = false).map { it.md5 })
  }

  @Test
  fun gOnlyNeverFallsBackToWhatTheScreenHeld() {
    val tab = TabState(TabId("t"), listOf(grid))
    tab.describe("key", grid) { TabContents(listOf(PostImage("1", "held", Variant.Preview, null)), emptyList(), 1) }
    assertEquals(emptyList(), switcherPictures(tab, gOnly = true))
    assertEquals(listOf("held"), switcherPictures(tab, gOnly = false).map { it.md5 })
  }

  @Test
  fun aMosaicOfOnlyAdultThumbnailsShowsTheIconUnderGOnly() {
    val tab = gridTab(QueueThumb("e1", "e"), QueueThumb("e2", "e"))
    assertEquals(emptyList(), switcherPictures(tab, gOnly = true))
  }
}
