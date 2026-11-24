package dev.marzahn.sankaku.features.grid

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ViewerOnScreenTest {
  @Test
  fun onlyAnotherPostMidCloseGetsANewState() {
    val atRest = ViewerOnScreen(startOpen = false)
    val rest = atRest.state
    atRest.open("b", closingId = "a")
    assertSame(rest, atRest.state)

    val closing = ViewerOnScreen(startOpen = true)
    val open = closing.state
    open.targetState = false
    closing.open("a", closingId = "a")
    assertSame(open, closing.state)
    closing.open("b", closingId = "a")
    assertNotSame(open, closing.state)
    assertEquals(false, closing.state.currentState)
  }

  private class Shown(val remembered: Any, val progress: Float)

  private fun reopenMidClose(next: String): Pair<Shown, Shown> {
    var result: Pair<Shown, Shown>? = null
    runComposeUiTest {
      mainClock.autoAdvance = false
      val onScreen = ViewerOnScreen(startOpen = false)
      var openId by mutableStateOf<String?>(null)
      var sharedId: String? = null
      var shown: Shown? = null
      fun open(id: String) {
        onScreen.open(id, closingId = sharedId)
        sharedId = id
        openId = id
      }
      setContent {
        ViewerVisibility(onScreen, visible = openId != null) {
          val remembered = remember { Any() }
          val progress by transition.animateFloat({ tween(SPRING_MS, easing = LinearEasing) }) { if (it == EnterExitState.Visible) 1f else 0f }
          shown = Shown(remembered, progress)
        }
      }
      runOnIdle { open("a") }
      mainClock.advanceTimeBy(SPRING_MS * 2L)
      runOnIdle { openId = null }
      mainClock.advanceTimeBy(REOPEN_AFTER_MS)
      val before = shown!!
      assertTrue(before.progress in 0.1f..0.9f, "not mid-close at the tap: ${before.progress}")
      runOnIdle { open(next) }
      mainClock.advanceTimeByFrame()
      result = before to shown!!
    }
    return result!!
  }

  @Test
  fun anotherPostMidCloseOpensAfresh() {
    val (before, after) = reopenMidClose("b")
    assertNotSame(before.remembered, after.remembered)
    assertTrue(after.progress < before.progress, "the opening went on from ${before.progress} to ${after.progress}")
  }

  @Test
  fun theSamePostMidCloseTurnsRound() {
    val (before, after) = reopenMidClose("a")
    assertSame(before.remembered, after.remembered)
    assertTrue(after.progress > before.progress - 0.05f, "the opening restarted from ${before.progress} to ${after.progress}")
  }

  private companion object {
    const val SPRING_MS = 1_000
    const val REOPEN_AFTER_MS = 200L
  }
}
