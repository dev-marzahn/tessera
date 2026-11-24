package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.queue.TodayStore
import dev.marzahn.sankaku.ui.Messages
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class QueuePageLandscapeTest {
  private val clock = object : Clock {
    override fun now() = Instant.parse("2026-11-14T12:00:00Z")
  }
  private val today = TodayStore(InMemoryTextFile(), clock, { TimeZone.UTC })
  private val tabs = TabsController(clock = clock, today = today)
  private val seen = SeenStore(InMemoryTextFile(), clock)

  private fun ComposeUiTest.show(width: Dp, height: Dp, fontScale: Float) {
    listOf("scenery", "sky", "flower", "cat", "landscape", "cloud", "tree", "sunset", "mountain", "ocean", "dog", "bird", "snow", "river")
      .forEach { tag ->
        val route = Route.Grid(listOf(tag))
        tabs.open(route, background = true)
        tabs.tabs.value.last { it.top == route }
          .reportPreview(route, SourcePreview((1..9).map { QueueThumb("m$tag$it", "s") }, about = 1900, loaded = 30))
      }
    tabs.activate(tabs.tabs.value[1].id)
    val privacy = PrivacySettings(InMemoryTextFile())
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
        SankakuTheme {
          Box(Modifier.requiredSize(width, height)) {
            QueuePage(
              onDismiss = {}, controller = tabs, posts = TabPosts.None, privacy = privacy, seen = seen,
              clearViewed = ClearViewed(tabs, seen, Messages()), interacted = { true }, onInteract = {},
              today = today, onSort = {},
            )
          }
        }
      }
    }
    waitForIdle()
  }

  private fun ComposeUiTest.assertRowsGetTheHeight(fontScale: Float) {
    show(LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT, fontScale)

    onNodeWithTag(SIDE_COLUMN_TAG).assertIsDisplayed()
    val list = onNodeWithTag(QUEUE_PAGE_LIST_TAG).getBoundsInRoot()
    val side = onNodeWithTag(SIDE_COLUMN_TAG).getBoundsInRoot()
    assertTrue(list.bottom >= LANDSCAPE_HEIGHT - 1.dp, "the list reaches the bottom: $list")
    assertTrue(list.left >= side.right, "the list is beside the column: $list, $side")
    assertTrue(list.bottom - list.top >= MIN_ROWS_HEIGHT, "rows get ${list.bottom - list.top} at $fontScale×")
    val proceed = onNodeWithText("Continue", substring = true).getBoundsInRoot()
    assertTrue(proceed.right <= list.left && proceed.bottom <= LANDSCAPE_HEIGHT, "Continue: $proceed")
    onNodeWithTag(TODAY_TAG).assertExists()
    onNodeWithText("Sort 14 new").assertExists()
    onNodeWithText("New · 14").assertExists()
    for (item in 1..2) {
      val row = onNodeWithContentDescription("Item $item of 15", substring = true).getBoundsInRoot()
      assertTrue(row.top >= list.top && row.bottom <= list.bottom, "row $item is whole: $row in $list")
    }
  }

  @Test
  fun onAPhoneOnItsSideTheRowsGetTheHeightBesideTheColumn() = runComposeUiTest { assertRowsGetTheHeight(1f) }

  @Test
  fun atTwiceTheFontTheRowsStillGetTheHeight() = runComposeUiTest { assertRowsGetTheHeight(2f) }

  @Test
  fun atTwiceTheFontTheColumnScrollsToItsChips() = runComposeUiTest {
    show(LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT, 2f)

    val chip = onNodeWithText("Started · 0")
    chip.assertIsNotDisplayed()
    val column = onNodeWithTag(SIDE_COLUMN_TAG).getBoundsInRoot()
    val before = onNodeWithText("Continue", substring = true).getBoundsInRoot()

    chip.performScrollTo().assertIsDisplayed()
    assertEquals(before, onNodeWithText("Continue", substring = true).getBoundsInRoot())
    assertTrue(before.bottom >= column.bottom - CONTINUE_MARGIN, "Continue at the foot: $before in $column")
    chip.performClick()
    waitForIdle()
    onNodeWithContentDescription("Item 2 of 15", substring = true).assertDoesNotExist()
  }

  @Test
  fun portraitKeepsThemOverTheRows() = runComposeUiTest {
    show(412.dp, 915.dp, 1f)

    onNodeWithTag(SIDE_COLUMN_TAG).assertDoesNotExist()
    val strip = onNodeWithTag(TODAY_TAG).getBoundsInRoot()
    val list = onNodeWithTag(QUEUE_PAGE_LIST_TAG).getBoundsInRoot()
    assertTrue(list.top >= strip.bottom, "the list is under Today: $list, $strip")
  }

  @Test
  fun findTakesTheWholeWidthOnItsSide() = runComposeUiTest {
    show(LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT, 1f)

    onNodeWithContentDescription("Find in queue").performClick()
    waitForIdle()
    onNodeWithTag(SIDE_COLUMN_TAG).assertDoesNotExist()
    assertTrue(onNodeWithTag(QUEUE_PAGE_LIST_TAG).getBoundsInRoot().left <= 1.dp)
  }

  private companion object {
    val LANDSCAPE_WIDTH = 915.dp
    val LANDSCAPE_HEIGHT = 388.dp

    val MIN_ROWS_HEIGHT = 240.dp

    val CONTINUE_MARGIN = 16.dp
  }
}
