package dev.marzahn.sankaku.tabs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class LaterPageTest {
  private val now = Instant.parse("2026-11-15T12:00:00Z")
  private val clock = object : Clock {
    override fun now() = now
  }
  private val tabs = TabsController(clock = clock)
  private val privacy = PrivacySettings(InMemoryTextFile())
  private val seen = SeenStore(InMemoryTextFile(), clock)

  private fun source(tag: String): TabState {
    val route = Route.Grid(listOf(tag))
    tabs.open(route, background = true)
    return tabs.tabs.value.last { it.top == route }
  }

  private fun keepInto(into: TabState, id: String): TabState {
    tabs.activate(into.id)
    tabs.openInBlock(listOf(Route.Post(id, md5 = "m$id", originTags = emptyList(), label = "Post $id", rating = "s")))
    return tabs.tabs.value.first { (it.top as? Route.Post)?.id == id }
  }

  @Test
  fun goThroughLaterSitsBesideClearViewedAtTheBottomAndTheLaterChipListsTheirBlocks() = runComposeUiTest {
    val a = source("artist_a")
    val p1 = keepInto(a, "p1")
    keepInto(a, "p2")
    val b = source("artist_b")
    val p3 = keepInto(b, "p3")
    source("artist_none")
    p1.markLater()
    p3.markLater()
    tabs.activate(a.id)
    var dismissed = false
    setContent {
      SankakuTheme {
        QueuePage(
          onDismiss = { dismissed = true }, controller = tabs, posts = TabPosts.None, privacy = privacy, seen = seen,
          clearViewed = ClearViewed(tabs, seen, Messages()), interacted = { true }, onInteract = {}, folding = true,
        )
      }
    }
    waitForIdle()

    val clear = onNodeWithContentDescription(clearViewedLabel(2)).getUnclippedBoundsInRoot()
    val later = onNodeWithText("Go through later · 2").getUnclippedBoundsInRoot()
    val go = onNodeWithText("Continue · Artist A").getUnclippedBoundsInRoot()
    assertEquals((clear.top + clear.bottom).value / 2, (later.top + later.bottom).value / 2, 4f)
    assertTrue(later.left > clear.right, "Go through later $later beside Clear viewed $clear")
    assertTrue(later.bottom <= go.top, "the row $later above Continue $go")
    val list = onNodeWithTag(QUEUE_PAGE_LIST_TAG).getUnclippedBoundsInRoot()
    assertTrue(later.top > list.top + (list.bottom - list.top) / 2, "the row $later is in the page's lower half")

    onNodeWithText("Later · 2").performClick()
    waitForIdle()
    onNodeWithText("Artist A", useUnmergedTree = true).assertExists()
    onNodeWithText("Artist B", useUnmergedTree = true).assertExists()
    onNodeWithText("Artist None", useUnmergedTree = true).assertDoesNotExist()
    assertEquals(2, onAllNodesWithContentDescription("1 later").fetchSemanticsNodes().size)

    onNodeWithText("Go through later · 2").performClick()
    waitForIdle()
    assertTrue(dismissed)
    assertEquals(p1.id, tabs.activeTab.id)
    assertEquals(KeptRunKind.Later, tabs.keptRunKind())
    assertEquals(1 to 2, tabs.keptPlace())
    assertTrue(tabs.next())
    assertEquals(p3.id, tabs.activeTab.id, "from artist_a's Later post to artist_b's, past p2 and the source")
  }
}
