package dev.marzahn.sankaku.features.grid

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.marzahn.sankaku.features.grid.GridScreenFixture.SecondPage
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class ViewerEndTest {
  private var fixture: GridScreenFixture? = null

  private fun fixture(secondPage: SecondPage) = GridScreenFixture(secondPageAtStart = secondPage).also {
    fixture = it
    it.start()
  }

  @AfterTest
  fun stop() {
    fixture?.stop()
  }

  private fun ComposeUiTest.has(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

  private fun ComposeUiTest.openTheLastLoadedPost(fixture: GridScreenFixture) = with(fixture) {
    showGrid()
    cursorTo(1)
    press(Key.Spacebar)
    repeat(5) { press(Key.D) }
    assertEquals(6, viewerPost())
  }

  @Test
  fun pastTheLastPostItSaysNoMorePostsAndPreviousReturnsToIt() = runDesktopComposeUiTest(width = 1280, height = 832) {
    val fixture = fixture(SecondPage.None)
    openTheLastLoadedPost(fixture)
    with(fixture) {
      onNodeWithContentDescription("Next post", substring = true).assertExists()
      press(Key.D)
      onNodeWithText(NoMorePosts).assertExists()
      onNodeWithContentDescription("Next post", substring = true).assertDoesNotExist()

      press(Key.A)
      assertFalse(has(NoMorePosts))
      assertEquals(6, viewerPost())
    }
  }

  @Test
  fun whileTheNextPageLoadsItSaysSoAndThenShowsItsFirstPost() = runDesktopComposeUiTest(width = 1280, height = 832) {
    val fixture = fixture(SecondPage.Hold)
    openTheLastLoadedPost(fixture)
    with(fixture) {
      press(Key.D)
      waitUntil(timeoutMillis = 5_000) { has(LoadingMorePosts) }

      releaseSecondPage()
      waitUntil(timeoutMillis = 5_000) { has("Fixture 7") }
      assertEquals(7, viewerPost())
      assertFalse(has(LoadingMorePosts))
    }
  }

  @Test
  fun aFailedNextPageShowsRetryWhichLoadsItAndStepsOn() = runDesktopComposeUiTest(width = 1280, height = 832) {
    val fixture = fixture(SecondPage.Fail)
    openTheLastLoadedPost(fixture)
    with(fixture) {
      press(Key.D)
      waitUntil(timeoutMillis = 5_000) { onEndPage("Retry") }

      secondPage = SecondPage.Serve
      onNode(hasText("Retry") and hasAnyAncestor(hasTestTag(ViewerEndTag))).performClick()
      waitUntil(timeoutMillis = 5_000) { has("Fixture 7") }
      assertEquals(7, viewerPost())
      assertFalse(onEndPage("Retry"))
    }
  }

  @Test
  fun aFailedNextPageGivesTheFailuresAdviceWithItsSteps() = runDesktopComposeUiTest(width = 1280, height = 832) {
    val fixture = fixture(SecondPage.SearchTimeout)
    openTheLastLoadedPost(fixture)
    with(fixture) {
      press(Key.D)
      waitUntil(timeoutMillis = 5_000) { onEndPage("Edit search") }
      assertFalse(onEndPage("HTTP"))
    }
  }

  private fun ComposeUiTest.onEndPage(text: String) =
    onAllNodes(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(ViewerEndTag))).fetchSemanticsNodes().isNotEmpty()
}
