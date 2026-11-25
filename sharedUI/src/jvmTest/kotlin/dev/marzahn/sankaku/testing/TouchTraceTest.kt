package dev.marzahn.sankaku.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TouchTraceTest {
  private fun Modifier.consumingIn(pass: PointerEventPass, below: Float = Float.MAX_VALUE) = pointerInput(pass, below) {
    awaitPointerEventScope {
      while (true) awaitPointerEvent(pass).changes.filter { it.position.y < below }.forEach { it.consume() }
    }
  }

  private fun Modifier.watching() = pointerInput(Unit) {
    awaitPointerEventScope {
      while (true) awaitPointerEvent()
    }
  }

  private fun ComposeUiTest.dragUp(from: Offset, frames: Int) {
    var finger = from
    onRoot().performTouchInput { down(finger) }
    repeat(frames) {
      mainClock.advanceTimeByFrame()
      finger -= Offset(0f, STEP)
      onRoot().performTouchInput { moveTo(finger, delayMillis = 0) }
    }
    onRoot().performTouchInput { up() }
  }

  @Test
  fun theTopmostSiblingTakesTheDownAndTheOneBelowIsNotOnThePath() = runDesktopComposeUiTest(SIZE, SIZE) {
    val trace = setTracedContent("under" to hasTestTag("under"), "over" to hasTestTag("over")) {
      Box {
        Box(Modifier.fillMaxSize().testTag("under").watching())
        Box(Modifier.fillMaxSize().testTag("over").watching())
      }
    }
    onRoot().performTouchInput { down(center) }
    trace.assertRecords("only the topmost sibling got the down") { it.single().onPath == setOf("over") }
  }

  @Test
  fun aNamedWrapperWithoutPointerInputIsNotOnThePathOfItsChildsTouches() = runDesktopComposeUiTest(SIZE, SIZE) {
    val trace = setTracedContent("wrapper" to hasTestTag("wrapper"), "child" to hasTestTag("child")) {
      Box(Modifier.fillMaxSize().testTag("wrapper")) {
        Box(Modifier.fillMaxSize().testTag("child").watching())
      }
    }
    onRoot().performTouchInput { down(center) }
    trace.assertRecords("only the child's own node was on the path") { it.single().onPath == setOf("child") }
  }

  @Test
  fun aMoveIsRecordedAsConsumedFromTheFrameItCrossesIntoTheConsumingArea() = runDesktopComposeUiTest(SIZE, SIZE) {
    val trace = setTracedContent("area" to hasTestTag("area")) {
      Box(Modifier.fillMaxSize().testTag("area").consumingIn(PointerEventPass.Main, below = START_Y - 2.5f * STEP))
    }
    dragUp(Offset(SIZE / 2f, START_Y), frames = 5)
    trace.assertRecords("the first consumed move is on frame 3") { records ->
      records.first { it.consumption != Consumption.Unconsumed }.let { it.kind == TouchKind.Move && it.frame == 3 }
    }
    trace.assertRecords("every touch went to the area") { records -> records.all { it.onPath == setOf("area") } }
  }

  @Test
  fun aNodeThatOnlyWatchesIsOnThePathAndTheTouchIsLeftUnconsumed() = runDesktopComposeUiTest(SIZE, SIZE) {
    val trace = setTracedContent("watcher" to hasTestTag("watcher")) {
      Box(Modifier.fillMaxSize().testTag("watcher").watching())
    }
    dragUp(Offset(SIZE / 2f, START_Y), frames = 2)
    trace.assertRecords("a down, two moves and an up") { records ->
      records.map { it.kind } == listOf(TouchKind.Down, TouchKind.Move, TouchKind.Move, TouchKind.Up)
    }
    trace.assertRecords("the watcher saw every touch and none was consumed") { records ->
      records.all { it.onPath == setOf("watcher") && it.consumption == Consumption.Unconsumed }
    }
  }

  @Test
  fun aConsumptionAfterTheMainPassIsToldApartFromOneByItsEnd() = runDesktopComposeUiTest(SIZE, SIZE) {
    val trace = setTracedContent {
      Box(Modifier.fillMaxSize().consumingIn(PointerEventPass.Final))
    }
    onRoot().performTouchInput { down(center) }
    trace.assertRecords("the down was consumed after the Main pass") { it.single().consumption == Consumption.AfterMainPass }
  }

  @Test
  fun aTouchBesideSmallContentIsStillRecorded() = runDesktopComposeUiTest(SIZE, SIZE) {
    val trace = setTracedContent("small" to hasTestTag("small")) {
      Box(Modifier.size(50.dp).testTag("small").watching())
    }
    onRoot().performTouchInput { down(Offset(SIZE - 10f, SIZE - 10f)) }
    trace.assertRecords("the down beside the content was recorded, with nothing named on its path") { it.single().onPath.isEmpty() }
  }

  @Test
  fun aSecondFingerIsRefused() = runDesktopComposeUiTest(SIZE, SIZE) {
    setTracedContent { Box(Modifier.fillMaxSize().watching()) }
    onRoot().performTouchInput { down(0, center) }
    val refusal = assertFailsWith<IllegalStateException> { onRoot().performTouchInput { down(1, center + Offset(20f, 0f)) } }
    assertEquals("the touch trace follows one finger, and this event has 2", refusal.message)
  }

  @Test
  fun aRunningClockIsRefused() = runDesktopComposeUiTest(SIZE, SIZE) {
    setTracedContent { Box(Modifier.fillMaxSize().watching()) }
    mainClock.autoAdvance = true
    val refusal = assertFailsWith<IllegalStateException> { onRoot().performTouchInput { down(center) } }
    assertEquals("the touch trace counts frames on a stopped clock: leave mainClock.autoAdvance false", refusal.message)
  }

  @Test
  fun aClaimThatFailsPrintsTheRecord() = runDesktopComposeUiTest(SIZE, SIZE) {
    val trace = setTracedContent("watcher" to hasTestTag("watcher")) {
      Box(Modifier.fillMaxSize().testTag("watcher").watching())
    }
    onRoot().performTouchInput { down(Offset(10f, 20f)) }
    val failure = assertFailsWith<AssertionError> { trace.assertRecords("nobody got the down") { it.isEmpty() } }
    assertEquals(
      "nobody got the down\ntouch trace, frames counted from the first touch:\n" +
        "frame 0: Down at (10, 20), on the path [watcher], left unconsumed",
      failure.message,
    )
  }

  @Test
  fun composesHitPathHoldsTheTouchedNodesOwnPointerNode() = runDesktopComposeUiTest(SIZE, SIZE) {
    setContent { Box(Modifier.fillMaxSize().testTag("watcher").watching()) }
    onRoot().performTouchInput { down(center) }
    val watcher = onNodeWithTag("watcher").fetchSemanticsNode().layoutInfo.ownCoordinates().toSet()
    val hit = ComposeHitPath(rootForTest()).nodes().filter { it.isAttached }.map { it.requireLayoutCoordinates() }
    assertTrue(hit.any(watcher::contains), "Compose's hit path for a held touch doesn't hold the watcher's pointer node")
  }

  @Test
  fun aTestFrameLastsAsLongAsTheTraceCountsIt() = runDesktopComposeUiTest(SIZE, SIZE) {
    mainClock.autoAdvance = false
    val before = mainClock.currentTime
    mainClock.advanceTimeByFrame()
    assertEquals(COMPOSE_TEST_FRAME_MILLIS, mainClock.currentTime - before)
  }

  private companion object {
    const val SIZE = 400
    const val STEP = 10f
    const val START_Y = 300f
  }
}
