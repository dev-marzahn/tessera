package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.data.PrivacySettings
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class QueueAccessibilityTest {
  private val tabs = TabsController()

  private fun ComposeUiTest.show(fontScale: Float = 1f, width: Dp = Dp.Unspecified) {
    val privacy = PrivacySettings(InMemoryTextFile())
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(2f, fontScale = fontScale)) {
        SankakuTheme {
          Box(if (width == Dp.Unspecified) Modifier else Modifier.width(width)) {
            PhoneQueuePage(
              onDismiss = {},
              controller = tabs,
              posts = TabPosts.None,
              privacy = privacy,
              seen = null,
              interacted = { false }, onInteract = {},
              onMenu = {},
            )
          }
        }
      }
    }
    waitForIdle()
  }

  @Test
  fun theHeaderKeepsEveryButtonOnAPhoneAtTwiceTheFont() = runComposeUiTest {
    repeat(12) { tabs.open(Route.Grid(listOf("tag$it")), background = true) }
    show(fontScale = 2f, width = 360.dp)

    val buttons = listOf("Find in queue", "G only", "Close queue", "More").map { name ->
      onNodeWithContentDescription(name).getUnclippedBoundsInRoot().also { assertTrue(it.width >= 40.dp, "$name: $it") }
    }
    buttons.zipWithNext().forEach { (a, b) ->
      assertTrue((b.left + b.right) / 2 - (a.left + a.right) / 2 >= 48.dp - 0.5.dp, "$a, $b")
    }
    assertTrue(buttons.last().right + 4.dp <= 360.dp, "${buttons.last()}")
    val title = onNodeWithText("Queue").getUnclippedBoundsInRoot()
    val close = onNodeWithContentDescription("Close queue").getUnclippedBoundsInRoot()
    assertTrue(title.top >= close.bottom - 1.dp, "title $title beside the buttons $close")
  }

  @Test
  fun theHeaderIsOneLineAtNormalText() = runComposeUiTest {
    show(width = 360.dp)
    val title = onNodeWithText("Queue").getUnclippedBoundsInRoot()
    val close = onNodeWithContentDescription("Close queue").getUnclippedBoundsInRoot()
    assertTrue(title.top < close.bottom && title.bottom > close.top, "title $title, close $close")
  }

  private fun ComposeUiTest.headingTexts(): List<String> =
    onAllNodes(SemanticsMatcher("is a heading") { it.config.contains(SemanticsProperties.Heading) })
      .fetchSemanticsNodes()
      .map { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty() }

  @Test
  fun theSectionsAreHeadingsForHeadingNavigation() = runComposeUiTest {
    tabs.open(Route.Grid(listOf("feed:following")), background = true)
    tabs.open(Route.Grid(listOf("artist_x")), background = true)
    tabs.close(tabs.tabs.value.first { it.title == "Artist X" }.id)
    show()

    val texts = headingTexts()
    assertTrue("Queue" in texts, "$texts")
    assertFalse("Feeds" in texts, "$texts")
    assertTrue("Recently closed" in texts, "$texts")
  }

  @Test
  fun aTwoHundredPercentFontCutsNoRowShort() = runComposeUiTest {
    tabs.open(Route.Grid(listOf("an_artist_with_a_very_long_name", "another_tag_that_is_long", "and_a_third_one")), background = true)
    tabs.activate(tabs.tabs.value.last().id)
    val title = tabs.activeTab.title
    show(fontScale = 2f)

    listOf(title, "Newest · current", "Continue · $title").forEach { text ->
      val nodes = onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes()
      assertTrue(nodes.isNotEmpty(), "no node for $text")
      nodes.forEach { node ->
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val layout = layouts.firstOrNull() ?: return@forEach
        assertFalse((0 until layout.lineCount).any(layout::isLineEllipsized), "\"$text\" is cut off with a “…”")
      }
    }
  }

  @Test
  fun aMoveSaysWhereTheItemWentSinceFocusStaysOnThePlace() = runComposeUiTest {
    tabs.open(Route.Grid(listOf("a")), background = true)
    tabs.open(Route.Grid(listOf("b")), background = true)
    show()
    mainClock.autoAdvance = false

    onNodeWithText("A").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
      .first { it.label == "Move down" }.action()
    mainClock.advanceTimeBy(100)

    val live = onAllNodes(SemanticsMatcher("polite live region") { it.config.contains(SemanticsProperties.LiveRegion) })
      .fetchSemanticsNodes()
      .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
    assertTrue("A moved to item 3 of 3" in live, "$live")
  }
}
