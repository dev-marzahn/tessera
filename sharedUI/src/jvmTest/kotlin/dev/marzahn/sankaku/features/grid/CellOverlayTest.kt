package dev.marzahn.sankaku.features.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.width
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.ui.openOnClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CellOverlayTest {
  @Test
  fun onlyFavouritesSearchesDimUnfavouritedPosts() {
    assertTrue(isFavouritesSearch(listOf("fav:someone", "rating:s")))
    assertFalse(isFavouritesSearch(listOf("blue_hair", "-fav:someone")))
    assertFalse(isFavouritesSearch(listOf("blue_hair", "order:popularity")))
  }

  @Test
  fun anUnfavouritedPostIsDimmedWithUndoThatFavouritesItAgain() = runComposeUiTest {
    var favourited by mutableStateOf(true)
    var overridden by mutableStateOf(false)
    setContent {
      CellOverlay(
        favourited = favourited,
        unfavourited = overridden && !favourited,
        hovered = false,
        onToggleFavourite = { favourited = !favourited; overridden = true },
        modifier = Modifier.size(200.dp),
      )
    }

    onNodeWithContentDescription("Favourited").assertIsDisplayed()
    onNodeWithText("Undo").assertDoesNotExist()

    favourited = false
    overridden = true
    onNodeWithText("Unfavourited").assertIsDisplayed()
    onNodeWithContentDescription("Favourited").assertDoesNotExist()

    onNodeWithText("Undo").performClick()
    assertTrue(favourited)
    onNodeWithText("Undo").assertDoesNotExist()
    onNodeWithContentDescription("Favourited").assertIsDisplayed()
  }

  @Test
  fun undoFitsOnOneLineInsideAnyCell() {
    data class Case(val width: Int, val height: Int, val fontScale: Float, val label: Boolean)
    val cases = listOf(
      Case(136, 102, 1f, label = true),
      Case(136, 102, 2f, label = false),
      Case(80, 240, 1f, label = false),
      Case(80, 240, 2f, label = false),
      Case(240, 160, 1f, label = true),
    )
    for (case in cases) runComposeUiTest {
      setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = case.fontScale)) {
          SankakuTheme {
            Box(Modifier.size(case.width.dp, case.height.dp).testTag("cell")) {
              CellOverlay(favourited = false, unfavourited = true, hovered = false, onToggleFavourite = {})
            }
          }
        }
      }
      val what = "${case.width}×${case.height} dp at ${case.fontScale}×"
      val cell = onNodeWithTag("cell").getUnclippedBoundsInRoot()
      val undo = onNodeWithText("Undo").assertIsDisplayed().getUnclippedBoundsInRoot()
      assertTrue(undo.left >= cell.left && undo.right <= cell.right, "Undo sticks out sideways in $what: $undo")
      assertTrue(undo.top >= cell.top && undo.bottom <= cell.bottom, "Undo sticks out of $what: $undo")
      val target = onNodeWithText("Undo").fetchSemanticsNode().touchBoundsInRoot
      assertTrue(target.height >= 48f && target.width >= 48f, "Undo's touch target is $target in $what")
      assertTrue(target.top >= cell.top.value && target.bottom <= cell.bottom.value, "Undo's touch target leaves $what: $target")

      val layouts = mutableListOf<TextLayoutResult>()
      onNodeWithText("Undo", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
      assertEquals(1, layouts.single().lineCount, "Undo wraps in $what")
      val text = onNodeWithText("Undo", useUnmergedTree = true).getUnclippedBoundsInRoot()
      assertTrue(text.left >= cell.left && text.right <= cell.right, "Undo's text leaves $what: $text")
      val line = layouts.single()
      assertTrue(line.getLineRight(0) <= line.size.width + 0.5f, "Undo is cut in $what: ${line.getLineRight(0)} > ${line.size}")

      if (case.label) onNodeWithText("Unfavourited").assertIsDisplayed() else onNodeWithText("Unfavourited").assertIsNotDisplayed()
    }
  }

  @Test
  fun theFavouriteButtonIsThereOnlyWhileHovered() = runComposeUiTest {
    var toggles = 0
    var opened = 0
    setContent {
      val hover = remember { MutableInteractionSource() }
      val hovered by hover.collectIsHoveredAsState()
      Box(Modifier.size(200.dp).testTag("cell").hoverable(hover).openOnClick(onOpen = { opened++ }, onOpenInNewTab = {})) {
        CellOverlay(favourited = true, unfavourited = false, hovered = hovered, onToggleFavourite = { toggles++ })
      }
    }

    onNodeWithContentDescription("Unfavourite").assertDoesNotExist()
    onNodeWithTag("cell").performMouseInput { moveTo(center) }
    onNodeWithContentDescription("Unfavourite").assertIsDisplayed().performClick()
    assertEquals(1, toggles)
    assertEquals(0, opened)

    onNodeWithTag("cell").performMouseInput { exit() }
    onNodeWithContentDescription("Unfavourite").assertDoesNotExist()
    onNodeWithContentDescription("Favourited").assertIsDisplayed()
  }

  @Test
  fun aPostThatIsNotFavouritedShowsItsHeartOnlyWhileHovered() = runComposeUiTest {
    var favourited by mutableStateOf(false)
    setContent {
      val hover = remember { MutableInteractionSource() }
      val hovered by hover.collectIsHoveredAsState()
      Box(Modifier.size(200.dp).testTag("cell").hoverable(hover)) {
        CellOverlay(
          favourited = favourited,
          unfavourited = false,
          hovered = hovered,
          onToggleFavourite = { favourited = !favourited },
        )
      }
    }

    onNodeWithContentDescription("Favourite").assertDoesNotExist()
    onNodeWithTag("cell").performMouseInput { moveTo(center) }
    onNodeWithContentDescription("Favourite").assertIsDisplayed().performClick()
    assertTrue(favourited)

    onNodeWithContentDescription("Unfavourite").assertIsDisplayed()
    onNodeWithContentDescription("Favourited").assertDoesNotExist()
  }
}
