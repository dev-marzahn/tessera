package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import com.google.fonts.icons.close
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DoneWithTest {
  @Test
  fun theButtonClosesTheSource() = runComposeUiTest {
    var closed = 0
    setContent { SankakuTheme { DoneWith("artist_x", keptStay = 0, onClose = { closed++ }) } }

    onNodeWithText("Done with artist_x?").assertExists()
    onNodeWithText("Close artist_x").performClick()

    assertEquals(1, closed)
  }

  @Test
  fun goneIntoFromSortItOffersCloseNextCardAndLater() = runComposeUiTest {
    var closed = 0
    var later = 0
    setContent {
      SankakuTheme { DoneWith("scenery", keptStay = 4, onClose = { closed++ }, closeLabel = CLOSE_NEXT_CARD, onLater = { later++ }) }
    }

    onNodeWithText("Done with scenery?").assertExists()
    onNodeWithText("4 kept stay").assertExists()
    onNodeWithText(CLOSE_NEXT_CARD).performClick()
    onNodeWithText(DONE_WITH_LATER).performClick()

    assertEquals(1, closed)
    assertEquals(1, later)
  }

  @Test
  fun onAnArtistItOffersFollowAndClose() = runComposeUiTest {
    var closed = 0
    var followed = 0
    setContent {
      SankakuTheme { DoneWith("artist_y", keptStay = 4, onClose = { closed++ }, followAndClose = DoneWithFollow(close) { followed++ }) }
    }

    onNodeWithText("Close artist_y").assertExists()
    onNodeWithText("New posts by artist_y will come in Following").assertExists()
    onNodeWithContentDescription("Follow artist_y and close").assertHasClickAction().performClick()

    assertEquals(0, closed)
    assertEquals(1, followed)
  }

  @Test
  fun goneIntoFromSortAnArtistOffersAllThree() = runComposeUiTest {
    setContent {
      SankakuTheme {
        DoneWith("artist_y", 0, onClose = {}, closeLabel = CLOSE_NEXT_CARD, onLater = {}, followAndClose = DoneWithFollow(close) {})
      }
    }

    onNodeWithText(CLOSE_NEXT_CARD).assertExists()
    onNodeWithContentDescription(followAndCloseName("artist_y")).assertExists()
    onNodeWithText(DONE_WITH_LATER).assertExists()
  }

  @Test
  fun withoutAFollowThereIsNoFollowAndClose() = runComposeUiTest {
    setContent { SankakuTheme { DoneWith("scenery", keptStay = 0, onClose = {}) } }

    onNodeWithText(FOLLOW_AND_CLOSE).assertDoesNotExist()
  }

  @Test
  fun theButtonIsAHeading() = runComposeUiTest {
    setContent { SankakuTheme { DoneWith("artist_x", keptStay = 0, onClose = {}) } }

    val config = onNodeWithText("Close artist_x").fetchSemanticsNode().config
    assertTrue(config.contains(SemanticsProperties.Heading))
    assertTrue(config.contains(SemanticsActions.OnClick))
  }

  @Test
  fun theKeptPostsThatStayAreSaid() = runComposeUiTest {
    setContent { SankakuTheme { DoneWith("artist_x", keptStay = 30, onClose = {}) } }

    onNodeWithText("30 kept stay").assertExists()
  }

  @Test
  fun aLongNameWrapsAtALargeFontInsteadOfBeingCutOff() = runComposeUiTest {
    val name = "an_artist_with_a_very_long_name another_tag_that_is_long"
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(2f, fontScale = 2f)) {
        SankakuTheme { DoneWith(name, keptStay = 0, onClose = {}, modifier = Modifier.width(240.dp)) }
      }
    }

    val question = onNodeWithText("Done with $name?").fetchSemanticsNode().boundsInRoot
    assertTrue(question.height > 100f, "${question.height}")
    assertTrue(question.width <= 481f, "${question.width}")
    val button = onNodeWithText("Close $name")
    assertTrue(hasClickAction().matches(button.fetchSemanticsNode()))
    assertTrue(button.fetchSemanticsNode().boundsInRoot.width <= 481f)
  }
}
