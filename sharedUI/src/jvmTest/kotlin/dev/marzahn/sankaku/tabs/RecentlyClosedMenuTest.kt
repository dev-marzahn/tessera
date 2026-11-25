package dev.marzahn.sankaku.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.queue.SeenStore
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RecentlyClosedMenuTest {
  private val seen = SeenStore(InMemoryTextFile())
  private val tabs = TabsController().apply {
    (1..50).forEach { open(Route.Grid(listOf("tag_$it")), background = true) }
  }

  private fun ComposeUiTest.show(gOnly: Boolean = false) {
    val privacy = PrivacySettings(InMemoryTextFile()).apply { setGOnly(gOnly) }
    setContent {
      SankakuTheme {
        PhoneQueuePage(onDismiss = {}, controller = tabs, posts = TabPosts.None, privacy = privacy, seen = seen, interacted = { false }, onInteract = {}, folding = false)
      }
    }
    waitForIdle()
  }

  private fun close(title: String) {
    tabs.close(tabs.tabs.value.first { it.title == title }.id)
  }

  @Test
  fun theOverflowMenusRecentlyClosedBringsTheSectionAtTheEndOfALongPageIntoView() = runComposeUiTest {
    close("Tag 3")
    close("Tag 4")
    val queue = tabs.tabs.value
    show()
    onNodeWithText("Recently closed").assertDoesNotExist()

    onNodeWithContentDescription("More").performClick()
    waitForIdle()
    onNodeWithText("Recently closed (2)").performClick()
    waitForIdle()

    onNodeWithText("Recently closed").assertIsDisplayed()
    onNodeWithText("Tag 4").assertIsDisplayed()
    onNodeWithText("Tag 3").assertIsDisplayed()
    assertEquals(queue, tabs.tabs.value)
    assertEquals(2, tabs.closed.value.size)
  }

  @Test
  fun withGOnlyOnTheEntryCountsOnlyWhatTheSectionLists() = runComposeUiTest {
    close("Tag 3")
    tabs.open(Route.Post("p1", "md5p1", listOf("tag_1"), label = "Post p1", rating = "e"), background = true)
    close("Post p1")
    show(gOnly = true)

    onNodeWithContentDescription("More").performClick()
    waitForIdle()

    onNodeWithText("Recently closed (1)").assertExists()
  }

  @Test
  fun withNothingClosedTheEntryIsOff() = runComposeUiTest {
    show()

    onNodeWithContentDescription("More").performClick()
    waitForIdle()

    onNodeWithText("Recently closed (0)").assertIsNotEnabled()
  }
}
