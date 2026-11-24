package dev.marzahn.sankaku.features.grid

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.marzahn.sankaku.api.dto.PostDto
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KeepFromViewerTest {
  private val fixture = GridScreenFixture()

  @BeforeTest
  fun start() = fixture.start()

  @AfterTest
  fun stop() = fixture.stop()

  @Test
  fun eInTheViewerKeepsThePostShownAfterTheGridsBlock() = runDesktopComposeUiTest(width = 1280, height = 832) {
    with(fixture) {
      showGrid()
      cursorTo(2)
      press(Key.Spacebar)
      assertEquals(2, viewerPost())

      press(Key.E, shift = true)
      press(Key.E, alt = true)
      assertEquals(listOf("fixture_tag"), queue())

      press(Key.E)
      assertTrue(viewerOpen(), "E closed the viewer")
      assertEquals(listOf("fixture_tag", "fixture-2"), queue())
      assertEquals("Kept in tab 2", messages.last())
      assertEquals(grid, tabs.activeTab.top)

      press(Key.D)
      press(Key.E)
      assertEquals(listOf("fixture_tag", "fixture-2", "fixture-3"), queue())
      assertEquals("Kept in tab 3", messages.last())

      press(Key.A)
      press(Key.E)
      assertEquals(listOf("fixture_tag", "fixture-2", "fixture-3"), queue())
      assertEquals("Already in tab 2", messages.last())
    }
  }

  @Test
  fun eInTheTagListStillIncludesTheTag() = runDesktopComposeUiTest(width = 1280, height = 832) {
    with(fixture) {
      showGrid()
      cursorTo(1)
      press(Key.Spacebar)
      press(Key.T)
      press(Key.E)
      assertTrue(queue().any { "fixture_1" in it.split(" ") }, "E didn't include the tag: ${queue()}")
      assertTrue(queue().none { it.startsWith("fixture-") }, "E kept the post: ${queue()}")
    }
  }

  private val post = PostDto(id = "fixture-1", md5 = "fixture-md5-1", rating = "s", status = "active")

  @Test
  fun thePhoneMenuKeepsOverAGrid() = runDesktopComposeUiTest {
    var kept = 0
    setContent { SankakuTheme { PostMenu(post, shown = true, onKeep = { kept++ }) } }
    onNodeWithContentDescription("More").performClick()
    onNodeWithText(KEEP_FOR_LATER).performClick()

    assertEquals(1, kept)
    onAllNodesWithText(KEEP_FOR_LATER).assertCountEquals(0)
  }

  @Test
  fun aKeptPostsMenuRemovesItInPlaceOfKeep() = runDesktopComposeUiTest {
    var kept = 0
    var removed = 0
    setContent { SankakuTheme { PostMenu(post, shown = true, onKeep = { kept++ }, onRemove = { removed++ }) } }
    onNodeWithContentDescription("More").performClick()
    onAllNodesWithText(KEEP_FOR_LATER).assertCountEquals(0)
    onNodeWithText(REMOVE_FROM_QUEUE).performClick()

    assertEquals(0, kept)
    assertEquals(1, removed)
    onAllNodesWithText(REMOVE_FROM_QUEUE).assertCountEquals(0)
  }

  @Test
  fun aLonePostsMenuHasNoKeep() = runDesktopComposeUiTest {
    setContent { SankakuTheme { PostMenu(post, shown = true) } }
    onNodeWithContentDescription("More").performClick()

    onNodeWithText("Share").assertExists()
    onAllNodesWithText(KEEP_FOR_LATER).assertCountEquals(0)
  }
}
