package dev.marzahn.sankaku.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import dev.marzahn.sankaku.ui.theme.SankakuTheme
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HideOnScrollTest {
  private lateinit var hide: HideOnScroll
  private var changeCount by mutableIntStateOf(0)

  private var coverCount by mutableIntStateOf(0)

  private var undoPending by mutableStateOf(false)

  private fun ComposeUiTest.showList() {
    setContent {
      SankakuTheme {
        hide = rememberHideOnScroll()
        LaunchedEffect(changeCount) { if (changeCount > 0) hide.show() }
        LaunchedEffect(undoPending) { if (undoPending) hide.show() }
        LaunchedEffect(coverCount) { if (coverCount > 0) hide.showAtOnce() }
        Box(Modifier.size(400.dp, 800.dp)) {
          LazyColumn(Modifier.testTag(LIST).nestedScroll(hide.connection)) {
            items(200) { Text("Row $it", Modifier.fillMaxWidth().height(48.dp)) }
          }
          Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).hidesOnScroll(hide)
              .size(120.dp, 56.dp).background(Color.Gray),
          )
        }
      }
    }
  }

  private fun ComposeUiTest.scrollThePillOut(): Float {
    onNodeWithTag(LIST).performTouchInput { swipeUp() }
    waitForIdle()
    val out = hide.hiddenBy
    assertTrue(out > 56f, "the pill hides on scrolling down: $out px")
    mainClock.autoAdvance = false
    return out
  }

  private fun ComposeUiTest.hiddenPerFrame(name: String, frames: Int = 20): List<Float> = List(frames) {
    mainClock.advanceTimeByFrame()
    hide.hiddenBy
  }.also { println("$name, pill hidden by per frame (px): ${it.map { px -> px.roundToInt() }}") }

  @Test
  fun aChangeSlidesTheHiddenPillBackOverSeveralFrames() = runComposeUiTest {
    showList()
    val out = scrollThePillOut()

    changeCount++
    val frames = hiddenPerFrame("a change (a keep, a screen, a count)")

    val steps = (listOf(out) + frames).zipWithNext { a, b -> a - b }
    assertTrue(steps.all { it >= 0f }, "only up, never past its place: $frames")
    assertTrue(steps.max() < out / 2, "no step of half the way or more: $frames")
    assertTrue(frames.count { it > 0.5f && it < out - 0.5f } >= 4, "$frames")
    assertEquals(0f, frames.last(), "$frames")
  }

  @Test
  fun aScrollDuringTheSlideTakesOver() = runComposeUiTest {
    showList()
    scrollThePillOut()
    changeCount++
    repeat(3) { mainClock.advanceTimeByFrame() }
    val midway = hide.hiddenBy
    assertTrue(midway > 0f, "still on its way: $midway")

    mainClock.autoAdvance = true
    onNodeWithTag(LIST).performTouchInput { swipeUp() }
    waitForIdle()
    assertTrue(hide.hiddenBy > midway, "the scroll hides it again: ${hide.hiddenBy} px, was $midway")
  }

  @Test
  fun scrollingUpStillBringsItBack() = runComposeUiTest {
    showList()
    scrollThePillOut()
    mainClock.autoAdvance = true
    onNodeWithTag(LIST).performTouchInput { swipeDown() }
    waitForIdle()
    assertEquals(0f, hide.hiddenBy)
  }

  @Test
  fun coveredShowsItInTheSameFrame() = runComposeUiTest {
    showList()
    scrollThePillOut()

    coverCount++
    val frames = hiddenPerFrame("covered", frames = 2)
    assertEquals(0f, frames.first(), "$frames")
  }

  @Test
  fun aLaterShowAtOnceTakesOverFromASlide() = runComposeUiTest {
    showList()
    scrollThePillOut()
    changeCount++
    repeat(2) { mainClock.advanceTimeByFrame() }
    coverCount++
    val frames = hiddenPerFrame("a slide, then covered", frames = 5)
    assertTrue(frames.all { it == 0f }, "$frames")
  }

  @Test
  fun aSlideFinishesWhenTheEffectThatStartedItRestarts() = runComposeUiTest {
    showList()
    val out = scrollThePillOut()

    undoPending = true
    repeat(2) { mainClock.advanceTimeByFrame() }
    undoPending = false
    val frames = hiddenPerFrame("undo pending, then not")
    assertTrue(frames.count { it > 0.5f && it < out - 0.5f } >= 3, "$frames")
    assertEquals(0f, frames.last(), "$frames")
  }

  private companion object {
    const val LIST = "list"
  }
}
