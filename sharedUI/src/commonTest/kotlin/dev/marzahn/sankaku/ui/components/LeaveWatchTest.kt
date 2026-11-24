package dev.marzahn.sankaku.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class LeaveWatchTest {
  private data class Frame(
    val left: Float,
    val settled: Boolean = true,
    val pressed: Boolean = false,
    val scaled: Boolean = false,
  )

  private fun leaves(vararg frames: Frame): List<Int> {
    val watch = LeaveWatch()
    return frames.withIndex().filter { (_, frame) ->
      watch.pressed = frame.pressed
      watch.startsLeaving(frame.left, frame.scaled, frame.settled)
    }.map { it.index }
  }

  @Test
  fun theScrimsSlideCountsFromItsFirstFrame() {
    val opening = arrayOf(Frame(-840f, settled = false), Frame(-300f, settled = false), Frame(0f))
    val leaves = leaves(*opening, Frame(-40f), Frame(-400f), Frame(-840f))
    assertEquals(listOf(3), leaves)
  }

  @Test
  fun anOpeningRailIsNotLeaving() {
    assertEquals(emptyList(), leaves(Frame(-840f), Frame(-841f), Frame(-500f)))
  }

  @Test
  fun theOpeningSpringsOvershootComingBackIsNotLeaving() {
    assertEquals(emptyList(), leaves(Frame(-200f, settled = false), Frame(0f), Frame(6f), Frame(2f), Frame(0f)))
  }

  @Test
  fun aDragWaitsForTheFingerToLift() {
    val leaves = leaves(Frame(0f), Frame(-200f, pressed = true), Frame(-100f, pressed = true), Frame(-300f))
    assertEquals(listOf(3), leaves)
  }

  @Test
  fun aDragLetGoThatSpringsBackOpenIsNotLeaving() {
    assertEquals(emptyList(), leaves(Frame(0f), Frame(-200f, pressed = true), Frame(-120f), Frame(0f)))
  }

  @Test
  fun aPredictiveBackGestureIsNotLeaving() {
    assertEquals(emptyList(), leaves(Frame(0f), Frame(-20f, scaled = true), Frame(-48f, scaled = true), Frame(0f)))
  }
}
