package dev.marzahn.sankaku.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.Route
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class QueuePageUndoRowTest {
  private val tabs = TabsController().apply {
    open(Route.Grid(listOf("a")), background = true)
    open(Route.Grid(listOf("b")), background = true)
  }
  private val pill = ClosedTabUndo(tabs)

  private fun titles() = tabs.tabs.value.map { it.title }

  private fun ComposeUiTest.show() {
    setContent {
      SankakuTheme {
        EndUndoAtNextAction(pill, tabs)
        UndoHiddenWhile(true, pill)
        Box(Modifier.width(412.dp)) {
          PhoneQueuePage(controller = tabs, undo = pill, folding = false)
        }
      }
    }
    waitForIdle()
  }

  @Test
  fun swipingASecondRowLeavesOnlyItsUndo() = runComposeUiTest {
    show()

    closeRow("A")
    onNodeWithText("Closed “A”").assertExists()
    closeRow("B")

    onNodeWithText("Closed “A”").assertDoesNotExist()
    onNodeWithText("Closed “B”").assertExists()
    assertEquals(listOf("Browse"), titles())
    assertEquals("B", pill.pending.value?.closed?.title)
  }

  @Test
  fun theNextActionEndsASwipedRowsUndoWhileThePageCoversThePill() = runComposeUiTest {
    show()

    closeRow("A")
    onNodeWithText("Closed “A”").assertExists()
    runOnIdle { tabs.open(Route.Grid(listOf("c")), background = true) }
    waitForIdle()

    onNodeWithText("Closed “A”").assertDoesNotExist()
    assertNull(pill.pending.value)
    assertEquals(listOf("Browse", "B", "C"), titles())
  }

  @Test
  fun theRowsUndoReopensInPlaceAndEndsThePillsToo() = runComposeUiTest {
    show()

    closeRow("A")
    onNodeWithText("Undo").performClick()
    waitForIdle()

    assertEquals(listOf("Browse", "A", "B"), titles())
    assertNull(pill.pending.value)
    onNodeWithText("Closed “A”").assertDoesNotExist()
  }

  @Test
  fun aBulkClosesUndoEndsAtTheNextActionToo() = runComposeUiTest {
    show()

    onNodeWithContentDescription("More").performClick()
    onNodeWithText("Close all but current (2)").performClick()
    waitForIdle()
    onNodeWithText("Closed 2").assertExists()

    runOnIdle { tabs.open(Route.Grid(listOf("c")), background = true) }
    waitForIdle()

    onNodeWithText("Closed 2").assertDoesNotExist()
    assertEquals(listOf("Browse", "C"), titles())
  }
}
