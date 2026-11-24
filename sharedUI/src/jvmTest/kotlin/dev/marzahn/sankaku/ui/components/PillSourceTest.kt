package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PillSourceTest {
  private val scale = 2.625f
  private val height = 120.dp

  private fun px(dp: Dp) = (dp.value * scale).toInt()

  private fun ComposeUiTest.showPill(text: CountPillText, width: Dp, buttons: Boolean = false, fontScale: Float = 1f) =
    showPill(text, mutableStateOf(width), buttons, fontScale)

  private fun ComposeUiTest.showPill(text: CountPillText, width: State<Dp>, buttons: Boolean = false, fontScale: Float = 1f) {
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(scale, fontScale)) {
        SankakuTheme {
          Box(Modifier.size(width.value, height)) {
            PillRow(
              Modifier.fillMaxWidth().align(Alignment.BottomCenter),
              start = { if (buttons) CloseCorner(canReopen = false, onClose = {}, onReopen = { false }) },
              pill = {
                CountPill(
                  text = text,
                  onOpen = {},
                  canReopen = false,
                  onReopen = { false },
                  actions = emptyList(),
                  arrows = buttons,
                  previousLabel = "Previous",
                  nextLabel = "Next",
                  modifier = Modifier.testTag(PILL),
                )
              },
            )
          }
        }
      }
    }
    waitForIdle()
  }

  private fun ComposeUiTest.shown(text: String): List<SemanticsNode> =
    onAllNodes(hasText(text, substring = false), useUnmergedTree = true).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }

  private fun SemanticsNode.layout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    config[SemanticsActions.GetTextLayoutResult].action!!(results)
    return results.single()
  }

  private val TextLayoutResult.cut: Boolean
    get() = multiParagraph.didExceedMaxLines ||
      (size.width >= layoutInput.constraints.maxWidth && getLineRight(0) > size.width + 0.5f)

  private fun ComposeUiTest.assertWhole(text: String) {
    val node = shown(text).singleOrNull() ?: error("\"$text\" isn't shown: ${shownTexts()}")
    assertFalse(node.layout().cut, "\"$text\" is cut: ${shownTexts()}")
  }

  private fun ComposeUiTest.assertCut(text: String) {
    val node = shown(text).singleOrNull() ?: error("\"$text\" isn't shown: ${shownTexts()}")
    assertTrue(node.layout().cut, "\"$text\" isn't cut: ${shownTexts()}")
  }

  private fun ComposeUiTest.assertNotShown(text: String) = assertTrue(shown(text).isEmpty(), "\"$text\" is shown")

  private fun ComposeUiTest.shownTexts() =
    onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }
      .map {
        it.layout().let { layout ->
          "${layout.layoutInput.text.text} (${layout.size.width} of ${layout.layoutInput.constraints.maxWidth} px${if (layout.cut) ", cut" else ""})"
        }
      }

  private fun place(source: String, tags: List<String>, figures: String = "1 / 55") =
    CountPillText(source, figures, "Queue", short = ShortSource.of(tags))

  @Test
  fun aLongNameTakesTheRoomThePillHasBeforeItIsShortened() = runDesktopComposeUiTest(width = px(412.dp), height = px(height)) {
    showPill(place("Lighthouse Night", listOf("Lighthouse", "Night")), width = 412.dp)
    val name = shown("Lighthouse Night").single().layout()

    assertWhole("Lighthouse Night")
    assertTrue(name.size.width / scale > 112f, "the name is ${name.size.width / scale} dp wide")
    assertNotShown("Lighthouse")
    assertWhole("1 / 55")
  }

  @Test
  fun aMultiTagNameThatStillDoesNotFitShowsItsFirstTagAndHowManyMore() = runDesktopComposeUiTest(width = px(393.dp), height = px(height)) {
    showPill(place("Lighthouse Starry Sky Night", listOf("Lighthouse", "Starry Sky", "Night")), width = 393.dp)

    assertNotShown("Lighthouse Starry Sky Night")
    assertWhole("Lighthouse")
    assertWhole(" +2")
    assertWhole("1 / 55")
  }

  @Test
  fun inTheLeastRoomTheFirstTagIsCutAndTheCountKept() = runDesktopComposeUiTest(width = px(360.dp), height = px(height)) {
    showPill(place("Lighthouse Starry Sky Night", listOf("Lighthouse", "Starry Sky", "Night")), width = 360.dp, buttons = true)

    assertNotShown("Lighthouse Starry Sky Night")
    assertCut("Lighthouse")
    assertWhole(" +2")
    assertWhole("1 / 55")
    val pill = onNodeWithTag(PILL).getUnclippedBoundsInRoot()
    val close = onNodeWithContentDescription(CLOSE_LABEL).getUnclippedBoundsInRoot()
    val previous = onNodeWithContentDescription("Previous").getUnclippedBoundsInRoot()
    val next = onNodeWithContentDescription("Next").getUnclippedBoundsInRoot()
    assertTrue(close.right <= pill.left, "pill $pill over ✕ $close")
    assertTrue(previous.left >= pill.left && next.right <= pill.right, "‹ $previous or › $next outside the pill $pill")
    assertEquals(48f, next.width.value, 0.5f, "› $next")
  }

  @Test
  fun aNameOfOneTagEndsInAnEllipsisAndTheFiguresStayWhole() = runDesktopComposeUiTest(width = px(360.dp), height = px(height)) {
    val name = "Kuraue Hinata (Yama no Susume)"
    showPill(place(name, listOf(name)), width = 360.dp)

    assertCut(name)
    assertWhole("1 / 55")
  }

  @Test
  fun thePillKeepsClearOfTopAndUndosRoom() = runDesktopComposeUiTest(width = px(360.dp), height = px(height)) {
    val name = "Kuraue Hinata (Yama no Susume)"
    showPill(place(name, listOf(name)), width = 360.dp)

    val pill = onNodeWithTag(PILL).getUnclippedBoundsInRoot()
    assertTrue(pill.right <= 360.dp - BottomRowLayout.CornerCentre - BottomRowLayout.PieceTarget / 2, "pill $pill over ↑ Top")
    assertTrue(pill.left >= 8.dp + BottomRowLayout.PieceTarget + PillGap, "pill $pill leaves Undo no room")
  }

  @Test
  fun withQueueButtonsOnANarrowPhoneAKeptRunsLaterGivesWayRatherThanThePillGoOverClose() =
    runDesktopComposeUiTest(width = px(360.dp), height = px(height)) {
      val text = place("Lighthouse Starry Sky Night", listOf("Lighthouse", "Starry Sky", "Night"), figures = "24 of 48")
        .copy(later = "5 later", progress = 0.5f)
      showPill(text, width = 360.dp, buttons = true)

      assertWhole("24 of 48")
      assertCut("5 later")
      val pill = onNodeWithTag(PILL).getUnclippedBoundsInRoot()
      val close = onNodeWithContentDescription(CLOSE_LABEL).getUnclippedBoundsInRoot()
      val next = onNodeWithContentDescription("Next").getUnclippedBoundsInRoot()
      assertTrue(close.right <= pill.left, "pill $pill over ✕ $close")
      assertTrue(next.right <= pill.right, "› $next outside the pill $pill")
    }

  @Test
  fun theWholeNameIsShownOnlyWhereItFitsWhole() = runDesktopComposeUiTest(width = px(420.dp), height = px(height)) {
    val width = mutableStateOf(420.dp)
    showPill(place("Lighthouse Night", listOf("Lighthouse", "Night")), width)
    val wholeAt = listOf(360, 364, 368, 372, 376, 380).filter { phone ->
      width.value = phone.dp
      waitForIdle()
      val whole = shown("Lighthouse Night").isNotEmpty()
      if (whole) assertWhole("Lighthouse Night") else assertWhole(" +1")
      whole
    }
    assertTrue(wholeAt.isNotEmpty() && wholeAt.size < 6, "whole at $wholeAt dp")
  }

  @Test
  fun atALargeFontTheShortFormStillKeepsItsCount() = runDesktopComposeUiTest(width = px(360.dp), height = px(height)) {
    showPill(place("Lighthouse Starry Sky", listOf("Lighthouse", "Starry Sky")), width = 360.dp, fontScale = 2f)

    assertNotShown("Lighthouse Starry Sky")
    assertWhole(" +1")
    assertWhole("1 / 55")
  }

  private companion object {
    const val PILL = "pill"
  }
}
