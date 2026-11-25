package dev.marzahn.sankaku.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.Variant
import dev.marzahn.sankaku.images.PostImage
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class DescribeTabTest {
  private class Screen(route: Route, shown: Boolean = true) {
    var shown by mutableStateOf(shown)
    var route by mutableStateOf(route)
    var contents by mutableStateOf(contentsOf("a"))
  }

  private class GridState(val contents: TabContents)

  private val tabs = TabsController(FileTabStateStore(InMemoryTextFile(null)))
  private val tab = tabs.activeTab
  private val grid = requireNotNull(tab.top)

  private fun ComposeUiTest.describing(content: @Composable () -> Unit) {
    setContent { CompositionLocalProvider(LocalTabEntry provides TabEntry(tab, CONTENT_KEY), content = content) }
    waitForIdle()
  }

  private fun ComposeUiTest.showing(vararg screens: Screen) = describing {
    screens.forEach { screen -> key(screen) { if (screen.shown) DescribeTab(screen.route) { screen.contents } } }
  }

  private fun ComposeUiTest.change(change: () -> Unit) {
    runOnIdle(change)
    waitForIdle()
  }

  @Test
  fun aClosedGridIsNoLongerHeldByItsTab() = runComposeUiTest {
    val screen = Screen(grid)
    lateinit var gridState: WeakReference<GridState>
    describing {
      if (screen.shown) {
        val state = remember { GridState(screen.contents).also { gridState = WeakReference(it) } }
        DescribeTab(grid) { state.contents }
      }
    }

    change { screen.shown = false }

    assertNull(collected(gridState), "the tab still holds the closed grid's state")
  }

  @Test
  fun aClosedGridKeepsWhatItShowedWhenItLeft() = runComposeUiTest {
    val screen = Screen(grid)
    showing(screen)
    val shownWhenLeft = contentsOf("b")

    change { screen.contents = shownWhenLeft }
    change { screen.shown = false }
    change { screen.contents = contentsOf("c") }

    assertSame(shownWhenLeft, tab.topContents())
  }

  @Test
  fun aPoppedGridLeavesNoDescription() = runComposeUiTest {
    val screen = Screen(grid)
    showing(screen)

    change {
      tab.clearEntry(CONTENT_KEY)
      screen.shown = false
    }

    assertEquals(emptyList(), tab.allContents())
  }

  @Test
  fun aGridThatLeavesLateLeavesTheOneThatReplacedItLive() = runComposeUiTest {
    val leaving = Screen(grid)
    val replacing = Screen(grid, shown = false)
    showing(leaving, replacing)
    val shownLater = contentsOf("d")

    change { replacing.shown = true }
    change { leaving.shown = false }
    change { replacing.contents = shownLater }

    assertSame(shownLater, tab.topContents())
  }

  @Test
  fun aGridWhoseRouteChangesDescribesOnlyItsNewRoute() = runComposeUiTest {
    val screen = Screen(grid)
    showing(screen)

    change { screen.route = Route.Grid(listOf("elsewhere")) }

    assertNull(tab.topContents())
    assertEquals(listOf(screen.contents), tab.allContents())
  }

  private fun <T : Any> collected(reference: WeakReference<T>): T? {
    repeat(GC_ROUNDS) {
      if (reference.get() == null) return null
      System.gc()
      Thread.sleep(10)
    }
    return reference.get()
  }

  private companion object {
    const val CONTENT_KEY = "grid"
    const val GC_ROUNDS = 50

    fun contentsOf(md5: String) = TabContents(listOf(PostImage("1", md5, Variant.Preview, null)), emptyList(), 1)
  }
}
