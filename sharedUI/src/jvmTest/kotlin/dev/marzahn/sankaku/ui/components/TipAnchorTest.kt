package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.data.InMemoryTextFile
import dev.marzahn.sankaku.ui.Tip
import dev.marzahn.sankaku.ui.TipAction
import dev.marzahn.sankaku.ui.Tips
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class TipAnchorTest {
  private fun ComposeUiTest.window(tips: Tips, content: @Composable () -> Unit) = setContent {
    SankakuTheme {
      Box(Modifier.fillMaxSize().tipGestures(tips).testTag("window")) {
        content()
        TipLayer(tips)
      }
    }
  }

  private fun ComposeUiTest.awaitTip(text: String) =
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

  @Test
  fun aTapOnTheTipEndsItAndReachesWhatsUnderIt() = runDesktopComposeUiTest(width = 400, height = 800) {
    val tips = Tips(InMemoryTextFile(), touch = true).apply { offer(Tip.GridGestures) }
    var underTaps = 0
    window(tips) {
      Column {
        Box(Modifier.size(200.dp)) { TipAnchor(Tip.GridGestures, tips, visible = true, Modifier.size(200.dp)) }
        Box(Modifier.size(400.dp).clickable { underTaps++ }.testTag("under"))
      }
    }
    awaitTip(Tip.GridGestures.text)

    val tip = onNodeWithText(Tip.GridGestures.text).getBoundsInRoot()
    onNodeWithTag("window").performTouchInput { click(Offset(((tip.left + tip.right) / 2).toPx(), ((tip.top + tip.bottom) / 2).toPx())) }
    waitForIdle()

    onAllNodesWithText(Tip.GridGestures.text).assertCountEquals(0)
    assertEquals(1, underTaps, "the tap on the tip reached the control under it")
    assertNull(tips.current)
  }

  @Test
  fun aTapElsewhereEndsTheTipAndLands() = runDesktopComposeUiTest(width = 400, height = 800) {
    val tips = Tips(InMemoryTextFile(), touch = true).apply { offer(Tip.GridGestures) }
    var taps = 0
    window(tips) {
      Column {
        Box(Modifier.size(200.dp)) { TipAnchor(Tip.GridGestures, tips, visible = true, Modifier.size(200.dp)) }
        Box(Modifier.size(400.dp).clickable { taps++ })
        Box(Modifier.size(100.dp).clickable { taps += 10 }.testTag("elsewhere"))
      }
    }
    awaitTip(Tip.GridGestures.text)
    onNodeWithTag("elsewhere").performClick()
    waitForIdle()
    onAllNodesWithText(Tip.GridGestures.text).assertCountEquals(0)
    assertEquals(10, taps)
  }

  @Test
  fun gotItAndTheTipsButtonStillWork() = runDesktopComposeUiTest(width = 400, height = 800) {
    val tips = Tips(InMemoryTextFile(), touch = true).apply { offer(Tip.OpenedInFront) }
    var shown = 0
    window(tips) {
      Box(Modifier.offset(y = 600.dp).size(100.dp)) {
        TipAnchor(Tip.OpenedInFront, tips, visible = true, Modifier.size(100.dp), action = TipAction("Show queue") { shown++ })
      }
    }
    awaitTip(Tip.OpenedInFront.text)
    onNodeWithText("Show queue").performClick()
    waitForIdle()
    assertEquals(1, shown)
    onAllNodesWithText(Tip.OpenedInFront.text).assertCountEquals(0)
  }

  @Test
  fun aTipThatAppearsMidGestureStaysPastItsEnd() = runDesktopComposeUiTest(width = 400, height = 800) {
    val tips = Tips(InMemoryTextFile(), touch = true).apply { offer(Tip.GridGestures) }
    window(tips) {
      Box(Modifier.size(200.dp)) { TipAnchor(Tip.GridGestures, tips, visible = true, Modifier.size(200.dp)) }
    }
    onNodeWithTag("window").performTouchInput { down(Offset(300f, 700f)) }
    awaitTip(Tip.GridGestures.text)
    onNodeWithTag("window").performTouchInput { up() }
    waitForIdle()
    onAllNodesWithText(Tip.GridGestures.text).assertCountEquals(1)
  }

  @Test
  fun aHoverTooltipDoesntTakeATipAway() = runDesktopComposeUiTest {
    val tips = Tips(InMemoryTextFile(), touch = false).apply { offer(Tip.KeepLoop) }
    val hover = TooltipState()
    window(tips) {
      Column {
        Box(Modifier.size(100.dp)) { TipAnchor(Tip.KeepLoop, tips, visible = true, Modifier.size(100.dp)) }
        TooltipBox(
          positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
          tooltip = { PlainTooltip { Text("Hover hint") } },
          state = hover,
        ) { Box(Modifier.size(40.dp)) }
      }
    }
    awaitTip(Tip.KeepLoop.text)

    val hovering = CoroutineScope(Dispatchers.Main).launch { runCatching { hover.show(MutatePriority.UserInput) } }
    mainClock.advanceTimeBy(1_000)
    waitForIdle()

    onAllNodesWithText(Tip.KeepLoop.text).assertCountEquals(1)
    hovering.cancel()
  }

  @Test
  fun theNextTipWaitsUntilTheGestureAfterOneEndsAndThatGestureStillLands() = runDesktopComposeUiTest {
    val tips = Tips(InMemoryTextFile(), touch = true).apply {
      offer(Tip.QueueSwipe)
      appeared(Tip.QueueSwipe)
      dismissed(Tip.QueueSwipe)
    }
    var dragged = 0f
    setContent {
      Box(Modifier.size(300.dp).tipGestures(tips).testTag("window")) {
        Box(Modifier.size(300.dp).draggable(rememberDraggableState { dragged += it }, Orientation.Horizontal))
      }
    }
    onNodeWithTag("window").performTouchInput { down(center) }
    onNodeWithTag("window").performTouchInput { moveBy(Offset(-100f, 0f)) }
    assertTrue(tips.resting)
    onNodeWithTag("window").performTouchInput { up() }

    assertFalse(tips.resting)
    assertTrue(dragged < 0f, "the swipe reached what's under it")
  }

  @Test
  fun noTipOverAPageThatCoversTheWindowAndOneShownGoes() = runDesktopComposeUiTest {
    val tips = Tips(InMemoryTextFile(), touch = false).apply { offer(Tip.KeepLoop) }
    tips.covered = true
    window(tips) { Box(Modifier.size(100.dp)) { TipAnchor(Tip.KeepLoop, tips, visible = true, Modifier.size(100.dp)) } }
    mainClock.advanceTimeBy(2_000)
    waitForIdle()
    onAllNodesWithText(Tip.KeepLoop.text).assertCountEquals(0)

    tips.covered = false
    awaitTip(Tip.KeepLoop.text)
    tips.covered = true
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(Tip.KeepLoop.text).fetchSemanticsNodes().isEmpty() }
  }

  @Test
  fun aTipOnTheFirstRowGoesBelowItsCellNotOverTheSearch() {
    val place = place(anchor = Rect(437f, 516f, 844f, 1127f), topLimit = 288f)
    assertFalse(place.above)
    assertEquals(1127 + CARET_H, place.bodyY)
    assertEquals(1127, place.caretY)
  }

  @Test
  fun aTipOnThePillGoesAboveIt() {
    val place = place(anchor = Rect(507f, 2616f, 772f, 2760f), topLimit = 288f)
    assertTrue(place.above)
    assertEquals(2616 - CARET_H - BODY_H, place.bodyY)
    assertTrue(place.bodyY >= 288)
  }

  @Test
  fun aTipStaysInsideTheWindowsSidesWithItsCaretOnTheAnchor() {
    val place = place(anchor = Rect(18f, 1500f, 425f, 1800f), topLimit = 288f)
    assertEquals(24, place.bodyX)
    assertEquals(198, place.caretX)
  }

  @Test
  fun aCellTallerThanTheRoomLeftGetsTheTipOverItsLowerPartBelowTheSearch() {
    val place = place(anchor = Rect(18f, 300f, 425f, 2700f), topLimit = 288f)
    assertFalse(place.above)
    assertEquals(2856 - 24 - BODY_H, place.bodyY)
  }

  private fun place(anchor: Rect, topLimit: Float) = tipPlacement(
    anchor = anchor,
    bodyWidth = 960,
    bodyHeight = BODY_H,
    caretWidth = CARET_W,
    caretHeight = CARET_H,
    windowWidth = 1280,
    windowHeight = 2856,
    topLimit = topLimit,
    margin = 24f,
    corner = 36f,
  )

  private companion object {
    const val BODY_H = 300
    const val CARET_W = 48
    const val CARET_H = 24
  }
}
