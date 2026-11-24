package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class InTabMarkTest {
  @Test
  fun theChipSaysWhichTabForABitThenGoesBack() = runComposeUiTest {
    lateinit var already: AlreadyInTab
    setContent {
      SankakuTheme {
        already = rememberAlreadyInTab()
        InTabChip(Modifier.testTag("chip"), already.of("p1"))
      }
    }
    onNodeWithTag("chip").assertContentDescriptionEquals("In tab")

    mainClock.autoAdvance = false
    already.show("p2", 5)
    mainClock.advanceTimeByFrame()
    onNodeWithTag("chip").assertContentDescriptionEquals("In tab")

    already.show("p1", 37)
    mainClock.advanceTimeByFrame()
    onNodeWithTag("chip").assertContentDescriptionEquals("Already in tab 37")
    onNodeWithText("Already tab 37", useUnmergedTree = true).assertExists()

    mainClock.advanceTimeBy(ALREADY_TAB_MS + 100)
    onNodeWithTag("chip").assertContentDescriptionEquals("In tab")
  }

  @Test
  fun aSeenKeptPostKeptAgainSaysKeptAgainForABit() = runComposeUiTest {
    lateinit var already: AlreadyInTab
    setContent {
      SankakuTheme {
        already = rememberAlreadyInTab()
        InTabChip(Modifier.testTag("chip"), already.of("p1"))
      }
    }
    mainClock.autoAdvance = false

    already.show("p1", 37, again = true)
    mainClock.advanceTimeByFrame()
    onNodeWithTag("chip").assertContentDescriptionEquals("Kept again")
    onNodeWithText("Kept again", useUnmergedTree = true).assertExists()

    mainClock.advanceTimeBy(ALREADY_TAB_MS + 100)
    onNodeWithTag("chip").assertContentDescriptionEquals("In tab")
  }

  @Test
  fun aSeenKeptCellAtThePhonesSmallestSizeFitsBothMarks() = runComposeUiTest {
    setContent {
      SankakuTheme {
        Row(Modifier.width(68.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          SeenBadge()
          InTabChip(touch = true)
        }
      }
    }

    val layouts = mutableListOf<TextLayoutResult>()
    onNodeWithText("Kept", useUnmergedTree = true)
      .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    assertFalse(layouts.single().hasVisualOverflow, "the label isn't cut off")
  }

  @Test
  fun theChipKeepsItsIconWhereThereIsRoom() {
    assertTrue(inTabChipShowsIcon(70.dp, already = false))
    assertFalse(inTabChipShowsIcon(40.dp, already = false))
    assertFalse(inTabChipShowsIcon(96.dp, already = true))
    assertTrue(inTabChipShowsIcon(120.dp, already = true))
  }
}
