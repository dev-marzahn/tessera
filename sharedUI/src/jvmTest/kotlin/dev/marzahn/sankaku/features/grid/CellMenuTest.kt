package dev.marzahn.sankaku.features.grid

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.marzahn.sankaku.features.auth.SignInController
import dev.marzahn.sankaku.ui.Tip
import dev.marzahn.sankaku.ui.Tips
import org.koin.core.context.GlobalContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CellMenuTest {
  private val fixture = GridScreenFixture()

  @BeforeTest
  fun start() = fixture.start()

  @AfterTest
  fun stop() = fixture.stop()

  private val tips: Tips get() = GlobalContext.get().get()

  private fun androidx.compose.ui.test.ComposeUiTest.rightClickCell(index: Int) {
    onNodeWithTag(gridCellTag(index)).performMouseInput { rightClick() }
    waitForIdle()
  }

  @Test
  fun theMenuNamesItsKeysAndKeepsInTheBackground() = runDesktopComposeUiTest(width = 1280, height = 832) {
    with(fixture) {
      showGrid()
      rightClickCell(1)
      onNodeWithText("Open").assertExists()
      onNodeWithText(MIDDLE_CLICK).assertExists()
      onNodeWithText("Favourite").assertExists()

      onNodeWithText("Open in background tab").performClick()
      waitForIdle()
      assertEquals(listOf("fixture_tag", "fixture-2"), queue())
      assertEquals(grid, tabs.activeTab.top)
      onAllNodesWithText(MIDDLE_CLICK).assertCountEquals(0)
      assertFalse(tips.pending(Tip.KeepLoop))
    }
  }

  @Test
  fun openOpensTheViewerOnThatPost() = runDesktopComposeUiTest(width = 1280, height = 832) {
    with(fixture) {
      showGrid()
      rightClickCell(2)
      onNodeWithText("Open").performClick()
      waitForIdle()
      assertTrue(viewerOpen())
      assertEquals(3, viewerPost())
    }
  }

  @Test
  fun favouriteSignedOutAsksToSignIn() = runDesktopComposeUiTest(width = 1280, height = 832) {
    with(fixture) {
      showGrid()
      rightClickCell(0)
      onNodeWithText("Favourite").performClick()
      waitForIdle()
      assertTrue(GlobalContext.get().get<SignInController>().isOpen.value)
    }
  }

  @Test
  fun theFirstCloseBringsTheKeepLoopTipOnce() = runDesktopComposeUiTest(width = 1280, height = 832) {
    with(fixture) {
      showGrid()
      cursorTo(1)
      press(Key.Spacebar)
      press(Key.Escape)
      waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(Tip.KeepLoop.text).fetchSemanticsNodes().isNotEmpty() }
      assertFalse(tips.pending(Tip.KeepLoop), "shown, the tip is done")
      mainClock.advanceTimeBy(2_000)
      waitForIdle()
      onAllNodesWithText(Tip.KeepLoop.text).assertCountEquals(1)
      onNodeWithText("Got it").performClick()
      waitForIdle()
      onAllNodesWithText(Tip.KeepLoop.text).assertCountEquals(0)

      press(Key.Spacebar)
      press(Key.Escape)
      mainClock.advanceTimeBy(2_000)
      onAllNodesWithText(Tip.KeepLoop.text).assertCountEquals(0)
    }
  }

  @Test
  fun aKeyOnTheGridSendsTheTipAway() = runDesktopComposeUiTest(width = 1280, height = 832) {
    with(fixture) {
      showGrid()
      cursorTo(1)
      press(Key.Spacebar)
      press(Key.Escape)
      waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(Tip.KeepLoop.text).fetchSemanticsNodes().isNotEmpty() }
      press(Key.ShiftLeft)
      press(Key.MetaLeft)
      onAllNodesWithText(Tip.KeepLoop.text).assertCountEquals(1)
      press(Key.D)
      onAllNodesWithText(Tip.KeepLoop.text).assertCountEquals(0)
      assertEquals(2, cursorPost())
    }
  }

  @Test
  fun aKeepBeforeTheFirstCloseMeansNoTip() = runDesktopComposeUiTest(width = 1280, height = 832) {
    with(fixture) {
      showGrid()
      cursorTo(1)
      press(Key.E)
      press(Key.Spacebar)
      press(Key.Escape)
      mainClock.advanceTimeBy(2_000)
      onAllNodesWithText(Tip.KeepLoop.text).assertCountEquals(0)
    }
  }
}
